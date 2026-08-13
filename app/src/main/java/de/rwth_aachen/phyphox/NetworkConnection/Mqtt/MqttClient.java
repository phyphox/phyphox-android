package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A minimal, dependency-free MQTT 3.1.1 client covering exactly what phyphox needs: connect (plain
 * or TLS, with optional username/password), publish (QoS 0 and 1), subscribe to a single topic,
 * keep the connection alive, and reconnect if it drops. It replaces the Eclipse Paho client and the
 * hannesa2 "paho.mqtt.android" fork, which wrapped the (archived) Paho Android Service.
 *
 * QoS 2 is intentionally not implemented: phyphox does not need exactly-once delivery. The former
 * "persistence" mode used QoS 2, but at-least-once (QoS 1) together with phyphox's own message
 * buffer covers the reliability case. See network-mqtts-unofficial in phyphox-docs.
 *
 * Threading: every socket write goes through a single-threaded executor, so writes never interleave.
 * A dedicated reader thread does the blocking reads. When an incoming packet needs a response (a
 * QoS 1 PUBLISH must be answered with a PUBACK) that write is posted back onto the writer executor.
 */
public class MqttClient {

    public interface Listener {
        void onMessage(String topic, byte[] payload);
        void onConnected();
        void onConnectionLost(String reason);
    }

    // MQTT control packet types (high nibble of the fixed-header byte)
    private static final int CONNECT = 1, CONNACK = 2, PUBLISH = 3, PUBACK = 4,
            SUBSCRIBE = 8, SUBACK = 9, PINGREQ = 12, PINGRESP = 13, DISCONNECT = 14;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final long RECONNECT_MIN_MS = 2000, RECONNECT_MAX_MS = 30000;

