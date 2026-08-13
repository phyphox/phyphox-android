package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

/**
 * Base class for the MQTT network services. It drives a from-scratch {@link MqttClient} (MQTT
 * 3.1.1, no external dependency) - see network-mqtts-unofficial in phyphox-docs. The concrete
 * subclasses only choose the payload format (JSON or CSV) and whether TLS and authentication are
 * used.
 *
 * The former QoS-2 "persistence" mode is gone: persistence="true" now publishes with QoS 1
 * (at-least-once) while connected, and there is no more offline message buffering. Plain publishes
 * use QoS 0.
 */
public abstract class MqttService extends NetworkService.Service {
    private final List<byte[]> data = new ArrayList<>();
    String receiveTopic = "";
    String clientID;
    String address; // scheme-normalised, used as the salt for the per-broker metadata id
    Context context;

    // set by the subclasses before connect()
    String username = null;
    String password = null;
    boolean tls = false;
    String certificateFileName = null; // resource name of a custom CA (see the certificate attribute); null = system trust store
    String resourceFolder = null; // the experiment's resource folder, where the certificate lives
    int qos = 0;

    SSLSocketFactory sslSocketFactory = null;
    MqttClient client = null;

    public void connect(String address) {
        if (tls) {
            try {
                sslSocketFactory = MqttHelper.buildSslSocketFactory(context, resourceFolder, certificateFileName);
            } catch (Exception e) {
                Log.e("MQTT", "TLS: " + e.getMessage());
                toast("MQTT: " + e.getMessage());
                return; // do not connect with a different trust model than the experiment intended
            }
        }
        // The address is kept scheme-normalised for the metadata id (kept identical to the previous
        // implementation), while host and port for the socket are parsed out of it separately.
        if (address.contains("://"))
            this.address = address;
        else
            this.address = "tcp://" + address;

        String hostPort = address;
        int schemeIdx = hostPort.indexOf("://");
        if (schemeIdx >= 0)
            hostPort = hostPort.substring(schemeIdx + 3);
        String host;
        int port;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException e) {
                port = tls ? 8883 : 1883;
            }
        } else {
            host = hostPort;
            port = tls ? 8883 : 1883;
        }

        client = new MqttClient(host, port, clientID, username, password, true, 60, sslSocketFactory,
                tls && certificateFileName == null, receiveTopic, new MqttClient.Listener() {
            @Override
            public void onMessage(String topic, byte[] payload) {
                synchronized (data) {
                    data.add(payload);
                }
            }

            @Override
            public void onConnected() {
                toast("MQTT: Connected");
            }

            @Override
            public void onConnectionLost(String reason) {
                Log.e("MQTT", "Connection lost: " + reason);
                toast("MQTT: " + reason);
            }
        });
        client.connect();
    }

    public void disconnect() {
        if (client != null)
            client.disconnect();
        client = null;
    }

    public byte[][] getResults() {
        synchronized (data) {
            byte[][] ret = data.toArray(new byte[data.size()][]);
            data.clear();
            return ret;
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public boolean isSubscribed() {
        return client != null && client.isSubscribed();
    }

    private void toast(final String message) {
        if (context == null)
            return;
        new Handler(context.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}
