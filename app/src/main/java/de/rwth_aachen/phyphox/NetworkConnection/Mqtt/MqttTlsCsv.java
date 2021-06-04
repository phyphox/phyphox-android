package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;
import de.rwth_aachen.phyphox.PhyphoxExperiment;

public class MqttTlsCsv extends MqttService {
    public MqttTlsCsv(String receiveTopic,
                      String userName,
                      String password,
                      Context context) {

        this.receiveTopic = receiveTopic;
        this.context = context;
        MqttHelper.tlsSetup(this, context, userName, password);
        mqttConnectOptions.setCleanSession(true);
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendCsv(this,send,requestCallbacks);
    }
}
