package de.rwth_aachen.phyphox.Helper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.BuildConfig;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.R;

public class DataExportUtility{

    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1001;
    public static final String MIME_TYPE_CSV_MINI = "text/csv";
    public static final String MIME_TYPE_CSV_ZIP = "application/zip";
    public static final String MIME_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_TYPE_XLS = "application/vnd.ms-excel";
    public static final String MIME_TYPE_PHYPHOX = "application/octet-stream";

    public static void createFileInDownloads(File exportFile, String filenameBase, String mimeType, Activity c){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DataExportUtility.createFileInDownloadsFromQ(exportFile, filenameBase, mimeType, c);
        } else {
            if (ContextCompat.checkSelfPermission(c, "android.permission.WRITE_EXTERNAL_STORAGE")
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(c,
                        "android.permission.WRITE_EXTERNAL_STORAGE")) {
                    Toast.makeText(c, "Storage permission is needed to save files to Downloads", Toast.LENGTH_LONG).show();
                }

                ActivityCompat.requestPermissions(c,
                        new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                DataExportUtility.saveFileToDownloadsPreQ(c, exportFile, filenameBase);
            }

        }
    }


    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static void createFileInDownloadsFromQ(File file, String fileName, String mimeType, Activity activity){
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.IS_PENDING, 1); // Mark file as pending

        ContentResolver resolver = activity.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = resolver.insert(collection, values);

        if (fileUri != null) {
            try (OutputStream out = resolver.openOutputStream(fileUri);
                 InputStream in = Files.newInputStream(file.toPath())) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }

                Toast.makeText(activity, "File saved to Downloads", Toast.LENGTH_SHORT).show();

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0); // Mark as finished
                resolver.update(fileUri, values, null, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void saveFileToDownloadsPreQ(Context context, File sourceFile, String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File destinationFile = new File(downloadsDir, fileName);

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            Toast.makeText(context, "File saved to Downloads", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void shareFile(File file, String mimeType, Activity c){
        //Use a FileProvider so we can send this file to other apps
        final Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".exportProvider", file);

        final Intent intent = createShareIntent(c, uri ,mimeType);
        grantUriPermission(c, intent, uri);

        //Create intents for apps that support viewing or editing the file
        final Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(uri, mimeType);
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        List<ResolveInfo> resInfoList = c.getPackageManager().queryIntentActivities(viewIntent, 0);
        Vector<Intent> extraIntents = new Vector<>();
        for (ResolveInfo ri : resInfoList) {
            if (ri.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID))
                continue;
            Intent appIntent = new Intent();
            appIntent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            appIntent.setAction(Intent.ACTION_VIEW);
            appIntent.setDataAndType(uri, mimeType);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            extraIntents.add(appIntent);
            c.grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        final Intent[] extraIntentsArray = extraIntents.toArray(new Intent[extraIntents.size()]);

        //Create chooser
        Intent chooser = Intent.createChooser(intent, c.getString(R.string.share_pick_share));
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntentsArray);
        //And finally grant permissions again for any activities created by the chooser
        grantUriPermission(c, chooser, uri);

        //Execute this intent
        c.startActivity(chooser);
    }

    public static void startPhyphoxFileSharing(Activity a, File file){

        final Uri uri = FileProvider.getUriForFile(a.getBaseContext(), a.getPackageName() + ".exportProvider", file);

        final Intent intent = createShareIntent(a, uri , MIME_TYPE_PHYPHOX);
        grantUriPermission(a, intent, uri);

        //Create chooser
        Intent chooser = Intent.createChooser(intent, a.getString(R.string.share_pick_share));
        //And finally grant permissions again for any activities created by the chooser
        grantUriPermission(a, chooser, uri);
        //Execute this intent
        a.startActivity(chooser);
    }

    private static void grantUriPermission(Activity activity, Intent intent, Uri uri){
        List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo ri : resInfoList) {
            String packageName = ri.activityInfo.packageName;
            if (packageName.equals(BuildConfig.APPLICATION_ID)) continue;
            activity.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private static Intent createShareIntent(Activity c, Uri uri, String mimeType){
        return ShareCompat.IntentBuilder.from(c)
                .setType(mimeType) //mime type from the export filter
                .setSubject(c.getString(R.string.export_subject))
                .setStream(uri)
                .getIntent()
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_GRANT_READ_URI_PERMISSION);

    }

}
