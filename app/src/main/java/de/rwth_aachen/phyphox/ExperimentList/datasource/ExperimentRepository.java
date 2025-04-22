package de.rwth_aachen.phyphox.ExperimentList.datasource;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExperimentList.handler.CategoryComparator;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Helper.Helper;
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

    public void loadAndShowMainExperimentList(Activity parent) {
        AssetExperimentLoader assetExperimentLoader = new AssetExperimentLoader(parent, this);

        //Clear the old list first
        categories.clear();
        assetExperimentLoader.showCurrentCameraAvailability();

        assetExperimentLoader.loadAndAddExperimentsFromLocalFiles();
        assetExperimentLoader.loadAndAddExperimentsFromAssets();

        addExperimentCategoriesToLinearLayout(parent.findViewById(R.id.experimentList), parent.getResources());
    }

    public void loadHiddenBluetoothExperiments(Activity parent) {
        AssetExperimentLoader assetExperimentLoader = new AssetExperimentLoader(parent, this);
        assetExperimentLoader.loadAndAddExperimentsFromHiddenBluetoothAssets();
    }

    public void addExperimentCategoriesToLinearLayout(LinearLayout target, Resources res){
        Collections.sort(categories, new CategoryComparator(res));

        target.removeAllViews();

        for (ExperimentsInCategory cat : categories) {
            cat.addToParent(target);
        }
    }

    private void saveFileToMainList(ExperimentShortInfo experimentShortInfo, File file, File folder, ExperimentListEnvironment environment) {
        long crc32 = Helper.getCRC32(file);
        if (!Helper.experimentInCollection(crc32, environment.parent)) {
            file.renameTo(new File(environment.getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
            if (!experimentShortInfo.resources.isEmpty()) {
                File targetFolder = new File(environment.getFilesDir(), Long.toHexString(crc32).toLowerCase());
                targetFolder.mkdirs();
                for (String src : experimentShortInfo.resources) {
                    File resFolder = new File(file.getParentFile().getAbsolutePath(), "res");
                    File srcFile = new File(resFolder, src);
                    File dstFile = new File(targetFolder, src);
                    try {
                        Helper.copyFile(srcFile, dstFile);
                    } catch (Exception e) {
                        Toast.makeText(environment.context, "Error while copying " + srcFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        Log.e("ExperimentRepository", "Error while copying " + srcFile.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void saveAssetToMainList(ExperimentShortInfo experimentShortInfo, ExperimentListEnvironment environment) {
        try {
            String subdir = experimentShortInfo.isTemp != null ? experimentShortInfo.isTemp + "/" : "";
            InputStream in = environment.assetManager.open("experiments/" + subdir + experimentShortInfo.xmlFile);
            OutputStream out = new FileOutputStream(new File(environment.getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Toast.makeText(environment.context, "Error: Could not retrieve assets.", Toast.LENGTH_LONG).show();
        }
    }

    public void saveExperimentsToMainList(ExperimentListEnvironment environment) {
        for (ExperimentsInCategory experimentCats : categories) {
            for (ExperimentShortInfo experimentShortInfo : experimentCats.retrieveExperiments()) {
                if (experimentShortInfo.isAsset) {
                    saveAssetToMainList(experimentShortInfo, environment);
                } else if (experimentShortInfo.isTemp != null && !experimentShortInfo.isTemp.isEmpty()) {
                    File folder = new File(environment.getFilesDir(), experimentShortInfo.isTemp);
                    File file = new File(folder, experimentShortInfo.xmlFile);
                    saveFileToMainList(experimentShortInfo, file, folder, environment);
                } else {
                    File file = new File(experimentShortInfo.xmlFile);
                    saveFileToMainList(experimentShortInfo, file, file.getParentFile(), environment);
                }
            }
        }
    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        if (categories == null)
            return;
        for (ExperimentsInCategory experimentCats : categories) {
            experimentCats.setPreselectedBluetoothAddress(preselectedBluetoothAddress);
        }
    }

    public HashMap<String, Vector<String>> getBluetoothDeviceNameList() {
        return bluetoothDeviceNameList;
    }

    public HashMap<UUID, Vector<String>> getBluetoothDeviceUUIDList() {
        return bluetoothDeviceUUIDList;
    }
}


