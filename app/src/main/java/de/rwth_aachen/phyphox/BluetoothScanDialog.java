package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.apache.poi.hssf.util.HSSFColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

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

    BluetoothScanDialog(final Activity activity, final Context context, BluetoothAdapter bta) {
        this.parentActivity = activity;
        this.ctx = context;
        this.bta = bta;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.bluetooth_scan_dialog, null);
                builder.setView(view)
                       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int id) {
                               dialog.dismiss();
                           }

                       })
                        .setPositiveButton(R.string.bt_more_info_link_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Uri uri = Uri.parse(context.getString(R.string.bt_more_info_link_url));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                                    activity.startActivity(intent);
                                }
                                dialog.dismiss();
                            }
                        });
                dialog = builder.create();

                title = (TextView) view.findViewById(R.id.bluetooth_scan_dialog_title);
                list = (ListView) view.findViewById(R.id.bluetooth_scan_dialog_items);
                listAdapter = new DeviceListAdapter();
                list.setAdapter(listAdapter);
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                        if (!listAdapter.getDevice(pos).supported && !listAdapter.getDevice(pos).phyphoxService)
                            return;
                        selectedDevice = listAdapter.getDevice(pos);
                        dialog.dismiss();
                    }
                });

                dialog.setTitle(context.getResources().getString(R.string.bt_pick_device));
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                });
            }
        });
    }

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
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

                    parentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listAdapter.addDevice(new BluetoothDeviceInfo(device, supported, phyphoxService, uuids, rssi));
                            listAdapter.notifyDataSetChanged();
                        }
                });
            }
        };

    public boolean scanPermission() {
        if (ContextCompat.checkSelfPermission(this.parentActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Android 6.0: No permission? Request it!
            final Activity parent = this.parentActivity;

            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(parent);
                    builder.setMessage(parent.getResources().getText(R.string.bt_location_explanation));
                    builder.setCancelable(false);
                    builder.setPositiveButton(parent.getResources().getText(R.string.doContinue),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                                    //We will stop here. If the user grants the permission, the permission callback will restart the action with the same intent
                                }
                            });
                    final AlertDialog alert = builder.create();
                    alert.show();
                }
            });

            return false;
        }
        return true;
    }

    public BluetoothDeviceInfo getBluetoothDevice(final String nameFilter, final UUID uuidFilter, final Set<String> supportedNameFilter, final Set<UUID> supportedUUIDFilter, final String idString) {
        this.nameFilter = nameFilter;
        this.uuidFilter = uuidFilter;
        this.supportedNameFilter = supportedNameFilter;
        this.supportedUUIDFilter = supportedUUIDFilter;

        if (!scanPermission())
            return null;

        bta.startLeScan(scanCallback);

        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (nameFilter == null || nameFilter.isEmpty()) {
                    title.setText(ctx.getResources().getString(R.string.bt_scanning_generic) + (idString != null && !idString.isEmpty() ? " (" + idString + ")" : ""));
                } else {
                    title.setText(ctx.getResources().getString(R.string.bt_scanning_specific1) + " \"" + nameFilter + " \"" + ctx.getResources().getString(R.string.bt_scanning_specific2) + (idString != null && !idString.isEmpty() ? " (" + idString + ")" : ""));
                }
                dialog.show();
            }
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
        BluetoothDevice device;
        Boolean supported;
        Boolean phyphoxService;
        Set<UUID> uuids;
        int lastRSSI;

        BluetoothDeviceInfo (BluetoothDevice device, Boolean supported, Boolean phyphoxService, Set<UUID> uuids, int lastRSSI) {
            this.device = device;
            this.supported = supported;
            this.phyphoxService = phyphoxService;
            this.uuids = uuids;
            this.lastRSSI = lastRSSI;
        }
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDeviceInfo> devices;
        private LayoutInflater inflator;
        public DeviceListAdapter() {
            super();
            devices = new ArrayList<BluetoothDeviceInfo>();
            inflator = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        public void addDevice(BluetoothDeviceInfo device) {
            boolean inList = false;
            for (BluetoothDeviceInfo item : devices) {
                if (item.device.equals(device.device)) {
                    inList = true;
                    item.uuids.addAll(device.uuids);
                    item.supported |= device.supported;
                    item.phyphoxService |= device.phyphoxService;
                    item.lastRSSI = device.lastRSSI;
                }
            }
            if (!inList)
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
                subViews.deviceName = (TextView) view.findViewById(R.id.device_name);
                subViews.notSupported = (TextView) view.findViewById(R.id.device_not_supported);
                subViews.signalStrength = (ImageView) view.findViewById(R.id.signal_strength);
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

            int color = deviceInfo.supported || deviceInfo.phyphoxService ? ctx.getResources().getColor(R.color.main) : ctx.getResources().getColor(R.color.mainDisabled);
            subViews.deviceName.setTextColor(color);

            if (deviceInfo.lastRSSI > -30)
                subViews.signalStrength.setImageResource(R.drawable.bluetooth_signal_4);
            else if (deviceInfo.lastRSSI > -50)
                subViews.signalStrength.setImageResource(R.drawable.bluetooth_signal_3);
            else if (deviceInfo.lastRSSI > -70)
                subViews.signalStrength.setImageResource(R.drawable.bluetooth_signal_2);
            else if (deviceInfo.lastRSSI > -90)
                subViews.signalStrength.setImageResource(R.drawable.bluetooth_signal_1);
            else
                subViews.signalStrength.setImageResource(R.drawable.bluetooth_signal_0);

            return view;
        }
    }

    static class SubViews {
        TextView deviceName;
        TextView notSupported;
        ImageView signalStrength;
    }
}
