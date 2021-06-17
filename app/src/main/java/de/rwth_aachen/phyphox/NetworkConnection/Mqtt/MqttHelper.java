package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.security.KeyStore;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;
import de.rwth_aachen.phyphox.PhyphoxExperiment;


public class MqttHelper{

    private static File handleBksFiles(Context context)throws FileNotFoundException{
        File tempPath = new File(context.getFilesDir(), "temp_zip");
        final File[] bksFiles = tempPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".bks");
            }
        });
        if(bksFiles.length == 1){
            return bksFiles[0];
        }else if (bksFiles.length > 1){
            Log.e("MQTT","BKS File Error: only one bks Fiel can be used. Using: "+ bksFiles[0].getName());
            return bksFiles[0];
        }
        throw new FileNotFoundException("No BKS File");
    }

    public static void tlsSetup(MqttService mqttService,
                                Context context,
                                String userName,
                                String password){
        try {
            File bksFile = handleBksFiles(context);
            KeyStore trustStore = KeyStore.getInstance("BKS");

            InputStream input = new FileInputStream(bksFile);
            trustStore.load(input, null);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, tmf.getTrustManagers(), null);
            mqttService.mqttConnectOptions.setSocketFactory(sslCtx.getSocketFactory());
        }catch (Exception ex){
            Log.e("MQTT", "TLS : " + ex.toString());
        }
        mqttService.mqttConnectOptions.setUserName(userName);
        mqttService.mqttConnectOptions.setPassword(password.toCharArray());
        mqttService.clientID = userName;
    }

    private static void writeBufferValuesIntoJson(Map.Entry<String, NetworkConnection.NetworkSendableData> item,
                                                  JSONObject json)throws JSONException
    {
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
                                        MqttService mqttService) throws JSONException{

        JSONObject json = new JSONObject();

            for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : send.entrySet()) {
                if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                    json.put(item.getKey(), item.getValue().metadata.get(mqttService.address));
                else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER) {
                    writeBufferValuesIntoJson(item,json);
                } else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME) {
                    JSONObject timeInfo = new JSONObject();
                    timeInfo.put("now", System.currentTimeMillis() / 1000.0);
                    JSONArray events = new JSONArray();
                    for (ExperimentTimeReference.TimeMapping timeMapping : item.getValue().timeReference.timeMappings) {
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

    private static void sendPersistenceMassages(MqttService mqttService, String sendTopic) throws MqttException{
        for(JSONObject persistenceMassage : mqttService.messageBuffer){
            MqttMessage message = new MqttMessage();
            message.setPayload(persistenceMassage.toString().getBytes());
            message.setQos(2);
            mqttService.client.publish(sendTopic, message);
        }
        mqttService.writeSequence = 0;
        mqttService.messageBuffer.clear();
    }

    private static void recordPersistenceMassages(Map<String, NetworkConnection.NetworkSendableData> send,
                                                  MqttService mqttService) throws JSONException{
        int capacity = mqttService.messageBuffer.capacity();
        mqttService.messageBuffer.insertElementAt(buildJson(send,mqttService),(mqttService.writeSequence % capacity));
        mqttService.writeSequence++;
        if(mqttService.writeSequence % capacity == 0) {
            mqttService.writeSequence = 0;
        }
    }

    public static void sendJson (MqttService mqttService,
                                 String sendTopic,
                                 Map<String, NetworkConnection.NetworkSendableData> send,
                                 List<NetworkService.RequestCallback> requestCallbacks
                                 ) {

        NetworkService.ServiceResult result;
        try {
            if (!mqttService.isConnected()) {
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.noConnection, null);
                if(mqttService.persistence){
                    recordPersistenceMassages(send,mqttService);
                }
            } else if (!mqttService.subscribed && !mqttService.receiveTopic.isEmpty()) {
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Not subscribed.");
            } else {
            JSONObject json = buildJson(send, mqttService);

            MqttMessage message = new MqttMessage();
            if(mqttService.persistence){
                sendPersistenceMassages(mqttService,sendTopic);
                message.setQos(2);
            }else {
                message.setQos(0);
            }
            message.setPayload(json.toString().getBytes());
            mqttService.client.publish(sendTopic, message);

            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.success, "");

        }

        } catch (MqttException e) {
            Log.e("MQTT","Could not publish: " +  e.getMessage());
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Could not publish. " + e.getMessage());
        } catch (JSONException e) {
            Log.e("MQTT","Could not build JSON: " +  e.getMessage());
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Could not build JSON. " + e.getMessage());
        }

        for (NetworkService.RequestCallback callback : requestCallbacks) {
            callback.requestFinished(result);
        }
    }

    private static String writeBufferValuesIntoCsvPayload(Map.Entry<String, NetworkConnection.NetworkSendableData> item,
                                                          String payload){
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

    public static void sendCsv (MqttService mqttService,
                                Map<String, NetworkConnection.NetworkSendableData> send,
                                List<NetworkService.RequestCallback> requestCallbacks) {

        DecimalFormat longformat = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
        longformat.applyPattern("############0.000");
        longformat.setGroupingUsed(false);

        NetworkService.ServiceResult result;
        try {
        if (!mqttService.isConnected()) {
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.noConnection, null);
        } else if (!mqttService.subscribed && !mqttService.receiveTopic.isEmpty()) {
            result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Not subscribed.");
        } else {
                for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : send.entrySet()) {
                    String payload ="";
                    if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                        payload = item.getValue().metadata.get(mqttService.address);
                    else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER) {
                        payload = writeBufferValuesIntoCsvPayload(item,payload);
                    } else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME)
                        payload = String.valueOf(longformat.format(System.currentTimeMillis()/1000.0));
                    else
                        continue;
                    MqttMessage message = new MqttMessage();
                    message.setPayload(payload.getBytes());
                    mqttService.client.publish(item.getKey(), message);
                }
                result = new NetworkService.ServiceResult(NetworkService.ResultEnum.success, "");
        }

    } catch (MqttException e) {
        Log.e("MQTT","Could not publish: " +  e.getMessage());
        result = new NetworkService.ServiceResult(NetworkService.ResultEnum.genericError, "Could not publish. " + e.getMessage());
    }
        for (NetworkService.RequestCallback callback : requestCallbacks) {
            callback.requestFinished(result);
        }
    }
}
