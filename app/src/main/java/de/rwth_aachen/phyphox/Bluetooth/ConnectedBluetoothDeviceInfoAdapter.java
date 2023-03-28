package de.rwth_aachen.phyphox.Bluetooth;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Map;

import de.rwth_aachen.phyphox.R;

public class ConnectedBluetoothDeviceInfoAdapter extends RecyclerView.Adapter<ConnectedBluetoothDeviceInfoAdapter.ViewHolder>{
    private Context mParent;

    ArrayList<ConnectedDeviceInfo> connectedDevices;

    public ConnectedBluetoothDeviceInfoAdapter(ArrayList<ConnectedDeviceInfo> connectedDevices){
        this.connectedDevices = connectedDevices;
    }

    public void update(ArrayList<ConnectedDeviceInfo> data) {
        connectedDevices.clear();
        connectedDevices.addAll(data);
        for (ConnectedDeviceInfo value: connectedDevices) {
            Log.d("BluetoothCheck", ""+value.getSignalStrength());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mParent = parent.getContext();
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View listItem= layoutInflater.inflate(R.layout.bluetooth_devices_info_items, parent, false);
        return new ViewHolder(listItem);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        String deviceName = connectedDevices.get(position).getDeviceName();
        int deviceSignal =  connectedDevices.get(position).getSignalStrength();
        int deviceBatteryLevel =  connectedDevices.get(position).getBatteryLabel();

        Drawable batteryImage = getBatteryLevelImage(deviceBatteryLevel);
        Drawable signalStrengthImage = getSignalStrengthImage(deviceSignal);

        holder.textViewDeviceName.setText(deviceName);
        holder.imageViewBatteryLevel.setImageDrawable(batteryImage);
        holder.imageViewSignalStrength.setImageDrawable(signalStrengthImage);
        holder.relativeLayout.setVisibility(View.VISIBLE);
    }


    @Override
    public int getItemCount() {
        return connectedDevices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView imageViewBatteryLevel;
        public final ImageView imageViewSignalStrength;
        public final TextView textViewDeviceName;
        public final RelativeLayout relativeLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            this.imageViewBatteryLevel = (ImageView) itemView.findViewById(R.id.battery_level);
            this.imageViewSignalStrength = (ImageView) itemView.findViewById(R.id.signal_strength);
            this.textViewDeviceName = (TextView) itemView.findViewById(R.id.bluetooth_device_name);
            relativeLayout = (RelativeLayout)itemView.findViewById(R.id.rl_battery_level);
        }
    }


    private Drawable getBatteryLevelImage(int batteryLevel){
        Drawable batteryDrawable;
        if(batteryLevel == 0)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.ic_empty);
        else if(batteryLevel <= 15)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_1);
        else if(batteryLevel <= 30)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_2);
        else if(batteryLevel <= 45)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_3);
        else if(batteryLevel <= 60)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_4);
        else if(batteryLevel <= 75)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_5);
        else if(batteryLevel <= 90)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_6);
        else if(batteryLevel <= 100)
            batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_full);
        else batteryDrawable = ContextCompat.getDrawable(mParent, R.drawable.battery_level_0);

        return batteryDrawable;
    }

    private Drawable getSignalStrengthImage(int signalStrength){
        Drawable signalStrengthDrawable;
        if (signalStrength > ConnectedDeviceInfo.SIGNAL_FULL)
            signalStrengthDrawable = ContextCompat.getDrawable(mParent, R.drawable.bluetooth_signal_4);
        else if (signalStrength > ConnectedDeviceInfo.SIGNAL_HIGH)
            signalStrengthDrawable=  ContextCompat.getDrawable(mParent, R.drawable.bluetooth_signal_3);
        else if (signalStrength > ConnectedDeviceInfo.SIGNAL_MEDIUM)
            signalStrengthDrawable = ContextCompat.getDrawable(mParent, R.drawable.bluetooth_signal_2);
        else if (signalStrength > ConnectedDeviceInfo.SIGNAL_LOW)
            signalStrengthDrawable = ContextCompat.getDrawable(mParent, R.drawable.bluetooth_signal_1);
        else
            signalStrengthDrawable = ContextCompat.getDrawable(mParent, R.drawable.bluetooth_signal_0);

        return signalStrengthDrawable;
    }
}
