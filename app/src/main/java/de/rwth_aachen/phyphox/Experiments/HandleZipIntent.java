package de.rwth_aachen.phyphox.Experiments;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.PhyphoxFile;

//This asyncTask extracts a zip file to a temporary directory
//When it's done, it either opens a single phyphox file or asks the user how to handle multiple phyphox files
class HandleZipIntent extends AsyncTask<String, Void, String> {
    private Intent intent; //The intent to read from
    private WeakReference<ExperimentListActivity> parent;
    BluetoothDevice preselectedDevice = null;

    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    HandleZipIntent(Intent intent, ExperimentListActivity parent) {
        this.intent = intent;
        this.parent = new WeakReference<ExperimentListActivity>(parent);
    }

    HandleZipIntent(Intent intent, ExperimentListActivity parent, BluetoothDevice preselectedDevice) {
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
        parent.get().zipReady(result, preselectedDevice);
    }
}
