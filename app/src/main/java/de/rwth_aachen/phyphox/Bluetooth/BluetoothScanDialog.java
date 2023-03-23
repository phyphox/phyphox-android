package de.rwth_aachen.phyphox.Bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;

/**
 * Created by Sebastian Staacks on 03.02.18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothScanDialog {

    private final Activity parentActivity;
    private final Context ctx;
    private final BluetoothAdapter bta;
    private AlertDialog dialog;
    private TextView title;
    private ListView list;
    private DeviceListAdapter listAdapter;
    private BluetoothDeviceInfo selectedDevice = null;
    private final Object lock = new Object();

    private String nameFilter;
    private UUID uuidFilter;
    private Set<String> supportedNameFilter;
    private Set<UUID> supportedUUIDFilter;

    private final Boolean autoConnect;

    ArrayList<String> macAddresses = new ArrayList<>();

    public BluetoothScanDialog(Boolean autoConnect, final Activity activity, final Context context, BluetoothAdapter bta) {
        this.autoConnect = autoConnect;
        this.parentActivity = activity;
        this.ctx = context;
        this.bta = bta;

        activity.runOnUiThread(() -> {

            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);

            if (!autoConnect) {
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.bluetooth_scan_dialog, null);
                builder.setView(view)
                        .setPositiveButton(R.string.bt_more_info_link_button, (dialog, which) -> {
                            Uri uri = Uri.parse(context.getString(R.string.bt_more_info_link_url));
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                                activity.startActivity(intent);
                            }
                            dialog.dismiss();
                        });
                title = view.findViewById(R.id.bluetooth_scan_dialog_title);
                list = view.findViewById(R.id.bluetooth_scan_dialog_items);
            }
            builder.setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());

            dialog = builder.create();

            dialog.setOnDismissListener(dialogInterface -> {
                synchronized (lock) {
                    lock.notify();
                }
            });

            if (!autoConnect) {
                listAdapter = new DeviceListAdapter();
                list.setAdapter(listAdapter);
                list.setOnItemClickListener((adapterView, view, pos, l) -> {
                    if (!listAdapter.getDevice(pos).supported && !listAdapter.getDevice(pos).phyphoxService)
                        return;
                    selectedDevice = listAdapter.getDevice(pos);
                    dialog.dismiss();
                });

                dialog.setTitle(context.getResources().getString(R.string.bt_pick_device));
            }
        });
    }

    private final BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                final Set<UUID> uuids = new HashSet<>();

                //Search scanRecord for UUIDs
                int index = 0;
                while (index < scanRecord.length - 2) {
                    int length = scanRecord[index];
                    if (index + length >= scanRecord.length) {
                        Log.d("bluetooth", "Malformed scanRecord, length too long.");
                        break;
                    }
                    if (length == 0)
                        break;
                    int type = scanRecord[index+1];

                    if (type == 0x02 || type == 0x03) {
                        for (int subindex = 0; subindex < length-1; subindex += 2) {
                            String uuid16str = String.format("%02x%02x", scanRecord[index + subindex + 2 + 1], scanRecord[index + subindex + 2]);
                            UUID uuid = UUID.fromString(Bluetooth.baseUUID.toString().substring(0, 4) + uuid16str + Bluetooth.baseUUID.toString().substring(8));
                            uuids.add(uuid);
//                                Log.d("bluetooth", "Device advertised: " + (device.getName() != null ? device.getName() : "null") + ", 16bit: " + uuid.toString());
                        }
                    } else if (type == 0x04 || type == 0x05) {
                        for (int subindex = 0; subindex < length-1; subindex += 4) {
                            String uuid32str = String.format("%02x%02x%02x%02x", scanRecord[index + subindex + 2 + 3], scanRecord[index + subindex + 2 + 2], scanRecord[index + subindex + 2 + 1], scanRecord[index + subindex + 2]);
                            UUID uuid = UUID.fromString(uuid32str + Bluetooth.baseUUID.toString().substring(8));
                            uuids.add(uuid);
//                                Log.d("bluetooth", "Device advertised: " + (device.getName() != null ? device.getName() : "null") + ", 32bit: " + uuid.toString());
                        }
                    } else if (length == 17 && (type == 0x06 || type == 0x07)) {
                        //128 bit UUIDs
                        for (int subindex = 0; subindex < length-1; subindex += 16) {

                            long leastSignificant = 0;
                            for (int i = 7; i >= 0; i--) {
                                leastSignificant <<= 8;
                                leastSignificant |= (scanRecord[index + subindex + 2 + i] & 0xFF);
                            }

                            long mostSignificant = 0;
                            for (int i = 7; i >= 0; i--) {
                                mostSignificant <<= 8;
                                mostSignificant |= (scanRecord[index + subindex + 2 + 8 + i] & 0xFF);
                            }

                            uuids.add(new UUID(mostSignificant, leastSignificant));
//                                Log.d("bluetooth", "Device advertised: " + (device.getName() != null ? device.getName() : "null") + ", 128bit: " + new UUID(mostSignificant, leastSignificant).toString());

                        }
                    }
                    index += length+1;
                }

                if (device.getName() == null || (!(nameFilter == null || nameFilter.isEmpty()) && !device.getName().contains(nameFilter))) {
                    return;
                }

                if (!(uuidFilter == null) && !uuids.contains(uuidFilter)) {
                    return;
                }

                boolean isSupported = (supportedNameFilter == null || supportedNameFilter.isEmpty());
                if (!isSupported) {
                    for (String name : supportedNameFilter)
                        if (device.getName().contains(name)) {
                            isSupported = true;
                            break;
                        }
                }

                if (supportedUUIDFilter != null) {
                    for (UUID uuid : supportedUUIDFilter) {
                        isSupported |= uuids.contains(uuid);
                    }
                }

                final boolean supported = isSupported;

                final boolean phyphoxService = uuids.contains(Bluetooth.phyphoxServiceUUID);

                final BluetoothDeviceInfo deviceInfo = new BluetoothDeviceInfo(device, supported, phyphoxService, uuids, rssi);

                if (autoConnect) {
                    if (supported || phyphoxService) {
                        selectedDevice = deviceInfo;
                        dialog.dismiss();
                    }
                } else {
                    parentActivity.runOnUiThread(() -> {
                        String macAddress = deviceInfo.device.getAddress();
                        if (!macAddresses.contains(macAddress)) {
                            //TODO: improve the addition of macaddress or change it
                            macAddresses.add(macAddress);
                            listAdapter.addDevice(deviceInfo);
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        };

    public boolean scanPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && (ContextCompat.checkSelfPermission(this.parentActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            //Android 6.0: No permission? Request it!
            final Activity parent = this.parentActivity;

            parent.runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent);
                builder.setMessage(parent.getResources().getText(R.string.bt_location_explanation));
                builder.setCancelable(true);
                builder.setPositiveButton(parent.getResources().getText(R.string.doContinue),
                        (dialog, id) -> {
                            dialog.cancel();
                            ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                            //We will stop here. If the user grants the permission, the permission callback will restart the action with the same intent
                        });
                builder.setNegativeButton(parent.getResources().getText(R.string.cancel), (dialog, id) -> dialog.cancel());
                final AlertDialog alert = builder.create();
                alert.show();
            });

            return false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (ContextCompat.checkSelfPermission(this.parentActivity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this.parentActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            //Android 12+: New Bluetooth scan permission required but no location permission
            final Activity parent = this.parentActivity;

            parent.runOnUiThread(() -> {
                dialog.cancel();
                ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 0);
            });
            return false;
        }

        return true;
    }

    public boolean locationEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;

        LocationManager locationManager =( LocationManager)parentActivity.getSystemService(Context.LOCATION_SERVICE);
        if (!( locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {

            //Android 6.0: Location service not enabled? Ask to enable it.
            final Activity parent = this.parentActivity;
            parent.runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent);
                builder.setMessage(parent.getResources().getText(R.string.bt_location_service_explanation));
                builder.setCancelable(true);
                builder.setPositiveButton(parent.getResources().getText(R.string.doContinue),
                        (dialog, id) -> {
                            dialog.cancel();
                            parent.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        });
                builder.setNegativeButton(parent.getResources().getText(R.string.cancel), (dialog, id) -> dialog.cancel());
                final AlertDialog alert = builder.create();
                alert.show();
            });

            return false;
        }
        return true;
    }

    public BluetoothDeviceInfo getBluetoothDevice(final String nameFilter,
                                                  final UUID uuidFilter,
                                                  final Set<String> supportedNameFilter,
                                                  final Set<UUID> supportedUUIDFilter,
                                                  final String idString) {
        this.nameFilter = nameFilter;
        this.uuidFilter = uuidFilter;
        this.supportedNameFilter = supportedNameFilter;
        this.supportedUUIDFilter = supportedUUIDFilter;

        if (!scanPermission())
            return null;

        if (!locationEnabled())
            return null;

        bta.startLeScan(scanCallback);

        parentActivity.runOnUiThread(() -> {
            String notification;
            if (nameFilter == null || nameFilter.isEmpty()) {
                notification = ctx.getResources().getString(R.string.bt_scanning_generic) + (idString != null && !idString.isEmpty() ? " (" + idString + ")" : "");
            } else {
                notification = ctx.getResources().getString(R.string.bt_scanning_specific1) + " \"" + nameFilter + "\" " + ctx.getResources().getString(R.string.bt_scanning_specific2) + (idString != null && !idString.isEmpty() ? " (" + idString + ")" : "");
            }
            if (autoConnect)
                dialog.setMessage(notification);
            else
                title.setText(notification);
            dialog.show();
        });

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                //
            }
        }
        bta.stopLeScan(scanCallback);

        return selectedDevice;
    }


    public class BluetoothDeviceInfo {
        public BluetoothDevice device;
        Boolean supported;
        public Boolean phyphoxService;
        public Set<UUID> uuids;
        int lastRSSI;
        boolean oneOfMany;
        boolean strongestSignal;

        BluetoothDeviceInfo (BluetoothDevice device, Boolean supported, Boolean phyphoxService, Set<UUID> uuids, int lastRSSI) {
            this.device = device;
            this.supported = supported;
            this.phyphoxService = phyphoxService;
            this.uuids = uuids;
            this.lastRSSI = lastRSSI;
            this.strongestSignal = true;
            this.oneOfMany = false;
        }
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDeviceInfo> devices;
        private LayoutInflater inflator;
        public DeviceListAdapter() {
            super();
            devices = new ArrayList<>();
            inflator = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        public void addDevice(BluetoothDeviceInfo device) {
            BluetoothDeviceInfo foundItem = null;
            for (BluetoothDeviceInfo item : devices) {
                if (item.device.equals(device.device)) {
                    foundItem = item;
                } else if (item.device.getName().equals(device.device.getName())) {
                    item.oneOfMany = true;
                    device.oneOfMany = true;
                    if (item.lastRSSI > device.lastRSSI) {
                        device.strongestSignal = false;
                    } else {
                        item.strongestSignal = false;
                    }
                }
            }
            if (foundItem != null) {
                foundItem.uuids.addAll(device.uuids);
                foundItem.supported |= device.supported;
                foundItem.phyphoxService |= device.phyphoxService;
                foundItem.lastRSSI = device.lastRSSI;
                foundItem.strongestSignal = device.strongestSignal;
            } else
                devices.add(device);
        }
        public BluetoothDeviceInfo getDevice(int position) {
            return devices.get(position);
        }

        public void clear() {
            devices.clear();
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int i) {
            return devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            SubViews subViews;
            // General ListView optimization code.
            if (view == null) {
                view = inflator.inflate(R.layout.bluetooth_scan_dialog_entry, null);
                subViews = new SubViews();
                subViews.deviceName = view.findViewById(R.id.device_name);
                subViews.notSupported = view.findViewById(R.id.device_not_supported);
                subViews.signalStrength = view.findViewById(R.id.signal_strength);
                view.setTag(subViews);
            } else {
                subViews = (SubViews) view.getTag();
            }
            BluetoothDeviceInfo deviceInfo = devices.get(i);
            final String deviceName = deviceInfo.device.getName();
            if (deviceName != null && deviceName.length() > 0)
                subViews.deviceName.setText(deviceName);
            else
                subViews.deviceName.setText(R.string.unknown);
            if (deviceInfo.supported || deviceInfo.phyphoxService) {
                subViews.notSupported.setVisibility(View.GONE);
                ((RelativeLayout.LayoutParams)subViews.deviceName.getLayoutParams()).addRule(RelativeLayout.CENTER_VERTICAL);
            } else {
                subViews.notSupported.setVisibility(View.VISIBLE);
            }

            int color;
            if(deviceInfo.supported || deviceInfo.phyphoxService){
                if(deviceInfo.oneOfMany && deviceInfo.strongestSignal){
                   color = ctx.getResources().getColor(R.color.phyphox_primary);
                }else{
                    if(Helper.isDarkTheme(ctx.getResources())){
                        color = ctx.getResources().getColor(R.color.phyphox_white_100);
                    } else{
                        color = ctx.getResources().getColor(R.color.phyphox_black_80);
                    }
                }
            } else{
               color = ctx.getResources().getColor(R.color.phyphox_white_50_black_50);
            }

            subViews.deviceName.setTextColor(color);

            if (deviceInfo.lastRSSI > -30)
                subViews.signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bluetooth_signal_4));
            else if (deviceInfo.lastRSSI > -50)
                subViews.signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bluetooth_signal_3));
            else if (deviceInfo.lastRSSI > -70)
                subViews.signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bluetooth_signal_2));
            else if (deviceInfo.lastRSSI > -90)
                subViews.signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bluetooth_signal_1));
            else
                subViews.signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.bluetooth_signal_0));

            subViews.signalStrength.setColorFilter(Helper.getAdjustedColorForImage(ctx));
            return view;
        }

    }

    static class SubViews {
        TextView deviceName;
        TextView notSupported;
        ImageView signalStrength;
    }
}
