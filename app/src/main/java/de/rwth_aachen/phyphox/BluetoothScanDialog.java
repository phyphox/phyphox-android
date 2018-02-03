package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

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
    private BluetoothDevice selectedDevice = null;
    private final Object lock = new Object();

    private String nameFilter;

    BluetoothScanDialog(Activity activity, final Context context, BluetoothAdapter bta) {
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

                       });
                dialog = builder.create();

                title = (TextView) view.findViewById(R.id.bluetooth_scan_dialog_title);
                list = (ListView) view.findViewById(R.id.bluetooth_scan_dialog_items);
                listAdapter = new DeviceListAdapter();
                list.setAdapter(listAdapter);
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
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
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    parentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device.getName() == null || (!nameFilter.isEmpty() && !device.getName().contains(nameFilter))) {
                                return;
                            }
                            listAdapter.addDevice(device);
                            listAdapter.notifyDataSetChanged();
                        }
                });
            }
        };

    public BluetoothDevice getBluetoothDevice(final String nameFilter) {
        this.nameFilter = nameFilter;
        bta.startLeScan(scanCallback);

        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (nameFilter.isEmpty()) {
                    title.setText(ctx.getResources().getString(R.string.bt_scanning_generic));
                } else {
                    title.setText(ctx.getResources().getString(R.string.bt_scanning_specific1) + " \"" + nameFilter + " \"" + ctx.getResources().getString(R.string.bt_scanning_specific2));
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


    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> devices;
        private LayoutInflater inflator;
        public DeviceListAdapter() {
            super();
            devices = new ArrayList<BluetoothDevice>();
            inflator = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        public void addDevice(BluetoothDevice device) {
            if(!devices.contains(device)) {
                devices.add(device);
            }
        }
        public BluetoothDevice getDevice(int position) {
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
                subViews.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                subViews.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(subViews);
            } else {
                subViews = (SubViews) view.getTag();
            }
            BluetoothDevice device = devices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                subViews.deviceName.setText(deviceName);
            else
                subViews.deviceName.setText(R.string.unknown);
            subViews.deviceAddress.setText(device.getAddress());
            return view;
        }
    }

    static class SubViews {
        TextView deviceName;
        TextView deviceAddress;
    }
}
