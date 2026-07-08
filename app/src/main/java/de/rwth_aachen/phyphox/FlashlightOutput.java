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
    private ArrayList<FlashlightController> controllers = new ArrayList<>();

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
        this.flashLightManager = new FlashLightManager(cameraManager, cameraControl);
    }

    public FlashLightManager getManager() {
        if(flashLightManager != null){
            return flashLightManager;
        }
        return null;
    }

    public void start(boolean restart){
        if (!receiverRegistered && context != null) {
            context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            receiverRegistered = true;
        }
        for(FlashlightController flashlightController: controllers){
            if(restart){
                flashlightController.start();
            }
        }
    }

    public void stop(){
        if (receiverRegistered && context != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
            }
            receiverRegistered = false;
        }

        for(FlashlightController flashlightController: controllers){
            if(flashlightController.isActive()){
                flashlightController.stop();
            }
        }
    }

    public boolean hasStrobeController() {
        for (FlashlightController controller : controllers) {
            if (controller instanceof FlashLightStrobe) {
                return true;
            }
        }
        return false;
    }

    public boolean isStrobeActiveWithFrequency() {
        for (FlashlightController controller : controllers) {
            if (controller instanceof FlashLightStrobe) {
                FlashLightStrobe strobe = (FlashLightStrobe) controller;

                if (strobe.dataInput != null && strobe.dataInput.getValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isStrobeUsingBuffer() {
        for (FlashlightController controller : controllers) {
            if (controller instanceof FlashLightStrobe) {
                FlashLightStrobe strobe = (FlashLightStrobe) controller;
                if (strobe.dataInput != null && strobe.dataInput.isBuffer) {
                    return true;
                }
            }
        }
        return false;
    }

    public void attachController(FlashlightController controller){
        this.controllers.add(controller);
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

    public abstract class FlashlightController {

        public abstract boolean isActive();
        public abstract void start();

        public abstract void stop();
    }

    public class FlashLightStrobe extends FlashlightController {

        DataInput dataInput;
        boolean strobeActive = false;

        FlashLightStrobe(DataInput input){
            this.dataInput = input;
        }

        @Override
        public void start() {
            if (flashLightManager == null) return;
            double frequency = dataInput.getValue();
            if (frequency > 0) {
                flashLightManager.updateRate(frequency);
                flashLightManager.startStrobeLoop();
                strobeActive = true;
            } else {
                stop();
            }
        }

        @Override
        public boolean isActive() { return strobeActive; }

        @Override
        public void stop() {
            flashLightManager.stopStrobe();
            strobeActive = false;

        }

    }

    public class FlashLightIntensity extends FlashlightController {

        DataInput dataInput;
        public boolean flashLightActive = false;

        FlashLightIntensity(DataInput input){
            this.dataInput = input;
        }

        @Override
        public void start() {
            if (flashLightManager == null) return;

            double intensity = dataInput.getValue();
            if (intensity == 0) {
                flashLightManager.turnOfFlashLight();
                flashLightActive = false;
                return;
            }

            if (intensity > 0) {
                flashLightManager.setIntensity(intensity);
            } else {
                flashLightManager.setIntensity(1);
            }
            flashLightActive = true;
        }

        @Override
        public boolean isActive() { return flashLightActive; }

        @Override
        public void stop() {
            if (flashLightManager != null) {
                flashLightManager.turnOfFlashLight();
                flashLightActive = false;
            }
        }
    }
}
