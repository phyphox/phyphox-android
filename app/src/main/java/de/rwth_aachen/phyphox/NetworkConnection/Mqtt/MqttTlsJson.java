package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;

import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

public class MqttTlsJson extends MqttService {
    String sendTopic;

    public MqttTlsJson(String receiveTopic,
                       String sendTopic,
                       String userName,
                       String password,
                       String certificateFileName,
                       String resourceFolder,
                       Context context,
                       boolean persistence) {

        this.receiveTopic = receiveTopic;
        this.sendTopic = sendTopic;
        this.context = context;
        this.username = userName;
        this.password = password;
        this.clientID = userName;
        this.tls = true;
        this.certificateFileName = certificateFileName; //optional: null uses the system trust store
        this.resourceFolder = resourceFolder;
        //persistence now selects at-least-once delivery (QoS 1) instead of the former QoS 2
        this.qos = persistence ? 1 : 0;
    }

    @Override
    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendJson(this, sendTopic, send, requestCallbacks);
    }
}
