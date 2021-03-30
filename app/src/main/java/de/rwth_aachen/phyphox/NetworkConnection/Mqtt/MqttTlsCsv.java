package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

import static android.os.Environment.getExternalStorageDirectory;

public class MqttTlsCsv extends MqttService {
    public MqttTlsCsv(String receiveTopic,
                      String bksFilePath,
                      String userName,
                      String password,
                      Context context) {

        this.receiveTopic = receiveTopic;
        this.context = context;
        MqttHelper.tlsSetup(this,bksFilePath,userName,password);
        mqttConnectOptions.setCleanSession(true);
    }

    public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<NetworkService.RequestCallback> requestCallbacks) {
        MqttHelper.sendCsv(this,send,requestCallbacks);
    }
}
