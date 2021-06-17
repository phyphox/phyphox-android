package de.rwth_aachen.phyphox.NetworkConnection;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.Metadata;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.SensorInput;

public class NetworkConnection implements NetworkService.RequestCallback, NetworkDiscovery.DiscoveryCallback {

    public interface ScanDialogDismissedDelegate {
        void networkScanDialogDismissed();
    }

    public interface NetworkConnectionDataPolicyInfoDelegate {
        void dataPolicyInfoDismissed();
    }

    public static class NetworkSendableData {
        public enum DataType {
                BUFFER, METADATA, TIME;
        }

        public DataType type;
        public DataBuffer buffer = null;
        public boolean clear = false;
        public Metadata metadata = null;
        public ExperimentTimeReference timeReference = null;
        public Map<String, String> additionalAttributes = null;
        public NetworkSendableData(DataBuffer buffer, boolean clear) {
            this.type = DataType.BUFFER;
            this.buffer = buffer;
            this.clear = clear;
        }
        public NetworkSendableData(Metadata metadata) {
            this.type = DataType.METADATA;
            this.metadata = metadata;
        }
        public NetworkSendableData(ExperimentTimeReference timeReference) {
            this.type = DataType.TIME;
            this.timeReference = timeReference;
        }
    }

    public static class NetworkReceivableData {
        DataBuffer buffer;
        boolean clear;
        public NetworkReceivableData(DataBuffer buffer, boolean clear) {
            this.buffer = buffer;
            this.clear = clear;
        }
    }

    public String id;
    String privacyURL;

    String address;
    public String specificAddress;
    NetworkDiscovery.Discovery discovery;
    boolean autoConnect;
    NetworkService.Service service;
    NetworkConversion.Conversion conversion;

    Map<String, NetworkSendableData> send;
    Map<String, NetworkReceivableData> receive;
    double interval;

    boolean executeRequested = false;
    boolean dataReady = false;
    List<NetworkService.RequestCallback> requestCallbacks = null;

    Handler mainHandler;
    Toast toast;

    final Handler intervalHandler = new Handler(Looper.getMainLooper());
    boolean running = false;