    private final String host;
    private final int port;
    private final String clientId;
    private final String username; // null if none
    private final String password; // null if none
    private final boolean cleanSession;
    private final int keepAliveSeconds;
    private final SSLSocketFactory sslSocketFactory; // null for a plain TCP connection
    private final boolean verifyHostname; // check the certificate covers the host; on for the system trust store, off for a pinned custom CA
    private final String subscribeTopic; // "" if the client only publishes
    private final Listener listener;

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "phyphox mqtt writer"));
    private final AtomicInteger packetIdCounter = new AtomicInteger(0);

    private volatile Socket socket = null;
    private volatile OutputStream out = null;
    private volatile Thread readerThread = null;
    private volatile Thread keepAliveThread = null;
    private volatile boolean connected = false;
    private volatile boolean subscribed = false;
    private volatile boolean closing = false;
    private volatile long lastWrite = 0;
    private long reconnectDelay = RECONNECT_MIN_MS;

    public MqttClient(String host, int port, String clientId, String username, String password,
                      boolean cleanSession, int keepAliveSeconds, SSLSocketFactory sslSocketFactory,
                      boolean verifyHostname, String subscribeTopic, Listener listener) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.cleanSession = cleanSession;
        this.keepAliveSeconds = keepAliveSeconds > 0 ? keepAliveSeconds : 60;
        this.sslSocketFactory = sslSocketFactory;
        this.verifyHostname = verifyHostname;
        this.subscribeTopic = subscribeTopic == null ? "" : subscribeTopic;
        this.listener = listener;
    }

    /** Connects asynchronously. Returns immediately; success/failure is reported via the Listener. */
    public void connect() {
        closing = false;
        writer.execute(this::attemptConnect);
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    /** Publishes asynchronously. The payload is sent on the writer thread; QoS may be 0 or 1. */
    public void publish(String topic, byte[] payload, int qos) {
        writer.execute(() -> {
            if (!connected)
                return;
            try {
                sendPublish(topic, payload, qos);
            } catch (IOException e) {
                connectionLost("publish failed: " + e.getMessage());
            }
        });
    }

    /** Closes the connection and does not reconnect. */
    public void disconnect() {
        closing = true;
        writer.execute(() -> {
            try {
                if (out != null) {
                    out.write(new byte[]{(byte) (DISCONNECT << 4), 0x00});
                    out.flush();
                }
            } catch (IOException ignored) {
            }
            closeSocket();
        });
        writer.shutdown();
    }

    // ---------------------------------------------------------------- connection

    private void attemptConnect() {
        if (closing)
            return;
        try {
            Socket s;
            if (sslSocketFactory != null) {
                s = sslSocketFactory.createSocket();
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                ((SSLSocket) s).startHandshake();
                //startHandshake validates the certificate chain but not the host name, so without
                //this check a publicly trusted certificate for a different host would be accepted.
                //Off for a pinned custom CA (certificate attribute), where the pin itself is the
                //trust anchor - see network-mqtts-unofficial in phyphox-docs.
                if (verifyHostname) {
                    if (!HttpsURLConnection.getDefaultHostnameVerifier()
                            .verify(host, ((SSLSocket) s).getSession()))
                        throw new SSLPeerUnverifiedException("Host name " + host + " not verified");
                }
            } else {
                s = new Socket();
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            }
            socket = s;
            out = s.getOutputStream();
            InputStream in = s.getInputStream();

            sendConnect();

            // Read CONNACK: type 2, remaining length 2, [session present, return code]
            int header = in.read();
            if (header < 0)
                throw new IOException("no CONNACK");
            int remaining = readRemainingLength(in);
            byte[] body = readFully(in, remaining);
            if ((header >> 4) != CONNACK || body.length < 2)
                throw new IOException("malformed CONNACK");
            int returnCode = body[1] & 0xff;
            if (returnCode != 0)
                throw new IOException(connackMessage(returnCode));

            connected = true;
            subscribed = false;
            reconnectDelay = RECONNECT_MIN_MS;

            startReader(in);
            startKeepAlive();

            if (!subscribeTopic.isEmpty())
                sendSubscribe(subscribeTopic, 0);
            else
                subscribed = true; // nothing to subscribe to, so treat as ready

            listener.onConnected();
        } catch (Exception e) {
            closeSocket();
            connected = false;
            if (!closing)
                scheduleReconnect(e.getMessage());
        }
    }

    private void scheduleReconnect(String reason) {
        listener.onConnectionLost(reason);
        final long delay = reconnectDelay;
        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                return;
            }
            if (!closing)
                writer.execute(this::attemptConnect);
        }, "phyphox mqtt reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void connectionLost(String reason) {
        if (!connected && !subscribed)
            return;
        connected = false;
        subscribed = false;
        closeSocket();
        if (!closing)
            scheduleReconnect(reason);
    }

    private void closeSocket() {
        connected = false;
        subscribed = false;
        Socket s = socket;
        socket = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- reader

    private void startReader(final InputStream in) {
        readerThread = new Thread(() -> {
            try {
                while (!closing) {
                    int header = in.read();
                    if (header < 0)
                        break; // connection closed by the broker
                    int type = (header >> 4) & 0x0f;
                    int flags = header & 0x0f;
                    int remaining = readRemainingLength(in);
                    byte[] body = readFully(in, remaining);
                    handlePacket(type, flags, body);
                }
            } catch (IOException e) {
                if (!closing)
                    connectionLost("connection lost: " + e.getMessage());
                return;
            }
            if (!closing)
                connectionLost("connection closed by broker");
        }, "phyphox mqtt reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handlePacket(int type, int flags, byte[] body) {
        switch (type) {
            case PUBLISH: {
                int qos = (flags >> 1) & 0x03;
                int i = 0;
                int topicLen = ((body[i] & 0xff) << 8) | (body[i + 1] & 0xff);
                i += 2;
                String topic = new String(body, i, topicLen, StandardCharsets.UTF_8);
                i += topicLen;
                int packetId = -1;
                if (qos > 0) {
                    packetId = ((body[i] & 0xff) << 8) | (body[i + 1] & 0xff);
                    i += 2;
                }
                byte[] payload = new byte[body.length - i];
                System.arraycopy(body, i, payload, 0, payload.length);
                listener.onMessage(topic, payload);
                if (qos == 1) {
                    final int id = packetId;
                    writer.execute(() -> {
                        try {
                            sendPubAck(id);
                        } catch (IOException ignored) {
                        }
                    });
                }
                break;
            }
            case SUBACK:
                subscribed = true;
                break;
            case PINGRESP:
            case PUBACK:
                break; // nothing to do; PUBACK just acknowledges one of our QoS 1 publishes
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- keep alive

    private void startKeepAlive() {
        keepAliveThread = new Thread(() -> {
            long intervalMs = keepAliveSeconds * 1000L;
            while (!closing && connected) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                if (!connected || closing)
                    return;
                if (System.currentTimeMillis() - lastWrite >= intervalMs) {
                    writer.execute(() -> {
                        try {
                            if (out != null) {
                                writePacket(new byte[]{(byte) (PINGREQ << 4), 0x00});
                            }
                        } catch (IOException e) {
                            connectionLost("ping failed: " + e.getMessage());
                        }
                    });
                }
            }
        }, "phyphox mqtt keepalive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    // ---------------------------------------------------------------- packet builders (writer thread only)

    private void sendConnect() throws IOException {
        ByteArrayOutputStream vh = new ByteArrayOutputStream();
        writeString(vh, "MQTT");        // protocol name
        vh.write(0x04);                 // protocol level 4 = MQTT 3.1.1
        int flags = 0;
        if (username != null) flags |= 0x80;
        if (password != null) flags |= 0x40;
        if (cleanSession) flags |= 0x02;
        vh.write(flags);
        vh.write((keepAliveSeconds >> 8) & 0xff);
        vh.write(keepAliveSeconds & 0xff);
        writeString(vh, clientId);
        if (username != null) writeString(vh, username);
        if (password != null) writeString(vh, password);
        writePacket(CONNECT, 0, vh.toByteArray());
    }

    private void sendPublish(String topic, byte[] payload, int qos) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeString(b, topic);
        if (qos > 0) {
            int id = nextPacketId();
            b.write((id >> 8) & 0xff);
            b.write(id & 0xff);
        }
        b.write(payload, 0, payload.length);
        writePacket(PUBLISH, (qos & 0x03) << 1, b.toByteArray());
    }

    private void sendSubscribe(String topic, int qos) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int id = nextPacketId();
        b.write((id >> 8) & 0xff);
        b.write(id & 0xff);
        writeString(b, topic);
        b.write(qos & 0x03);
        writePacket(SUBSCRIBE, 0x02, b.toByteArray()); // SUBSCRIBE requires the reserved flags 0010
    }

    private void sendPubAck(int packetId) throws IOException {
        writePacket(PUBACK, 0, new byte[]{(byte) ((packetId >> 8) & 0xff), (byte) (packetId & 0xff)});
    }

    private void writePacket(int type, int flags, byte[] body) throws IOException {
        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        pkt.write((type << 4) | (flags & 0x0f));
        writeRemainingLength(pkt, body.length);
        pkt.write(body, 0, body.length);
        writePacket(pkt.toByteArray());
    }

    private void writePacket(byte[] raw) throws IOException {
        OutputStream o = out;
        if (o == null)
            throw new IOException("not connected");
        o.write(raw);
        o.flush();
        lastWrite = System.currentTimeMillis();
    }

    private int nextPacketId() {
        int id = packetIdCounter.updateAndGet(v -> (v + 1) & 0xffff);
        return id == 0 ? packetIdCounter.incrementAndGet() : id; // packet identifier must be non-zero
    }

    // ---------------------------------------------------------------- wire helpers

    private static void writeString(ByteArrayOutputStream b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        b.write((bytes.length >> 8) & 0xff);
        b.write(bytes.length & 0xff);
        b.write(bytes, 0, bytes.length);
    }

    // Remaining Length is a Variable Byte Integer (1-4 bytes, 7 bits each, high bit = continuation)
    private static void writeRemainingLength(ByteArrayOutputStream b, int length) {
        do {
            int digit = length & 0x7f;
            length >>= 7;
            if (length > 0)
                digit |= 0x80;
            b.write(digit);
        } while (length > 0);
    }

    private static int readRemainingLength(InputStream in) throws IOException {
        int multiplier = 1, value = 0, digit;
        int count = 0;
        do {
            digit = in.read();
            if (digit < 0)
                throw new IOException("EOF in remaining length");
            value += (digit & 0x7f) * multiplier;
            multiplier *= 128;
            if (++count > 4)
                throw new IOException("malformed remaining length");
        } while ((digit & 0x80) != 0);
        return value;
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(buf, read, length - read);
            if (n < 0)
                throw new IOException("EOF while reading packet");
            read += n;
        }
        return buf;
    }

    private static String connackMessage(int returnCode) {
        switch (returnCode) {
            case 1: return "connection refused: unacceptable protocol version";
            case 2: return "connection refused: identifier rejected";
            case 3: return "connection refused: server unavailable";
            case 4: return "connection refused: bad username or password";
            case 5: return "connection refused: not authorized";
            default: return "connection refused: code " + returnCode;
        }
    }
}
