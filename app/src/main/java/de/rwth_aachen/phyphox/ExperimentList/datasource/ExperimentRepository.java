package de.rwth_aachen.phyphox.ExperimentList.datasource;

import android.app.Activity;
import android.widget.LinearLayout;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExperimentList.handler.CategoryComparator;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.R;

public class ExperimentRepository{

    Vector<ExperimentsInCategory> categories;

    /**
     * Collects names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
     */
    public final HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>();

    /**
     *  Collects uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices
     */
    public final HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>();

    public ExperimentRepository() {

    }

    public void addExperiment(ExperimentShortInfo shortInfo, Activity parent) {
        AssetExperimentLoader assetExperimentLoader = new AssetExperimentLoader(parent, this);
        assetExperimentLoader.addExperiment(shortInfo);
    }

    public void loadExperimentList(Activity parent) {
        AssetExperimentLoader assetExperimentLoader = new AssetExperimentLoader(parent, this);

        //Clear the old list first
        categories.clear();
        assetExperimentLoader.showCurrentCameraAvailability();

        assetExperimentLoader.loadAndAddExperimentsFromLocalFiles();
        assetExperimentLoader.loadAndAddExperimentsFromAssets();

        addExperimentCategoryToParent(parent);

        assetExperimentLoader.loadAndAddExperimentsFromHiddenBluetoothAssets();

    }

    public void addExperimentCategoryToParent(Activity parent){
        Collections.sort(categories, new CategoryComparator(parent.getResources()));

        LinearLayout parentLayout = parent.findViewById(R.id.experimentList);
        parentLayout.removeAllViews();

        for (ExperimentsInCategory cat : categories) {
            cat.addToParent(parentLayout);
        }
    }

    public HashMap<String, Vector<String>> getBluetoothDeviceNameList() {
        return bluetoothDeviceNameList;
    }

    public HashMap<UUID, Vector<String>> getBluetoothDeviceUUIDList() {
        return bluetoothDeviceUUIDList;
    }
}


