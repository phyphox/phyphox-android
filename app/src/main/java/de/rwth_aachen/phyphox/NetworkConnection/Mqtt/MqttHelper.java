package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;


public class MqttHelper {

    // Builds the SSL socket factory for a TLS ("mqtts") connection. The certificate is an
    // experiment resource named by the connection's certificate attribute: it lives in the res
    // directory of the experiment container and is copied along with the experiment like an image
    // resource, so it survives saving to the collection. If no certificate is named, the system
    // trust store is used. A named certificate that cannot be loaded is an error - silently
    // falling back to the system trust store would connect with a different trust model than the
    // experiment author intended.
    public static SSLSocketFactory buildSslSocketFactory(Context context,
                                                         String resourceFolder,
                                                         String certificateFileName) throws Exception {
        if (certificateFileName == null || certificateFileName.isEmpty())
            return (SSLSocketFactory) SSLSocketFactory.getDefault(); // system trust store

        // Resolve the certificate like an image resource: from the experiment's resource folder if
        // it has one, falling back to the res assets bundled with phyphox.
        InputStream input = null;
        if (resourceFolder != null && !resourceFolder.startsWith("ASSET")) {
            File certFile = new File(resourceFolder, certificateFileName);
            if (certFile.isFile())
                input = new FileInputStream(certFile);
        }
        if (input == null) {
            try {
                input = context.getAssets().open("experiments/res/" + certificateFileName);
            } catch (Exception e) {
                throw new Exception("Certificate \"" + certificateFileName + "\" not found.");
            }
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca = cf.generateCertificate(input); // accepts PEM or DER

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", ca);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, tmf.getTrustManagers(), null);
            return sslCtx.getSocketFactory();
        } finally {
            input.close();
        }
    }

    private static void writeBufferValuesIntoJson(Map.Entry<String, NetworkConnection.NetworkSendableData> item,
                                                  JSONObject json) throws JSONException {
        String datatype = item.getValue().additionalAttributes != null ? item.getValue().additionalAttributes.get("datatype") : null;
        if (datatype != null && datatype.equals("number")) {
            double v = item.getValue().buffer.value;
            if (Double.isNaN(v) || Double.isInfinite(v))
                json.put(item.getKey(), null);
            else
                json.put(item.getKey(), v);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (double v : item.getValue().buffer.getArray()) {
                if (Double.isNaN(v) || Double.isInfinite(v))
                    jsonArray.put(null);
                else
                    jsonArray.put(v);
            }
            json.put(item.getKey(), jsonArray);
        }
    }

    private static JSONObject buildJson(Map<String, NetworkConnection.NetworkSendableData> send,
                                        MqttService mqttService) throws JSONException {

        JSONObject json = new JSONObject();

        for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : send.entrySet()) {
            if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                json.put(item.getKey(), item.getValue().metadata.get(mqttService.address));
            else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER) {
                writeBufferValuesIntoJson(item, json);
            } else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME) {
                JSONObject timeInfo = new JSONObject();
                timeInfo.put("now", System.currentTimeMillis() / 1000.0);
                JSONArray events = new JSONArray();
                for (ExperimentTimeReference.TimeMapping timeMapping : item.getValue().timeReference.getTimeMappings()) {
                    JSONObject eventJson = new JSONObject();
                    eventJson.put("event", timeMapping.event.name());
                    eventJson.put("experimentTime", timeMapping.experimentTime);
                    eventJson.put("systemTime", timeMapping.systemTime / 1000.);
                    events.put(eventJson);
                }
                timeInfo.put("events", events);
                json.put(item.getKey(), timeInfo);
            }
        }
        return json;
    }

    public static void sendJson(MqttService mqttService,
                                String sendTopic,
                                Map<String, NetworkConnection.NetworkSendableData> send,
                                List<NetworkService.RequestCallback> requestCallbacks) {

        NetworkService.ServiceResult result;
        try {
            if (!mqttService.isConnected()) {
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.noConnection, null);
            } else if (!mqttService.isSubscribed() && !mqttService.receiveTopic.isEmpty()) {
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Not subscribed.");
            } else {
                JSONObject json = buildJson(send, mqttService);
                mqttService.client.publish(sendTopic, json.toString().getBytes(), mqttService.qos);
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.success, "");
            }
        } catch (JSONException e) {
            Log.e("MQTT", "Could not build JSON: " + e.getMessage());
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Could not build JSON. " + e.getMessage());
        }

        for (NetworkService.RequestCallback callback : requestCallbacks) {
            callback.requestFinished(result);
        }
    }

    private static String writeBufferValuesIntoCsvPayload(Map.Entry<String, NetworkConnection.NetworkSendableData> item,
                                                          String payload) {
        String datatype = item.getValue().additionalAttributes != null ? item.getValue().additionalAttributes.get("datatype") : null;
        if (datatype != null && datatype.equals("number")) {
            if (item.getValue().buffer.getFilledSize() == 0)
                return payload;
            double v = item.getValue().buffer.value;
            if (Double.isNaN(v) || Double.isInfinite(v))
                payload = "null";
            else
                payload = String.valueOf(v);
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (double v : item.getValue().buffer.getArray()) {
                if (first)
                    first = false;
                else
                    sb.append(",");
                if (Double.isNaN(v) || Double.isInfinite(v))
                    sb.append("null");
                else
                    sb.append(v);
            }
            payload = sb.toString();
        }
        return payload;
    }

    public static void sendCsv(MqttService mqttService,
                               Map<String, NetworkConnection.NetworkSendableData> send,
                               List<NetworkService.RequestCallback> requestCallbacks) {

        DecimalFormat longformat = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
        longformat.applyPattern("############0.000");
        longformat.setGroupingUsed(false);

        NetworkService.ServiceResult result;

        if (!mqttService.isConnected()) {
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.noConnection, null);
        } else if (!mqttService.isSubscribed() && !mqttService.receiveTopic.isEmpty()) {
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Not subscribed.");
        } else {
            for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : send.entrySet()) {
                String payload = "";
                if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                    payload = item.getValue().metadata.get(mqttService.address);
                else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER) {
                    payload = writeBufferValuesIntoCsvPayload(item, payload);
                } else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME)
                    payload = String.valueOf(longformat.format(System.currentTimeMillis() / 1000.0));
                else
                    continue;
                mqttService.client.publish(item.getKey(), payload.getBytes(), mqttService.qos);
            }
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.success, "");
        }

        for (NetworkService.RequestCallback callback : requestCallbacks) {
            callback.requestFinished(result);
        }
    }
}
