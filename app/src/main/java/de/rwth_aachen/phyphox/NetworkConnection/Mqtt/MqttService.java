package de.rwth_aachen.phyphox.NetworkConnection.Mqtt;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

import static android.os.Environment.getExternalStorageDirectory;

public abstract class MqttService extends NetworkService.Service {
    List<byte[]> data = new ArrayList<>();
    String receiveTopic;
    String clientID;
    String address;
    MqttAndroidClient client = null;
    Context context;
    boolean connected = false;
    boolean subscribed = false;
    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
    MqttDefaultFilePersistence dataStore;
    int writeSequence = 0;
    Vector<JSONObject> messageBuffer = new Vector<JSONObject>(600);
    boolean persistence = false;

    public void connect(String address) {
        this.address = address;

        mqttConnectOptions.setAutomaticReconnect(true);

        client = new MqttAndroidClient(context, address, clientID, dataStore);

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                if (!receiveTopic.isEmpty()) {
                    try {
                        client.subscribe(receiveTopic, 0, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                subscribed = true;
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                subscribed = false;
                                Log.e("MQTT", "Connection failure: " + exception.getMessage());
                            }
                        });

                    } catch (MqttException ex) {
                        Log.e("MQTT", "Could not subscribe: " + ex.getMessage());
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                connected = false;
                subscribed = false;
                Log.e("MQTT", "Connection lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                data.add(message.getPayload());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        try {
            client.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connected = true;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    connected = false;
                    subscribed = false;
                    Log.e("MQTT", "Connection failed: " + exception.getMessage());
                }
            });


        } catch (MqttException ex){
            Log.e("MQTT", "Could not connect: " + ex.getMessage());
        }
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException ex){
            Log.e("MQTT","Could not disconnect: " +  ex.getMessage());
        }
        client = null;
        connected = false;
        subscribed = false;
    }

    public byte[][] getResults() {
        byte[][] ret = data.toArray(new byte[data.size()][]);
        data.clear();
        return ret;
    }

    protected void setPersistenceSettings(){
        dataStore = new MqttDefaultFilePersistence(getExternalStorageDirectory().getAbsolutePath()+"/Download/");
        try {
            dataStore.open(clientID,address);
        }catch (Exception ex){
            Log.e("MQTT", "Data Store: " + ex.toString());
        }
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setMaxInflight(600);
    }

}
