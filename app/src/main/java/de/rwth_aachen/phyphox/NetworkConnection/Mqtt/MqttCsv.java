package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;

import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

public class MqttCsv extends MqttService {
    public MqttCsv(String receiveTopic, Context context) {
        this.receiveTopic = receiveTopic;
        this.context = context;
        this.clientID = "phyphox_" + String.format("%06x", (System.nanoTime() & 0xffffff));
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendCsv(this,send,requestCallbacks);
    }
}