    public NetworkConnection(String id, String privacyURL, String address, NetworkDiscovery.Discovery discovery, boolean autoConnect, NetworkService.Service service, NetworkConversion.Conversion conversion, Map<String, NetworkSendableData> send, Map<String, NetworkReceivableData> receive, double interval, Context ctx) {
        this.id = id;
        this.privacyURL = privacyURL;
        this.address = address;
        this.discovery = discovery;
        this.autoConnect = autoConnect;
        this.service = service;
        this.conversion = conversion;
        this.send = send;
        this.receive = receive;
        this.interval = interval;

        this.mainHandler = new android.os.Handler(ctx.getMainLooper());

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                toast = Toast.makeText(ctx, "Error", Toast.LENGTH_LONG);
            }
        });
    }

    protected void displayErrorMessage(final String message) {
        // show toast
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                toast.setText(message);
                toast.show();
            }
        });
    }

    public AlertDialog getDataAndPolicyDialog(boolean infoMicrophone, boolean infoLocation, boolean infoSensorData, String[] infoSensorDataList, Context ctx, NetworkConnectionDataPolicyInfoDelegate delegate) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getResources().getString(R.string.networkPrivacyInfo));
        sb.append("\n\n");

        boolean infoUniqueId = false;
        boolean infoDeviceInfo = false;
        boolean infoSensorInfo = false;
        Set<String> infoSensorInfoList = new HashSet<>();

        for (NetworkSendableData sendable : send.values()) {
            if (sendable.type == NetworkSendableData.DataType.METADATA) {
                switch (sendable.metadata.metadata) {
                    case uniqueID:
                        infoUniqueId = true;
                        break;
                    case build:
                    case deviceBaseOS:
                    case deviceBoard:
                    case deviceBrand:
                    case deviceCodename:
                    case deviceManufacturer:
                    case deviceModel:
                    case deviceRelease:
                    case fileFormat:
                    case version:
                        infoDeviceInfo = true;
                        break;
                    case sensorMetadata:
                        infoSensorInfo = true;
                        infoSensorInfoList.add(ctx.getResources().getString(SensorInput.getDescriptionRes(SensorInput.resolveSensorName(sendable.metadata.sensor))));
                        break;
                }
            }
        }

        if (infoUniqueId) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacyUniqueID));
            sb.append("\n");
        }
        if (infoMicrophone) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacySensorMicrophone));
            sb.append("\n");
        }
        if (infoLocation) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacySensorLocation));
            sb.append("\n");
        }
        if (infoSensorData) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacySensorData));
            sb.append(" ");
            Arrays.sort(infoSensorDataList);
            sb.append(TextUtils.join(", ", infoSensorDataList));
            sb.append("\n");
        }
        if (infoDeviceInfo) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacyDeviceInfo));
            sb.append("\n");
        }
        if (infoSensorInfo) {
            sb.append("- ");
            sb.append(ctx.getResources().getString(R.string.networkPrivacySensorInfo));
            sb.append(" ");
            String[] list = infoSensorInfoList.toArray(new String[0]);
            Arrays.sort(list);
            sb.append(TextUtils.join(", ", list));
            sb.append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setMessage(sb.toString())
                .setTitle(R.string.networkPrivacyWarning)
                .setPositiveButton(R.string.networkVisitPrivacyURL, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Uri uri = Uri.parse(privacyURL);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        if (intent.resolveActivity(ctx.getPackageManager()) != null) {
                            ctx.startActivity(intent);
                        }
                    }
                })
                .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        delegate.dataPolicyInfoDismissed();
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        delegate.dataPolicyInfoDismissed();
                    }
                });
        return builder.create();
    }

    ScanDialogDismissedDelegate dismissedDelegate = null;
    public void connect(ScanDialogDismissedDelegate dismissedDelegate) {
        if (specificAddress != null) {
            connect(specificAddress);
            if (dismissedDelegate != null)
                dismissedDelegate.networkScanDialogDismissed();
        } else {
            if (discovery != null) {
                discovery.startDiscovery(this);
                this.dismissedDelegate = dismissedDelegate;
            } else {
                specificAddress = address;
                connect(specificAddress);
                if (dismissedDelegate != null)
                    dismissedDelegate.networkScanDialogDismissed();
            }
        }
    }

    public void connect(String address) {
        if (address == null)
            return;
        dataReady = false;
        service.connect(address);
    }

    public void disconnect() {
        dataReady = false;
    }

    public void newItem(String name, String address) {
        if (dismissedDelegate != null)
            dismissedDelegate.networkScanDialogDismissed();
        discovery.stopDiscovery();
        specificAddress = address;
        connect(specificAddress);
    }

    public void start() {
        if (interval > 0) {
            running = true;
            intervalExecute.run();
        }
    }

    public void stop() {
        running = false;
        intervalHandler.removeCallbacksAndMessages(intervalExecute);
    }

    private Runnable intervalExecute = new Runnable() {
        @Override
        public void run() {
            intervalHandler.removeCallbacksAndMessages(intervalExecute);
            execute(null);
            if (running)
                intervalHandler.postDelayed(intervalExecute, (long)(interval*1000));
        }
    };

    public void execute(List<NetworkService.RequestCallback> requestCallbacks) {
        if (this.requestCallbacks == null)
            this.requestCallbacks = new ArrayList<>();
        if (requestCallbacks != null)
            this.requestCallbacks.addAll(requestCallbacks);
        executeRequested = true;
    }

    public void doExecute() {
        if (executeRequested) {
            this.requestCallbacks.add(this);

            service.execute(send, requestCallbacks);
            for (Map.Entry<String, NetworkSendableData> item : send.entrySet()) {
                if (item.getValue().buffer == null)
                    continue;
                if (item.getValue().clear)
                    item.getValue().buffer.clear(false);
            }

            this.requestCallbacks = null;
            executeRequested = false;
        }
    }

    public void requestFinished(NetworkService.ServiceResult result) {
        switch (result.result) {
            case conversionError:
                displayErrorMessage("Network error: Conversion failed. " + result.message);
                break;
            case genericError:
                displayErrorMessage("Network error: Generic error. " + result.message);
                break;
            case noConnection:
                displayErrorMessage("Network error: No connection to network service.");
                break;
            case timeout:
                displayErrorMessage("Network error: The connection timed out.");
                break;
            case success:
                byte[][] data = service.getResults();
                if (data != null) {
                    try {
                        conversion.prepare(data);
                        dataReady = true;
                    } catch (NetworkConversion.ConversionException e) {
                        displayErrorMessage(e.getMessage());
                    }
                }
        }
    }

    public void pushDataToBuffers() {
        if (!dataReady)
            return;

        for (Map.Entry<String, NetworkReceivableData> item : receive.entrySet()) {
            try {
                if (item.getValue().buffer == null)
                    continue;
                Double[] data = conversion.get(item.getKey());
                if (item.getValue().clear)
                    item.getValue().buffer.clear(false);
                item.getValue().buffer.append(data, data.length);
                item.getValue().buffer.markSet();
            } catch (NetworkConversion.ConversionException e) {
                displayErrorMessage(e.getMessage());
            }
        }

        dataReady = false;
    }
}
