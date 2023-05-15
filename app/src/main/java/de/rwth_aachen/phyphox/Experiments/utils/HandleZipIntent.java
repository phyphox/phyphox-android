package de.rwth_aachen.phyphox.Experiments.utils;

import static de.rwth_aachen.phyphox.GlobalConfig.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.UUID;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentListActivity;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.PhyphoxFile;
import de.rwth_aachen.phyphox.R;

//This asyncTask extracts a zip file to a temporary directory
//When it's done, it either opens a single phyphox file or asks the user how to handle multiple phyphox files
public class HandleZipIntent  extends AsyncTask<String, Void, String> {

    private Intent intent; //The intent to read from
    private WeakReference<ExperimentListActivity> parent;
    BluetoothDevice preselectedDevice = null;
    private ProgressDialog progress;

    private HandleZipCallback handleZipCallback;

    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    public HandleZipIntent(Intent intent, ExperimentListActivity parent, ProgressDialog progress) {
        this.intent = intent;
        this.parent = new WeakReference<ExperimentListActivity>(parent);
        this.progress = progress;
    }

    public HandleZipIntent(Intent intent, ExperimentListActivity parent, BluetoothDevice preselectedDevice) {
        this.intent = intent;
        this.parent = new WeakReference<ExperimentListActivity>(parent);
        this.preselectedDevice = preselectedDevice;
    }

    //Copying is done on a second thread...
    protected String doInBackground(String... params) {
        PhyphoxFile.PhyphoxStream phyphoxStream = PhyphoxFile.openXMLInputStream(intent, parent.get());
        if (!phyphoxStream.errorMessage.isEmpty()) {
            return phyphoxStream.errorMessage;
        }

        //Copy the input stream to a random file name
        try {
            //Prepare temporary directory
            File tempPath = new File(parent.get().getFilesDir(), "temp_zip");
            if (!tempPath.exists()) {
                if (!tempPath.mkdirs())
                    return "Could not create temporary directory to extract zip file.";
            }
            String[] files = tempPath.list();
            for (String file : files) {
                if (!(new File(tempPath, file).delete()))
                    return "Could not clear temporary directory to extract zip file.";
            }

            ZipInputStream zis = new ZipInputStream(phyphoxStream.inputStream);

            ZipEntry entry;
            byte[] buffer = new byte[2048];
            while((entry = zis.getNextEntry()) != null) {
                File f = new File(tempPath, entry.getName());
                String canonicalPath = f.getCanonicalPath();
                if (!canonicalPath.startsWith(tempPath.getCanonicalPath())) {
                    return "Security exception: The zip file appears to be tempered with to perform a path traversal attack. Please contact the source of your experiment package or contact the phyphox team for details and help on this issue.";
                }
                FileOutputStream out = new FileOutputStream(f);
                int size = 0;
                while ((size = zis.read(buffer)) > 0)
                {
                    out.write(buffer, 0, size);
                }
                out.close();
            }
            zis.close();
        } catch (Exception e) {
            return "Error loading zip file: " + e.getMessage();
        }

        return "";
    }

    @Override
    //Call the parent callback when we are done.
    protected void onPostExecute(String result) {
        //zipReady(parent.get(), result, preselectedDevice);
        handleZipCallback.onSuccess(result);

    }

    public void setCallback(HandleZipCallback callback){
        this.handleZipCallback = callback;
    }


    public void zipReady(Activity activity, String result, BluetoothDevice preselectedDevice) {

            if (progress != null)
                progress.dismiss();
            if (result.isEmpty()) {
                File tempPath = new File(activity.getFilesDir(), "temp_zip");
                final File[] files = tempPath.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.endsWith(".phyphox");
                    }
                });
                if (files.length == 0) {
                    Toast.makeText(activity, "Error: There is no valid phyphox experiment in this zip file.", Toast.LENGTH_LONG).show();
                } else if (files.length == 1) {
                    //Create an intent for this file
                    Intent intent = new Intent(activity, Experiment.class);
                    intent.setData(Uri.fromFile(files[0]));
                    if (preselectedDevice != null)
                        intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, preselectedDevice.getAddress());
                    intent.setAction(Intent.ACTION_VIEW);

                    //Open the file
                    activity.startActivity(intent);
                } else {

                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                    View view = inflater.inflate(R.layout.open_multipe_dialog, null);
                    final Activity parent = activity;
                    builder.setView(view)
                            .setPositiveButton(R.string.open_save_all, (dialog, id) -> {
                                for (File file : files) {
                                    if (!Helper.experimentInCollection(file, parent))
                                        file.renameTo(new File(activity.getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                                }
                                // TODO loadExperimentList();
                                dialog.dismiss();
                            })
                            .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
                    AlertDialog dialog = builder.create();

                    ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                    LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

                    dialog.setTitle(activity.getResources().getString(R.string.open_zip_title));

                    Vector<ExperimentsInCategory> zipExperiments = new Vector<>();

                    //Load experiments from local files
                    for (File file : files) {
                        //Load details for each experiment
                        try {
                            InputStream input = new FileInputStream(file);
                            //TODO loadExperimentInfo(input, file.getName(), "temp_zip", false, zipExperiments, null, null);
                            input.close();
                        } catch (IOException e) {
                            Log.e("zip", e.getMessage());
                            Toast.makeText(parent, "Error: Could not load experiment \"" + file + "\" from zip file.", Toast.LENGTH_LONG).show();
                        }
                    }

                    Collections.sort(zipExperiments, new CategoryComparator());

                    for (ExperimentsInCategory cat : zipExperiments) {
                        if (preselectedDevice != null)
                            cat.setPreselectedBluetoothAddress(preselectedDevice.getAddress());
                        cat.addToParent(catList);
                    }

                    dialog.show();
                }
            } else {
                Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
            }
        }



}

