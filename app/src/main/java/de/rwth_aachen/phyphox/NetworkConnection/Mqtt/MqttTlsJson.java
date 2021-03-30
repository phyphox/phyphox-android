package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Vector;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

import static android.os.Environment.getExternalStorageDirectory;

public class MqttTlsJson extends MqttService{
    String sendTopic;

    public MqttTlsJson(String receiveTopic,
                       String sendTopic,
                       String bksFilePath,
                       String userName,
                       String password,
                       Context context,
                       boolean persistence) {

        this.receiveTopic = receiveTopic;
        this.sendTopic = sendTopic;
        this.context = context;
        this.persistence = persistence;
        MqttHelper.tlsSetup(this,bksFilePath,userName,password);

        if(persistence){
            setPersistenceSettings();
        }else {
            mqttConnectOptions.setCleanSession(true);
        }

    }

    @Override
    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendJson(this,sendTopic,send,requestCallbacks);
    }
}
