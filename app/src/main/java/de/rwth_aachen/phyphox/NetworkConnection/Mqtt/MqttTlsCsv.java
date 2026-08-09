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
                      String certificateFileName,
                      String resourceFolder,
                      Context context) {

        this.receiveTopic = receiveTopic;
        this.context = context;
        this.username = userName;
        this.password = password;
        this.clientID = userName;
        this.tls = true;
        this.certificateFileName = certificateFileName; //optional: null uses the system trust store
        this.resourceFolder = resourceFolder;
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendCsv(this, send, requestCallbacks);
    }
}
