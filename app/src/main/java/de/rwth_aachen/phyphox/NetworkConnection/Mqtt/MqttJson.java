package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;

import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

public class MqttJson extends MqttService {
    String sendTopic;

    public MqttJson(String receiveTopic,
                    String sendTopic,
                    String username,
                    String password,
                    Context context,
                    boolean persistence) {

        this.receiveTopic = receiveTopic;
        this.sendTopic = sendTopic;
        this.username = username; //optional: null connects without authentication
        this.password = password;
        this.context = context;
        this.clientID = "phyphox_" + String.format("%06x", (System.nanoTime() & 0xffffff));
        //persistence now selects at-least-once delivery (QoS 1) instead of the former QoS 2
        this.qos = persistence ? 1 : 0;
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendJson(this, sendTopic, send, requestCallbacks);
    }
}
