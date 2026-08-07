package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.widget.Toast;

import androidx.camera.core.CameraControl;

import java.util.ArrayList;

public class FlashlightOutput {
    private FlashLightManager flashLightManager;
    private CameraManager cameraManager;
    DataInput intensityInput = null;
    DataInput frequencyInput = null;
    DataInput dutyCycleInput = null;

    private final Context context;
    private BroadcastReceiver batteryReceiver;
    private boolean receiverRegistered = false;
    private static final int OVERHEAT_THRESHOLD = 450;
    private static final int COOLDOWN_THRESHOLD = 400;

    public FlashlightOutput(Context context,CameraManager cameraManager) {
        this.context = context;
        this.cameraManager = cameraManager;
        setupThermalMonitoring();
    }

    private void setupThermalMonitoring() {
        batteryReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                if (flashLightManager != null) {
                    if (temp >= OVERHEAT_THRESHOLD && !flashLightManager.isOverheated()) {
                        flashLightManager.setOverheated(true);
                        stop();
                        notifyUserOfOverheat();
                    } else if (temp <= COOLDOWN_THRESHOLD && flashLightManager.isOverheated()) {
                        flashLightManager.setOverheated(false);
                    }
                }
            }
        };
    }

    public void initHardware(CameraControl cameraControl) {
        if (flashLightManager != null)
            flashLightManager.release();
        this.flashLightManager = new FlashLightManager(cameraManager, cameraControl);
    }

    public FlashLightManager getManager() {
        if(flashLightManager != null){
            return flashLightManager;
        }
        return null;
    }

    public void start(){
        if (!receiverRegistered && context != null) {
            context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            receiverRegistered = true;
        }
        if (flashLightManager == null)
            return;

        updateState();
        flashLightManager.startStrobeLoop();
    }

    public void updateState() {
        double intensity = intensityInput != null ? intensityInput.getValue() : 1.0;
        double frequency = frequencyInput != null ? frequencyInput.getValue() : 0.0;
        double dutycycle = dutyCycleInput != null ? dutyCycleInput.getValue() : 0.5;

        flashLightManager.updateFlashState(intensity, frequency, dutycycle);
    }

    public void stop(){
        if (receiverRegistered && context != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
            }
            receiverRegistered = false;
        }

        flashLightManager.stopStrobe();
    }

    public boolean usesStrobe() {
        return frequencyInput != null && (frequencyInput.isBuffer || frequencyInput.getValue() > 0);
    }

    private void notifyUserOfOverheat() {
        if (context instanceof Activity && !((Activity) context).isFinishing()) {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.device_overheating))
                    .setMessage(context.getString(R.string.device_heating_serious))
                    .setPositiveButton(android.R.string.ok, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .show();
        } else {
            Toast.makeText(context, context.getString(R.string.device_heating_serious), Toast.LENGTH_LONG).show();
        }
    }
}
