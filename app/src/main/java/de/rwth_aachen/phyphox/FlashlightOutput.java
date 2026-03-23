package de.rwth_aachen.phyphox;

import android.hardware.camera2.CameraManager;

import androidx.camera.core.CameraControl;

import java.util.ArrayList;

public class FlashlightOutput {
    private FlashLightManager flashLightManager;
    private CameraManager cameraManager;
    private ArrayList<FlashlightController> controllers = new ArrayList<>();

    public FlashlightOutput(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
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
        for(FlashlightController flashlightController: controllers){
            if(restart){
                flashlightController.start();
            }
        }
    }

    public void stop(){
        for(FlashlightController flashlightController: controllers){
            if(flashlightController.isActive()){
                flashlightController.stop();
            }
        }
    }

    public void attachController(FlashlightController controller){
        this.controllers.add(controller);
    }

    public abstract class FlashlightController {

        public abstract boolean isActive();
        public abstract void start();

        public abstract void stop();
    }

    public class FlashLightStobe extends FlashlightController {

        DataInput dataInput;
        boolean strobeActive = false;

        FlashLightStobe(DataInput input){
            this.dataInput = input;
        }

        @Override
        public void start() {
            if (flashLightManager == null) return;
            double frequency = dataInput.getValue();
            if (frequency <= 0) {
                stop();
            } else {
                flashLightManager.updateRate(frequency);
                flashLightManager.startStrobeLoop();
                strobeActive = true;
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

            int intensity = (int) dataInput.getValue();
            if(intensity == 0) {
                flashLightManager.turnOfFlashLight();
                flashLightActive = false;
                return;
            }

            if(intensity > 1){
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
