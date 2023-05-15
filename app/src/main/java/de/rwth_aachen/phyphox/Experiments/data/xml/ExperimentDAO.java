package de.rwth_aachen.phyphox.Experiments.data.xml;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.App;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;

public class ExperimentDAO {

    private final Context context;
    List<ExperimentDataModel> experimentDataModels = new ArrayList<>();

    public ExperimentDAO(){
        this.context = App.getContext();
    }

    public List<ExperimentDataModel> getExperimentsFromLocalFile(){
        try {
            //Get all files that end on ".phyphox"
            File[] files = context.getFilesDir().listFiles((dir, filename) -> filename.endsWith(".phyphox"));

            for (File file : files) {
                if (file.isDirectory())
                    continue;
                //Load details for each experiment
                InputStream input = context.openFileInput(file.getName());
                //TODO loadExperimentInfo(input, file.getName(), null, false, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(context, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();
        }

        return null;
    }

    public List<ExperimentDataModel> getExperimentsFromAsset() {
        try {
            AssetManager assetManager = context.getAssets();
            final String[] experimentXMLs = assetManager.list("experiments"); //All experiments are placed in the experiments folder

            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                if (!experimentXML.endsWith(".phyphox"))
                    continue;
                InputStream input = assetManager.open("experiments/" + experimentXML);

                ExperimentInfoXMLParser parser =  new ExperimentInfoXMLParser(input, experimentXML, null,true, null, null, null);

                experimentDataModels.add(parser.getExperimentDataModel());
            }
            //getExperimentsFromTempZip();

        } catch (IOException e) {
            Toast.makeText(context, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();

        }

        return experimentDataModels;

    }

    public void getExperimentsFromTempZip(){
        File tempPath = new File(context.getFilesDir(), "temp_zip");
        if(tempPath.isDirectory()) {
            final File[] files = tempPath.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".phyphox");
                }
            });

            if (files != null & files.length > 1) {

                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        ExperimentInfoXMLParser parser = new ExperimentInfoXMLParser(input, file.getName(), "temp_zip", false, null, null, null);
                        experimentDataModels.add(parser.getExperimentDataModel());
                        input.close();
                    } catch (IOException e) {
                        Log.e("zip", e.getMessage());
                    }
                }

            }
        }
    }


    //Load hidden bluetooth experiments - these are not shown but will be offered if a matching Bluetooth device is found during a scan
    public List<ExperimentDataModel> getHiddenBluetoothExperiments() {
        try {
            AssetManager assetManager = context.getAssets();
            final String[] experimentXMLs = assetManager.list("experiments/bluetooth");
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                InputStream input = assetManager.open("experiments/bluetooth/" + experimentXML);
                //TODO loadExperimentInfo(input, experimentXML, null,true, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList);
            }
        } catch (IOException e) {
            Toast.makeText(context, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

        return null;
    }

}
