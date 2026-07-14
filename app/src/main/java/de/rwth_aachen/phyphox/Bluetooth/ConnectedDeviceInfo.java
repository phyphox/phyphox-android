package de.rwth_aachen.phyphox.Bluetooth;

import de.rwth_aachen.phyphox.R;

public class ConnectedDeviceInfo {
    public static final int SIGNAL_FULL = -45;
    public static final int SIGNAL_HIGH = -65;
    public static final int SIGNAL_MEDIUM = -80;
    public static final int SIGNAL_LOW = -90;
    public static final int NO_SIGNAL = -98;

    //Single mapping of an RSSI value to the signal strength icon, used by the scan dialog and
    //the connected-device info bar alike
    public static int getSignalStrengthDrawable(int rssi) {
        if (rssi > SIGNAL_FULL)
            return R.drawable.bluetooth_signal_4;
        else if (rssi > SIGNAL_HIGH)
            return R.drawable.bluetooth_signal_3;
        else if (rssi > SIGNAL_MEDIUM)
            return R.drawable.bluetooth_signal_2;
        else if (rssi > SIGNAL_LOW)
            return R.drawable.bluetooth_signal_1;
        else
            return R.drawable.bluetooth_signal_0;
    }

    private String deviceId;
    private String deviceName;
    private int batteryLabel;
    private int signalStrength = -1;


    ConnectedDeviceInfo(){}

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
