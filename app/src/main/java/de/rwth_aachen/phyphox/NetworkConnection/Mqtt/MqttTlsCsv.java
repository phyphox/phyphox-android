package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

public class MqttTlsCsv extends MqttService {
    public MqttTlsCsv(String receiveTopic,
                      String userName,
                      String password,
                      Context context,
                      boolean clearBuffer) {

        this.receiveTopic = receiveTopic;
        this.context = context;
        this.clearBuffer = clearBuffer;
        MqttHelper.tlsSetup(this, context, userName, password);
        mqttConnectOptions.setCleanSession(true);
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendCsv(this,send,requestCallbacks,clearBuffer);
    }
}
