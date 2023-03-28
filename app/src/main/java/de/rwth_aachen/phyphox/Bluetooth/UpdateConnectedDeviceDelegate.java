package de.rwth_aachen.phyphox.Bluetooth;

import java.util.ArrayList;

public interface UpdateConnectedDeviceDelegate {

    void updateConnectedDevice(ArrayList<ConnectedDeviceInfo> connectedDeviceInfos);

}
