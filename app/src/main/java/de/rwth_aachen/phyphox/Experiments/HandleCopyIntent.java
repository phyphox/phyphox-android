package de.rwth_aachen.phyphox.Experiments;


import static de.rwth_aachen.phyphox.GlobalConfig.EXPERIMENT_ISTEMP;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;

import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.PhyphoxFile;

//This asyncTask extracts a zip file to a temporary directory
//When it's done, it either opens a single phyphox file or asks the user how to handle multiple phyphox files
class HandleCopyIntent  extends AsyncTask<String, Void, String> {
    private Intent intent; //The intent to read from
    private WeakReference<ExperimentListActivity> parent;
    private File file = null;
    private ProgressDialog progress;

    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    HandleCopyIntent(Intent intent, ExperimentListActivity parent, ProgressDialog progress) {
        this.intent = intent;
        this.parent = new WeakReference<ExperimentListActivity>(parent);
        this.progress = progress;
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
            File tempPath = new File(parent.get().getFilesDir(), "temp");
            if (!tempPath.exists()) {
                if (!tempPath.mkdirs())
                    return "Could not create temporary directory to store temporary file.";
            }
            String[] files = tempPath.list();
            for (String file : files) {
                if (!(new File(tempPath, file).delete()))
                    return "Could not clear temporary directory for temporary file.";
            }

            //Copy the input stream to a random file name
            try {
                file = new File(tempPath, UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"); //Random file name
                FileOutputStream output = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = phyphoxStream.inputStream.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                phyphoxStream.inputStream.close();
            } catch (Exception e) {
                file = null;
                return "Error during file transfer: " + e.getMessage();
            }
        } catch (Exception e) {
            file = null;
            return "Error loading file: " + e.getMessage();
        }

        return "";
    }

    @Override
    //Call the parent callback when we are done.
    protected void onPostExecute(String result) {
        if (!result.isEmpty()) {
            showError(result);
            return;
        }

        if (file == null) {
            showError("File is null.");
            return;
        }

        //Create an intent for this file
        Intent intent = new Intent(parent.get(), Experiment.class);
        intent.setData(Uri.fromFile(file));
        intent.putExtra(EXPERIMENT_ISTEMP, "temp");
        intent.setAction(Intent.ACTION_VIEW);

        //Open the file
        parent.get().handleIntent(intent);
    }

    void showError(String error) {
        if (progress != null)
            progress.dismiss();
        Toast.makeText(parent.get(), error, Toast.LENGTH_LONG).show();
    }

}
