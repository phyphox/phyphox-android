package de.rwth_aachen.phyphox.Bluetooth;

public class ConnectedDeviceInfo {
    public static final int SIGNAL_FULL = -45;
    public static final int SIGNAL_HIGH = -65;
    public static final int SIGNAL_MEDIUM = -80;
    public static final int SIGNAL_LOW = -90;
    public static final int NO_SIGNAL = -98;

    private String deviceId;
    private String deviceName;
    private int batteryLabel;
    private int signalStrength = 0;


    ConnectedDeviceInfo(){}

    public ConnectedDeviceInfo(String deviceId, String deviceName, int batteryLabel, int signalStrength){
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.batteryLabel = batteryLabel;
        this.signalStrength = signalStrength;
    }

    public String getDeviceId(){
        return deviceId;
    }

    public void setDeviceId(String deviceId){
        this.deviceId = deviceId;
    }

    public int getBatteryLabel() {
        return batteryLabel;
    }

    public void setBatteryLabel(int batteryLabel) {
        this.batteryLabel = batteryLabel;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

}
