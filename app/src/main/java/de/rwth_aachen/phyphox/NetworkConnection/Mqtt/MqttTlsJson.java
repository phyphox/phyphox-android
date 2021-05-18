package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;

import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;
import de.rwth_aachen.phyphox.PhyphoxExperiment;

public class MqttTlsJson extends MqttService{
    String sendTopic;
    PhyphoxExperiment experiment;//todo

    public MqttTlsJson(String receiveTopic,
                       String sendTopic,
                       String userName,
                       String password,
                       Context context,
                       boolean persistence,
                       boolean clearBuffer,
                       PhyphoxExperiment experiment) {

        this.receiveTopic = receiveTopic;
        this.sendTopic = sendTopic;
        this.context = context;
        this.persistence = persistence;
        this.clearBuffer = clearBuffer;
        this.experiment = experiment;

        MqttHelper.tlsSetup(this, context, userName, password);

        if(persistence){
            setPersistenceSettings();
        }else {
            mqttConnectOptions.setCleanSession(true);
        }

    }

    @Override
    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendJson(this,sendTopic,send,requestCallbacks, experiment);
    }
}
