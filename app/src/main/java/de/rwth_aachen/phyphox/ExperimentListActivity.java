package de.rwth_aachen.phyphox;

import static android.content.Context.SENSOR_SERVICE;
import static de.rwth_aachen.phyphox.ExperimentItemAdapter.EXPERIMENT_ISASSET;
import static de.rwth_aachen.phyphox.ExperimentItemAdapter.EXPERIMENT_ISTEMP;
import static de.rwth_aachen.phyphox.ExperimentItemAdapter.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS;
import static de.rwth_aachen.phyphox.ExperimentsInCategory.phyphoxCat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.collection.ArraySet;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.io.FileUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothExperimentLoader;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;

//ExperimentList implements the activity which lists all experiments to the user. This is the start
//activity for this app if it is launched without an intent.


public class ExperimentListActivity extends AppCompatActivity {

    static class ExperimentShortInfo {
        RGB color;
        Drawable icon;
        String title;
        String description;
        String fullDescription;
        Set<String> resources;
        String xmlFile;
        String isTemp;
        boolean isAsset;
        int unavailableSensor;
        String isLink;
        public Map<String, String> links;
        String categoryName;
    }

    //Strings which define extra information for intents starting an experiment from local files


    //String constant to identify our preferences
    public static final String PREFS_NAME = "phyphox";

    //Name of support category
    static final String phyphoxCatHintRelease = "1.1.12"; //Change this to reactivate the phyphox support category hint on the next update. We set it to the version in which it is supposed to be re-enabled, so we can easily understand its meaning.

    //A resource reference for easy access
    private Resources res;

    ProgressDialog progress = null;

    BluetoothExperimentLoader bluetoothExperimentLoader = null;
    long currentQRcrc32 = -1;
    int currentQRsize = -1;
    byte[][] currentQRdataPackets = null;

    boolean newExperimentDialogOpen = false;

    //private Vector<ExperimentsInCategory> categories = new Vector<>(); //The list of categories. The ExperimentsInCategory class (see below) holds a ExperimentsInCategory and all its experiment items
    //private HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    //private HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>(); //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices

    PopupWindow popupWindow = null;

    private ExperimentRepository experimentRepository;

    @SuppressLint("ClickableViewAccessibility")
    private void showSupportHint() {
        if (popupWindow != null)
            return;
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View hintView = inflater.inflate(R.layout.support_phyphox_hint, null);
        TextView text = hintView.findViewById(R.id.support_phyphox_hint_text);
        text.setText(res.getString(R.string.categoryPhyphoxOrgHint));
        ImageView iv = hintView.findViewById(R.id.support_phyphox_hint_arrow);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)iv.getLayoutParams();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(lp);

        popupWindow = new PopupWindow(hintView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if(Build.VERSION.SDK_INT >= 21){
            popupWindow.setElevation(4.0f);
        }

        popupWindow.setOutsideTouchable(false);
        popupWindow.setTouchable(false);
        popupWindow.setFocusable(false);
        LinearLayout ll = hintView.findViewById(R.id.support_phyphox_hint_root);

        ll.setOnTouchListener((view, motionEvent) -> {
            if (popupWindow != null)
                popupWindow.dismiss();
            return true;
        });

        popupWindow.setOnDismissListener(() -> popupWindow = null);

        final View root = findViewById(R.id.rootExperimentList);
        root.post(() -> {
            try {
                popupWindow.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            } catch (WindowManager.BadTokenException e) {
                Log.e("showHint", "Bad token when showing hint. This is not unusual when app is rotating while showing the hint.");
            }
        });
    }

    @Override
    public void onUserInteraction() {
        if (popupWindow != null)
            popupWindow.dismiss();
    }

    private void showSupportHintIfRequired() {
        try {
            if (!getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).versionName.split("-")[0].equals(phyphoxCatHintRelease))
                return;
        } catch (Exception e) {
            return;
        }

        final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String lastSupportHint = settings.getString("lastSupportHint", "");
        if (lastSupportHint.equals(phyphoxCatHintRelease)) {
            return;
        }

        showSupportHint();
        final boolean disabled = false;

        ReportingScrollView sv = ((ReportingScrollView)findViewById(R.id.experimentScroller));
        sv.setOnScrollChangedListener(new ReportingScrollView.OnScrollChangedListener() {
            @Override
            public void onScrollChanged(ReportingScrollView scrollView, int x, int y, int oldx, int oldy) {
                int bottom = scrollView.getChildAt(scrollView.getChildCount()-1).getBottom();
                if (y + 10 > bottom - scrollView.getHeight()) {
                    scrollView.setOnScrollChangedListener(null);
                    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("lastSupportHint", phyphoxCatHintRelease);
                    editor.apply();
                }
            }
        });
    }



    //Load all experiments from assets and from local files


    @Override
    //If we return to this activity we want to reload the experiment list as other activities may
    //have changed it
    protected void onResume() {
        super.onResume();
        experimentRepository.loadExperimentList();
    }


    void showError(String error) {
        if (progress != null)
            progress.dismiss();
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    public void zipReady(String result, BluetoothDevice preselectedDevice) {
        if (progress != null)
            progress.dismiss();
        if (result.isEmpty()) {
            File tempPath = new File(getFilesDir(), "temp_zip");
            String[] extensions = {"phyphox"};
            final Collection<File> files = FileUtils.listFiles(tempPath, extensions, true);
            if (files.size() == 0) {
                Toast.makeText(this, "Error: There is no valid phyphox experiment in this zip file.", Toast.LENGTH_LONG).show();
            } else if (files.size() == 1) {
                //Create an intent for this file
                Intent intent = new Intent(this, Experiment.class);
                intent.setData(Uri.fromFile(files.iterator().next()));
                if (preselectedDevice != null)
                    intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, preselectedDevice.getAddress());
                intent.putExtra(EXPERIMENT_ISTEMP, "temp_zip");
                intent.setAction(Intent.ACTION_VIEW);

                //Open the file
                startActivity(intent);
            } else {

                //Vector<ExperimentsInCategory> zipExperiments = new Vector<>();

                //Load experiments from local files
                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, tempPath.toURI().relativize(file.toURI()).getPath(), "temp_zip", false);
                        ExperimentShortInfo shortInfo = experimentRepository.getAssetExperimentLoader().loadExperimentsShortInfo(data);
                        if (shortInfo != null){
                            experimentRepository.getAssetExperimentLoader().
                                    addExperiment(shortInfo, shortInfo.categoryName);
                        }
                        //loadExperimentInfo(input, tempPath.toURI().relativize(file.toURI()).getPath(), "temp_zip", false, zipExperiments, null, null);
                        input.close();
                    } catch (IOException e) {
                        Log.e("zip", e.getMessage());
                        Toast.makeText(this, "Error: Could not load experiment \"" + file + "\" from zip file.", Toast.LENGTH_LONG).show();
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.open_multipe_dialog, null);
                final Activity parent = this;
                builder.setView(view)
                        .setPositiveButton(R.string.open_save_all, (dialog, id) -> {
                            /**
                             for (ExperimentsInCategory experimentCats : zipExperiments) {
                             for (ExperimentShortInfo experimentShortInfo : experimentCats.experimentItemAdapter.experimentShortInfos) {
                             File file = new File(tempPath, experimentShortInfo.xmlFile);
                             long crc32 = Helper.getCRC32(file);
                             if (!Helper.experimentInCollection(crc32, parent)) {
                             file.renameTo(new File(getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                             if (!experimentShortInfo.resources.isEmpty()) {
                             File resFolder = new File(tempPath, "res");
                             File targetFolder = new File(getFilesDir(), Long.toHexString(crc32).toLowerCase());
                             targetFolder.mkdirs();
                             for (String src : experimentShortInfo.resources) {
                             File srcFile = new File(resFolder, src);
                             File dstFile = new File(targetFolder, src);
                             try {
                             Helper.copyFile(srcFile, dstFile);
                             } catch (Exception e) {
                             Toast.makeText(ExperimentList.this, "Error while copying " + srcFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                             }
                             }
                             }
                             }
                             }
                             }
                             */
                            experimentRepository.loadExperimentList();
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
                AlertDialog dialog = builder.create();

                ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

                dialog.setTitle(getResources().getString(R.string.open_zip_title));

                experimentRepository.addExperimentCategoryToParent();

                /**
                 for (ExperimentsInCategory cat : zipExperiments) {
                 if (preselectedDevice != null)
                 cat.setPreselectedBluetoothAddress(preselectedDevice.getAddress());
                 cat.addToParent(true);
                 }
                 */

                dialog.show();
            }
        } else {
            Toast.makeText(ExperimentListActivity.this, result, Toast.LENGTH_LONG).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void loadExperimentFromBluetoothDevice(final BluetoothDevice device) {
        final ExperimentListActivity parent = this;
        if (bluetoothExperimentLoader == null) {
            bluetoothExperimentLoader = new BluetoothExperimentLoader(getBaseContext(), new BluetoothExperimentLoader.BluetoothExperimentLoaderCallback() {
                @Override
                public void updateProgress(int transferred, int total) {

                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if (total > 0) {
                                if (progress.isIndeterminate()) {
                                    progress.dismiss();
                                    progress = new ProgressDialog(parent);
                                    progress.setTitle(res.getString(R.string.loadingTitle));
                                    progress.setMessage(res.getString(R.string.loadingText));
                                    progress.setIndeterminate(false);
                                    progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                    progress.setCancelable(true);
                                    progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                        @Override
                                        public void onCancel(DialogInterface dialogInterface) {
                                            if (bluetoothExperimentLoader != null)
                                                bluetoothExperimentLoader.cancel();
                                        }
                                    });
                                    progress.setProgress(transferred);
                                    progress.setMax(total);
                                    progress.show();
                                } else {
                                    progress.setProgress(transferred);
                                }
                            }
                        }
                    });
                }

                @Override
                public void dismiss() {
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                        }
                    });
                }

                @Override
                public void error(String msg) {
                    dismiss();
                    showBluetoothExperimentReadError(msg, device);
                }

                @Override
                public void success(Uri experimentUri, boolean isZip) {
                    dismiss();
                    Intent intent = new Intent(parent, Experiment.class);
                    intent.setData(experimentUri);
                    intent.setAction(Intent.ACTION_VIEW);
                    if (isZip) {
                        new HandleZipIntent(intent, parent, device).execute();
                    } else {
                        intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, device.getAddress());
                        startActivity(intent);
                    }
                }
            });
        }
        progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true, true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if (bluetoothExperimentLoader != null)
                    bluetoothExperimentLoader.cancel();
            }
        });
        bluetoothExperimentLoader.loadExperimentFromBluetoothDevice(device);
    }

    protected void handleIntent(Intent intent) {


        if (progress != null)
            progress.dismiss();

        String scheme = intent.getScheme();
        if (scheme == null)
            return;
        boolean isZip = false;
        if (scheme.equals(ContentResolver.SCHEME_FILE)) {
            if (scheme.equals(ContentResolver.SCHEME_FILE) && !intent.getData().getPath().startsWith(getFilesDir().getPath()) && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Android 6.0: No permission? Request it!
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                //We will stop here. If the user grants the permission, the permission callback will restart the action with the same intent
                return;
            }
            Uri uri = intent.getData();

            byte[] data = new byte[4];
            InputStream is;
            try {
                is = this.getContentResolver().openInputStream(uri);
                if (is.read(data, 0, 4) < 4) {
                    Toast.makeText(this, "Error: File truncated.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (FileNotFoundException e) {
                Toast.makeText(this, "Error: File not found.", Toast.LENGTH_LONG).show();
                return;
            } catch (IOException e) {
                Toast.makeText(this, "Error: IOException.", Toast.LENGTH_LONG).show();
                return;
            }

            isZip = (data[0] == 0x50 && data[1] == 0x4b && data[2] == 0x03 && data[3] == 0x04);

            if (!isZip) {
                //This is just a single experiment - Start the Experiment activity and let it handle the intent
                Intent forwardedIntent = new Intent(intent);
                forwardedIntent.setClass(this, Experiment.class);
                this.startActivity(forwardedIntent);
            } else {
                //We got a zip-file. Let's see what's inside...
                progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
                new HandleZipIntent(intent, this).execute();
            }
        } else if (scheme.equals(ContentResolver.SCHEME_CONTENT) || scheme.equals("phyphox") || scheme.equals("http") || scheme.equals("https")) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new HandleCopyIntent(intent, this).execute();
        }
    }

    protected void showNewExperimentDialog() {
        newExperimentDialogOpen = true;
        final FloatingActionButton newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        final FloatingActionButton newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        final FloatingActionButton newExperimentBluetooth= (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        final View backgroundDimmer = (View) findViewById(R.id.experimentListDimmer);

        Animation rotate45In = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_rotate45);
        Animation fabIn = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_in);
        Animation labelIn = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_label_in);
        Animation fadeDark = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fade_dark);

        newExperimentButton.startAnimation(rotate45In);
        newExperimentSimple.startAnimation(fabIn);
        newExperimentSimpleLabel.startAnimation(labelIn);
        newExperimentBluetooth.startAnimation(fabIn);
        newExperimentBluetoothLabel.startAnimation(labelIn);
        newExperimentQR.startAnimation(fabIn);
        newExperimentQRLabel.startAnimation(labelIn);
        backgroundDimmer.startAnimation(fadeDark);

        newExperimentSimple.setClickable(true);
        newExperimentSimpleLabel.setClickable(true);
        newExperimentBluetooth.setClickable(true);
        newExperimentBluetoothLabel.setClickable(true);
        newExperimentQR.setClickable(true);
        newExperimentQRLabel.setClickable(true);
        backgroundDimmer.setClickable(true);
    }

    protected void hideNewExperimentDialog() {
        newExperimentDialogOpen = false;
        final FloatingActionButton newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        final FloatingActionButton newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        final FloatingActionButton newExperimentBluetooth= (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        final View backgroundDimmer = (View) findViewById(R.id.experimentListDimmer);

        Animation rotate0In = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_rotate0);
        Animation fabOut = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_out);
        Animation labelOut = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_label_out);
        Animation fadeTransparent = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fade_transparent);

        newExperimentSimple.setClickable(false);
        newExperimentSimpleLabel.setClickable(false);
        newExperimentBluetooth.setClickable(false);
        newExperimentBluetoothLabel.setClickable(false);
        newExperimentQR.setClickable(false);
        newExperimentQRLabel.setClickable(false);
        backgroundDimmer.setClickable(false);

        newExperimentButton.startAnimation(rotate0In);
        newExperimentSimple.startAnimation(fabOut);
        newExperimentSimpleLabel.startAnimation(labelOut);
        newExperimentBluetooth.startAnimation(fabOut);
        newExperimentBluetoothLabel.startAnimation(labelOut);
        newExperimentQR.startAnimation(fabOut);
        newExperimentQRLabel.startAnimation(labelOut);
        backgroundDimmer.startAnimation(fadeTransparent);

    }

    protected void scanQRCode() {
        IntentIntegrator qrScan = new IntentIntegrator(this);

        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        qrScan.setPrompt(getResources().getString(R.string.newExperimentQRscan));
        qrScan.setBeepEnabled(false);
        qrScan.setOrientationLocked(true);

        qrScan.initiateScan();
    }

    @Override
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.recreate();
        }
    }

    protected void showQRScanError(String msg, Boolean isError) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentQRErrorTitle : R.string.newExperimentQR)
                .setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        scanQRCode();
                    }
                })
                .setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void showBluetoothExperimentReadError(String msg, final BluetoothDevice device) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(R.string.newExperimentBTReadErrorTitle)
                .setPositiveButton(R.string.tryagain, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        loadExperimentFromBluetoothDevice(device);
                    }
                })
                .setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        String textResult;
        if (scanResult != null && (textResult = scanResult.getContents()) != null) {
            if (textResult.toLowerCase().startsWith("http://") || textResult.toLowerCase().startsWith("https://") || textResult.toLowerCase().startsWith("phyphox://")) {
                //This is an URL, open it
                //Create an intent for this new file
                Intent URLintent = new Intent(this, Experiment.class);
                URLintent.setData(Uri.parse("phyphox://" + textResult.split("//", 2)[1]));
                URLintent.setAction(Intent.ACTION_VIEW);
                handleIntent(URLintent);

            } else if (textResult.startsWith("phyphox")) {
                //The QR code contains the experiment itself. The first 13 bytes are:
                // p h y p h o x [crc32] [i] [n]
                //"phyphox" as string (7 bytes)
                //crc32 hash (big endian) of the submitted experiment (has to be the same for each qr code if the experiment is spread across multiple codes)
                //i is the index of this code in a sequence of n code (starting at zero, so i starts at 0 and end with n-1
                //n is the total number of codes for this experiment
                byte[] data = intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_0");
                if (data == null) {
                    Toast.makeText(ExperimentListActivity.this, "Unexpected error: Could not retrieve data from QR code.", Toast.LENGTH_LONG).show();
                    return;
                }
                long crc32 = (((long)(data[7] & 0xff) << 24) | ((long)(data[8] & 0xff) << 16) | ((long)(data[9] & 0xff) << 8) | ((long)(data[10] & 0xff)));
                int index = data[11];
                int count = data[12];

                if ((currentQRcrc32 >= 0 && currentQRcrc32 != crc32) || (currentQRsize >= 0 && count != currentQRsize) || (currentQRsize >= 0 && index >= currentQRsize)) {
                    showQRScanError(res.getString(R.string.newExperimentQRcrcMismatch), true);
                    currentQRsize = -1;
                    currentQRcrc32 = -1;
                }
                if (currentQRcrc32 < 0) {
                    currentQRcrc32 = crc32;
                    currentQRsize = count;
                    currentQRdataPackets = new byte[count][];
                }
                currentQRdataPackets[index] = Arrays.copyOfRange(data, 13, data.length);
                int missing = 0;
                for (int i = 0; i < currentQRsize; i++) {
                    if (currentQRdataPackets[i] == null)
                        missing++;
                }
                if (missing == 0) {
                    //We have all the data. Write it to a temporary file and give it to our default intent handler...
                    File tempPath = new File(getFilesDir(), "temp_qr");
                    if (!tempPath.exists()) {
                        if (!tempPath.mkdirs()) {
                            showQRScanError("Could not create temporary directory to write zip file.", true);
                            return;
                        }
                    }
                    String[] files = tempPath.list();
                    for (String file : files) {
                        if (!(new File(tempPath, file).delete())) {
                            showQRScanError("Could not clear temporary directory to extract zip file.", true);
                            return;
                        }
                    }

                    int totalSize = 0;

                    for (int i = 0; i < currentQRsize; i++) {
                        totalSize += currentQRdataPackets[i].length;
                    }
                    byte [] dataReceived = new byte[totalSize];
                    int offset = 0;
                    for (int i = 0; i < currentQRsize; i++) {
                        System.arraycopy(currentQRdataPackets[i], 0, dataReceived, offset, currentQRdataPackets[i].length);
                        offset += currentQRdataPackets[i].length;
                    }

                    CRC32 crc32Received = new CRC32();
                    crc32Received.update(dataReceived);
                    if (crc32Received.getValue() != crc32) {
                        Log.e("qrscan", "Received CRC32 " + crc32Received.getValue() + " but expected " + crc32);
                        showQRScanError(res.getString(R.string.newExperimentQRBadCRC), true);
                        return;
                    }

                    byte [] zipData = Helper.inflatePartialZip(dataReceived);

                    File zipFile;
                    try {
                        zipFile = new File(tempPath, "qr.zip");
                        FileOutputStream out = new FileOutputStream(zipFile);
                        out.write(zipData);
                        out.close();
                    } catch (Exception e) {
                        showQRScanError("Could not write QR content to zip file.", true);
                        return;
                    }

                    currentQRsize = -1;
                    currentQRcrc32 = -1;

                    Intent zipIntent = new Intent(this, Experiment.class);
                    zipIntent.setData(Uri.fromFile(zipFile));
                    zipIntent.setAction(Intent.ACTION_VIEW);
                    new HandleZipIntent(zipIntent, this).execute();
                } else {
                    showQRScanError(res.getString(R.string.newExperimentQRCodesMissing1) + " " + currentQRsize + " " + res.getString(R.string.newExperimentQRCodesMissing2) + " " + missing, false);
                }
            } else {
                //QR code does not contain or reference a phyphox experiment
                showQRScanError(res.getString(R.string.newExperimentQRNoExperiment), true);
            }
        }
    }

    @Override
    //The onCreate block will setup some onClickListeners and display a do-not-damage-your-phone
    //  warning message.
    protected void onCreate(Bundle savedInstanceState) {

        //Switch from the theme used as splash screen to the theme for the activity
        //This method is for pre Android 12 devices: We set a theme that shows the splash screen and
        //on create is executed when all resources are loaded, which then replaces the theme with
        //the normal one.
        //On Android 12 this does not hurt, but Android 12 shows its own splash method (defined with
        //specific attributes in the theme), so the classic splash screen is not shown anyways
        //before setTheme is called and we see the normal theme right away.
        setTheme(R.style.Theme_Phyphox_DayNight);

        String themePreference = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(getString(R.string.setting_dark_mode_key), SettingsFragment.DARK_MODE_ON);
        SettingsFragment.setApplicationTheme(themePreference);

        //Basics. Call super-constructor and inflate the layout.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_list);

        res = getResources(); //Get Resource reference for easy access.

        if (!displayDoNotDamageYourPhone()) { //Show the do-not-damage-your-phone-warning
            showSupportHintIfRequired();
        }

        Activity parentActivity = this;


        Helper.setWindowInsetListenerForSystemBar(findViewById(R.id.expListHeader));

        //Set the on-click-listener for the credits
        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Context wrapper = new ContextThemeWrapper(ExperimentListActivity.this, R.style.Theme_Phyphox_DayNight);
                PopupMenu popup = new PopupMenu(wrapper, v);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_privacy) {
                            Uri uri = Uri.parse(res.getString(R.string.privacyPolicyURL));
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            if (intent.resolveActivity(getPackageManager()) != null) {
                                startActivity(intent);
                            }
                            return true;
                        } else if (item.getItemId() == R.id.action_credits) {
                            //Create the credits as an AlertDialog
                            ContextThemeWrapper ctw = new ContextThemeWrapper(ExperimentListActivity.this, R.style.rwth);
                            AlertDialog.Builder credits = new AlertDialog.Builder(ctw);
                            LayoutInflater creditsInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
                            View creditLayout = creditsInflater.inflate(R.layout.credits, null);

                            //Set the credit texts, which require HTML markup
                            TextView tv = (TextView) creditLayout.findViewById(R.id.creditNames);

                            SpannableStringBuilder creditsNamesSpannable = new SpannableStringBuilder();
                            boolean first = true;
                            for (String line : res.getString(R.string.creditsNames).split("\\n")) {
                                if (first)
                                    first = false;
                                else
                                    creditsNamesSpannable.append("\n");
                                creditsNamesSpannable.append(line.trim());
                            }
                            Matcher matcher = Pattern.compile("^.*:$", Pattern.MULTILINE).matcher(creditsNamesSpannable);
                            while (matcher.find()) {
                                creditsNamesSpannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            }
                            tv.setText(creditsNamesSpannable);
                            TextView tvA = (TextView) creditLayout.findViewById(R.id.creditsApache);
                            tvA.setText(Html.fromHtml(res.getString(R.string.creditsApache)));
                            TextView tvB = (TextView) creditLayout.findViewById(R.id.creditsZxing);
                            tvB.setText(Html.fromHtml(res.getString(R.string.creditsZxing)));
                            TextView tvC = (TextView) creditLayout.findViewById(R.id.creditsPahoMQTT);
                            tvC.setText(Html.fromHtml(res.getString(R.string.creditsPahoMQTT)));

                            //Finish alertDialog builder
                            credits.setView(creditLayout);
                            credits.setPositiveButton(res.getText(R.string.close), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    //Nothing to do. Just close the thing.
                                }
                            });

                            //Present the dialog
                            credits.show();
                            return true;
                        } else if (item.getItemId() == R.id.action_helpExperiments) {
                                Uri uri = Uri.parse(res.getString(R.string.experimentsPhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            return true;
                        } else if (item.getItemId() == R.id.action_helpFAQ) {
                                Uri uri = Uri.parse(res.getString(R.string.faqPhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            return true;
                        } else if (item.getItemId() == R.id.action_helpRemote) {
                                Uri uri = Uri.parse(res.getString(R.string.remotePhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            return true;
                        } else if (item.getItemId() == R.id.action_settings) {
                                Intent intent = new Intent(parentActivity, Settings.class);
                                startActivity(intent);
                                return true;
                            } else if (item.getItemId() == R.id.action_deviceInfo) {
                            StringBuilder sb = new StringBuilder();

                            PackageInfo pInfo;
                            try {
                                pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
                            } catch (Exception e) {
                                pInfo = null;
                            }

                            if(Helper.isDarkTheme(res)){
                                sb.append(" <font color='white'");
                            }else{
                                sb.append(" <font color='black'");
                            }

                            sb.append("<b>phyphox</b><br />");
                            if (pInfo != null) {
                                sb.append("Version: ");
                                sb.append(pInfo.versionName);
                                sb.append("<br />");
                                sb.append("Build: ");
                                sb.append(pInfo.versionCode);
                                sb.append("<br />");
                            } else {
                                sb.append("Version: Unknown<br />");
                                sb.append("Build: Unknown<br />");
                            }
                            sb.append("File format: ");
                            sb.append(PhyphoxFile.phyphoxFileVersion);
                            sb.append("<br /><br />");

                            sb.append("<b>Permissions</b><br />");
                            if (pInfo != null && pInfo.requestedPermissions != null) {
                                for (int i = 0; i < pInfo.requestedPermissions.length; i++) {
                                    sb.append(pInfo.requestedPermissions[i].startsWith("android.permission.") ? pInfo.requestedPermissions[i].substring(19) : pInfo.requestedPermissions[i]);
                                    sb.append(": ");
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                                        sb.append((pInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0 ? "no" : "yes");
                                    else
                                        sb.append("API < 16");
                                    sb.append("<br />");
                                }
                            } else {
                                if (pInfo == null)
                                    sb.append("Unknown<br />");
                                else
                                    sb.append("None<br />");
                            }
                            sb.append("<br />");

                            sb.append("<b>Device</b><br />");
                            sb.append("Model: ");
                            sb.append(Build.MODEL);
                            sb.append("<br />");
                            sb.append("Brand: ");
                            sb.append(Build.BRAND);
                            sb.append("<br />");
                            sb.append("Board: ");
                            sb.append(Build.DEVICE);
                            sb.append("<br />");
                            sb.append("Manufacturer: ");
                            sb.append(Build.MANUFACTURER);
                            sb.append("<br />");
                            sb.append("ABIS: ");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
                                    if (i > 0)
                                        sb.append(", ");
                                    sb.append(Build.SUPPORTED_ABIS[i]);
                                }
                            } else {
                                sb.append("API < 21");
                            }
                            sb.append("<br />");
                            sb.append("Base OS: ");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                sb.append(Build.VERSION.BASE_OS);
                            } else {
                                sb.append("API < 23");
                            }
                            sb.append("<br />");
                            sb.append("Codename: ");
                            sb.append(Build.VERSION.CODENAME);
                            sb.append("<br />");
                            sb.append("Release: ");
                            sb.append(Build.VERSION.RELEASE);
                            sb.append("<br />");
                            sb.append("Patch: ");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                sb.append(Build.VERSION.SECURITY_PATCH);
                            } else {
                                sb.append("API < 23");
                            }
                            sb.append("<br /><br />");

                            sb.append("<b>Sensors</b><br /><br />");
                            SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                            if (sensorManager == null) {
                                sb.append("Unkown<br />");
                            } else {
                                for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                                    sb.append("<b>");
                                    sb.append(res.getString(SensorInput.getDescriptionRes(sensor.getType())));
                                    sb.append("</b> (type ");
                                    sb.append(sensor.getType());
                                    sb.append(")");
                                    sb.append("<br />");
                                    sb.append("- Name: ");
                                    sb.append(sensor.getName());
                                    sb.append("<br />");
                                    sb.append("- Range: ");
                                    sb.append(sensor.getMaximumRange());
                                    sb.append(" ");
                                    sb.append(SensorInput.getUnit(sensor.getType()));
                                    sb.append("<br />");
                                    sb.append("- Resolution: ");
                                    sb.append(sensor.getResolution());
                                    sb.append(" ");
                                    sb.append(SensorInput.getUnit(sensor.getType()));
                                    sb.append("<br />");
                                    sb.append("- Min delay: ");
                                    sb.append(sensor.getMinDelay());
                                    sb.append(" µs");
                                    sb.append("<br />");
                                    sb.append("- Max delay: ");
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        sb.append(sensor.getMaxDelay());
                                    } else {
                                        sb.append("API < 21");
                                    }
                                    sb.append(" µs");
                                    sb.append("<br />");
                                    sb.append("- Power: ");
                                    sb.append(sensor.getPower());
                                    sb.append(" mA");
                                    sb.append("<br />");
                                    sb.append("- Vendor: ");
                                    sb.append(sensor.getVendor());
                                    sb.append("<br />");
                                    sb.append("- Version: ");
                                    sb.append(sensor.getVersion());
                                    sb.append("<br /><br />");
                                }
                            }
                            sb.append("<br /><br />");

                            sb.append("<b>Cameras</b><br /><br />");
                            sb.append("<b>Depth sensors</b><br />");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                sb.append("- Depth sensors front: ");
                                int depthFront = DepthInput.countCameras(CameraCharacteristics.LENS_FACING_FRONT);
                                sb.append(depthFront);
                                sb.append("<br />");
                                sb.append("- Max resolution front: ");
                                sb.append(depthFront > 0 ? DepthInput.getMaxResolution(CameraCharacteristics.LENS_FACING_FRONT) : "-");
                                sb.append("<br />");
                                sb.append("- Max frame rate front: ");
                                sb.append(depthFront > 0 ? DepthInput.getMaxRate(CameraCharacteristics.LENS_FACING_FRONT) : "-");
                                sb.append("<br />");
                                sb.append("- Depth sensors back: ");
                                int depthBack = DepthInput.countCameras(CameraCharacteristics.LENS_FACING_FRONT);
                                sb.append(depthBack);
                                sb.append("<br />");
                                sb.append("- Max resolution back: ");
                                sb.append(depthBack > 0 ? DepthInput.getMaxResolution(CameraCharacteristics.LENS_FACING_BACK) : "-");
                                sb.append("<br />");
                                sb.append("- Max frame rate back: ");
                                sb.append(depthBack > 0 ? DepthInput.getMaxRate(CameraCharacteristics.LENS_FACING_BACK) : "-");
                                sb.append("<br />");
                            } else {
                                sb.append("API < 23");
                            }
                            sb.append("<br /><br />");

                            sb.append("<b>Camera 2 API</b><br />");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                sb.append(CameraHelper.getCamera2FormattedCaps(false));
                            } else {
                                sb.append("API < 21");
                            }
                            sb.append("</font>");

                            final Spanned text = Html.fromHtml(sb.toString());
                            ContextThemeWrapper ctw = new ContextThemeWrapper( ExperimentListActivity.this, R.style.Theme_Phyphox_DayNight);
                            AlertDialog.Builder builder = new AlertDialog.Builder(ctw);
                            builder.setMessage(text)
                                    .setTitle(R.string.deviceInfo)
                                    .setPositiveButton(R.string.copyToClipboard, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Copy the device info to the clipboard and notify the user

                                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                            ClipData data = ClipData.newPlainText(res.getString(R.string.deviceInfo), text);
                                            cm.setPrimaryClip(data);

                                            Toast.makeText(ExperimentListActivity.this, res.getString(R.string.deviceInfoCopied), Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Closed by user. Nothing to do.
                                        }
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
                popup.inflate(R.menu.menu_help);
                popup.show();

            }
        };
        ImageView creditsV = (ImageView) findViewById(R.id.credits);
        creditsV.setOnClickListener(ocl);

        //Setup the on-click-listener for the create-new-experiment button
        final ExperimentListActivity thisRef = this; //Context needs to be accessed in the onClickListener

        Button.OnClickListener neocl = v -> {
            if (newExperimentDialogOpen)
                hideNewExperimentDialog();
            else
                showNewExperimentDialog();
        };

        final FloatingActionButton newExperimentButton = findViewById(R.id.newExperiment);
        final View experimentListDimmer =  findViewById(R.id.experimentListDimmer);
        newExperimentButton.setOnClickListener(neocl);
        experimentListDimmer.setOnClickListener(neocl);

        Button.OnClickListener neoclSimple = v -> {
            hideNewExperimentDialog();
            newExperimentDialog(thisRef);
        };

        final FloatingActionButton newExperimentSimple = findViewById(R.id.newExperimentSimple);
        final TextView newExperimentSimpleLabel = findViewById(R.id.newExperimentSimpleLabel);
        newExperimentSimple.setOnClickListener(neoclSimple);
        newExperimentSimpleLabel.setOnClickListener(neoclSimple);

        Button.OnClickListener neoclBluetooth = v -> {
            hideNewExperimentDialog();
            //(new RunBluetoothScan(thisRef)).execute();

            Set<String> bluetoothNameKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceNameList().keySet();
            Set<UUID> bluetoothUUIDKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceUUIDList().keySet();

            new BluetoothScanner(parentActivity,bluetoothNameKeySet, bluetoothUUIDKeySet, new BluetoothScanner.BluetoothScanListener() {
                @Override
                public void onBluetoothDeviceFound(BluetoothScanDialog.BluetoothDeviceInfo result) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        openBluetoothExperiments(result.device, result.uuids, result.phyphoxService);
                    }
                }

                @Override
                public void onBluetoothScanError(String msg, Boolean isError, Boolean isFatal) {
                    showBluetoothScanError(res.getString(R.string.bt_android_version), true, true);

                }
            }).execute();
        };

        final FloatingActionButton newExperimentBluetooth = (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        newExperimentBluetooth.setOnClickListener(neoclBluetooth);
        newExperimentBluetoothLabel.setOnClickListener(neoclBluetooth);

        Button.OnClickListener neoclQR = v -> {
            hideNewExperimentDialog();
            scanQRCode();
        };

        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        newExperimentQR.setOnClickListener(neoclQR);
        newExperimentQRLabel.setOnClickListener(neoclQR);

        ExperimentListEnvironment environment = new ExperimentListEnvironment(getAssets(), getResources(), parentActivity.getApplicationContext(), parentActivity );

        experimentRepository = new ExperimentRepository(environment);

        handleIntent(getIntent());

    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission") //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        final HashMap<String, Vector<String>> mBluetoothDeviceNameList = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceNameList();
        final HashMap<UUID, Vector<String>> mBluetoothDeviceUUIDList = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceUUIDList();

        final ExperimentListActivity parent = this;
        Set<String> experiments = new HashSet<>();
        if (device.getName() != null) {
            for (String name :mBluetoothDeviceNameList.keySet()) {
                if (device.getName().contains(name)){
                    Vector<String> experimentsForName = mBluetoothDeviceNameList.get(name);
                    if (experimentsForName != null)
                        experiments.addAll(experimentsForName);
                }
            }
        }

        for (UUID uuid : uuids) {
            Vector<String> experimentsForUUID = mBluetoothDeviceUUIDList.get(uuid);
            if (experimentsForUUID != null)
                experiments.addAll(experimentsForUUID);
        }
        final Set<String> supportedExperiments = experiments;

        if (supportedExperiments.isEmpty() && phyphoxService) {
            //We do not have any experiments for this device, so there is no choice. Just load the experiment provided by the device.
            loadExperimentFromBluetoothDevice(device);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.open_multipe_dialog, null);
        builder.setView(view);

        if (!supportedExperiments.isEmpty()) {
            builder.setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AssetManager assetManager = parent.getAssets();
                    try {
                        for (String file : supportedExperiments) {
                            InputStream in = assetManager.open("experiments/bluetooth/" + file);
                            OutputStream out = new FileOutputStream(new File(parent.getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                            byte[] buffer = new byte[1024];
                            int count;
                            while ((count = in.read(buffer)) != -1) {
                                out.write(buffer, 0, count);
                            }
                            in.close();
                            out.flush();
                            out.close();
                        }
                    } catch (Exception e) {
                        Toast.makeText(parent, "Error: Could not retrieve assets.", Toast.LENGTH_LONG).show();
                    }
                    experimentRepository.loadExperimentList();
                    dialog.dismiss();
                }

            });
        }
        builder.setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());

        String instructions = "";
        if (!supportedExperiments.isEmpty()) {
            instructions += res.getString(R.string.open_bluetooth_assets);
        }
        if (!supportedExperiments.isEmpty() && phyphoxService)
            instructions += "\n\n";
        if (phyphoxService) {
            instructions += res.getString(R.string.newExperimentBluetoothLoadFromDeviceInfo);
            builder.setNeutralButton(R.string.newExperimentBluetoothLoadFromDevice, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    loadExperimentFromBluetoothDevice(device);
                    dialog.dismiss();
                }
            });
        }
        AlertDialog dialog = builder.create();

        ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(instructions);

        dialog.setTitle(parent.getResources().getString(R.string.open_bluetooth_assets_title));

        Vector<ExperimentsInCategory> bluetoothExperiments = new Vector<>();
        AssetManager assetManager = parent.getAssets();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/bluetooth/"+file);
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, "bluetooth/"+file, "bluetooth", true);
                ExperimentListActivity.ExperimentShortInfo shortInfo = experimentRepository.getAssetExperimentLoader().loadExperimentsShortInfo(data);
                if (shortInfo != null){
                    experimentRepository.getAssetExperimentLoader().addExperiment(shortInfo, shortInfo.categoryName);
                }
                input.close();
            } catch (IOException e) {
                Log.e("bluetooth", e.getMessage());
                Toast.makeText(parent, "Error: Could not load experiment \"" + file + "\" from asset.", Toast.LENGTH_LONG).show();
            }
        }

        Collections.sort(bluetoothExperiments, new CategoryComparator(res));
        experimentRepository.getAssetExperimentLoader().setCategories(bluetoothExperiments);

        LinearLayout parentLayout = view.findViewById(R.id.open_multiple_dialog_list);
        parentLayout.removeAllViews();

        for (ExperimentsInCategory cat : bluetoothExperiments) {
            cat.setPreselectedBluetoothAddress(device.getAddress());
            cat.addToParent(parentLayout);
        }
        dialog.show();
    }

    protected void showBluetoothScanError(String msg, Boolean isError, Boolean isFatal) {
        final ExperimentListActivity parent = this;

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentBluetoothErrorTitle : R.string.newExperimentBluetooth);
        if (!isFatal) {
            builder.setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    //(new RunBluetoothScan()).execute();
                    Set<String> bluetoothNameKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceNameList().keySet();
                    Set<UUID> bluetoothUUIDKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceUUIDList().keySet();

                    new BluetoothScanner(parent,bluetoothNameKeySet, bluetoothUUIDKeySet, new BluetoothScanner.BluetoothScanListener() {
                        @Override
                        public void onBluetoothDeviceFound(BluetoothScanDialog.BluetoothDeviceInfo result) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                openBluetoothExperiments(result.device, result.uuids, result.phyphoxService);
                            }
                        }

                        @Override
                        public void onBluetoothScanError(String msg, Boolean isError, Boolean isFatal) {
                            showBluetoothScanError(res.getString(R.string.bt_android_version), true, true);

                        }
                    }).execute();
                }
            });
        }
        builder.setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });
        runOnUiThread(() -> {
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }


    //Displays a warning message that some experiments might damage the phone
    private boolean displayDoNotDamageYourPhone() {
        //Use the app theme and create an AlertDialog-builder
        ContextThemeWrapper ctw = new ContextThemeWrapper( this, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
        LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View warningLayout = adbInflater.inflate(R.layout.donotshowagain, null);

        //This reference is used to address a do-not-show-again checkbox within the dialog
        final CheckBox dontShowAgain = (CheckBox) warningLayout.findViewById(R.id.donotshowagain);

        //Setup AlertDialog builder
        adb.setView(warningLayout);
        adb.setTitle(R.string.warning);
        adb.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //User clicked ok. Did the user decide to skip future warnings?
                Boolean skipWarning = false;
                if (dontShowAgain.isChecked())
                    skipWarning = true;

                //Store user decision
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("skipWarning", skipWarning);
                editor.apply();
            }
        });

        //Check preferences if the user does not want to see warnings
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        Boolean skipWarning = settings.getBoolean("skipWarning", false);
        if (!skipWarning) {
            adb.show(); //User did not decide to skip, so show it.
            return true;
        } else {
            return false;
        }

    }

    //This displays a rather complex dialog to allow users to set up a simple experiment
    private void newExperimentDialog(final Context c) {
        //Build the dialog with an AlertDialog builder...
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder neDialog = new AlertDialog.Builder(ctw);
        LayoutInflater neInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View neLayout = neInflater.inflate(R.layout.new_experiment, null);

        //Get a bunch of references to the dialog elements
        final EditText neTitle = (EditText) neLayout.findViewById(R.id.neTitle); //The edit box for the title of the new experiment
        final EditText neRate = (EditText) neLayout.findViewById(R.id.neRate); //Edit box for the aquisition rate

        //More references: Checkboxes for sensors
        final CheckBox neAccelerometer = (CheckBox) neLayout.findViewById(R.id.neAccelerometer);
        final CheckBox neGyroscope = (CheckBox) neLayout.findViewById(R.id.neGyroscope);
        final CheckBox neHumidity = (CheckBox) neLayout.findViewById(R.id.neHumidity);
        final CheckBox neLight = (CheckBox) neLayout.findViewById(R.id.neLight);
        final CheckBox neLinearAcceleration = (CheckBox) neLayout.findViewById(R.id.neLinearAcceleration);
        final CheckBox neLocation = (CheckBox) neLayout.findViewById(R.id.neLocation);
        final CheckBox neMagneticField = (CheckBox) neLayout.findViewById(R.id.neMagneticField);
        final CheckBox nePressure = (CheckBox) neLayout.findViewById(R.id.nePressure);
        final CheckBox neProximity = (CheckBox) neLayout.findViewById(R.id.neProximity);
        final CheckBox neTemperature = (CheckBox) neLayout.findViewById(R.id.neTemperature);

        //Setup the dialog builder...
        neRate.addTextChangedListener(new DecimalTextWatcher());
        neDialog.setView(neLayout);
        neDialog.setTitle(R.string.newExperiment);
        neDialog.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //Here we have to create the experiment definition file
                //This is a lot of tedious work....

                //Prepare the variables from user input

                String title = neTitle.getText().toString(); //Title of the new experiment

                //Prepare the rate
                double rate;
                try {
                    rate = Double.valueOf(neRate.getText().toString().replace(',', '.'));
                } catch (Exception e) {
                    rate = 0;
                    Toast.makeText(ExperimentListActivity.this, "Invaid sensor rate. Fall back to fastest rate.", Toast.LENGTH_LONG).show();
                }

                //Collect the enabled sensors
                boolean acc = neAccelerometer.isChecked();
                boolean gyr = neGyroscope.isChecked();
                boolean hum = neHumidity.isChecked();
                boolean light = neLight.isChecked();
                boolean lin = neLinearAcceleration.isChecked();
                boolean loc = neLocation.isChecked();
                boolean mag = neMagneticField.isChecked();
                boolean pressure = nePressure.isChecked();
                boolean prox = neProximity.isChecked();
                boolean temp = neTemperature.isChecked();
                if (!(acc || gyr || light || lin || loc || mag || pressure || prox || hum || temp)) {
                    acc = true;
                    Toast.makeText(ExperimentListActivity.this, "No sensor selected. Adding accelerometer as default.", Toast.LENGTH_LONG).show();
                }

                //Generate random file name
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";

                //Now write the whole file...
                try {
                    FileOutputStream output = c.openFileOutput(file, MODE_PRIVATE);
                    output.write("<phyphox version=\"1.14\">".getBytes());

                    //Title, standard category and standard description
                    output.write(("<title>"+title.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("&", "&amp;")+"</title>").getBytes());
                    output.write(("<category>"+res.getString(R.string.categoryNewExperiment)+"</category>").getBytes());
                    output.write(("<color>red</color>").getBytes());
                    output.write("<description>Get raw data from selected sensors.</description>".getBytes());

                    //Buffers for all sensors
                    output.write("<data-containers>".getBytes());
                    if (acc) {
                        output.write(("<container size=\"0\">acc_time</container>").getBytes());
                        output.write(("<container size=\"0\">accX</container>").getBytes());
                        output.write(("<container size=\"0\">accY</container>").getBytes());
                        output.write(("<container size=\"0\">accZ</container>").getBytes());
                    }
                    if (gyr) {
                        output.write(("<container size=\"0\">gyr_time</container>").getBytes());
                        output.write(("<container size=\"0\">gyrX</container>").getBytes());
                        output.write(("<container size=\"0\">gyrY</container>").getBytes());
                        output.write(("<container size=\"0\">gyrZ</container>").getBytes());
                    }
                    if (hum) {
                        output.write(("<container size=\"0\">hum_time</container>").getBytes());
                        output.write(("<container size=\"0\">hum</container>").getBytes());
                    }
                    if (light) {
                        output.write(("<container size=\"0\">light_time</container>").getBytes());
                        output.write(("<container size=\"0\">light</container>").getBytes());
                    }
                    if (lin) {
                        output.write(("<container size=\"0\">lin_time</container>").getBytes());
                        output.write(("<container size=\"0\">linX</container>").getBytes());
                        output.write(("<container size=\"0\">linY</container>").getBytes());
                        output.write(("<container size=\"0\">linZ</container>").getBytes());
                    }
                    if (loc) {
                        output.write(("<container size=\"0\">loc_time</container>").getBytes());
                        output.write(("<container size=\"0\">locLat</container>").getBytes());
                        output.write(("<container size=\"0\">locLon</container>").getBytes());
                        output.write(("<container size=\"0\">locZ</container>").getBytes());
                        output.write(("<container size=\"0\">locV</container>").getBytes());
                        output.write(("<container size=\"0\">locDir</container>").getBytes());
                        output.write(("<container size=\"0\">locAccuracy</container>").getBytes());
                        output.write(("<container size=\"0\">locZAccuracy</container>").getBytes());
                        output.write(("<container size=\"0\">locStatus</container>").getBytes());
                        output.write(("<container size=\"0\">locSatellites</container>").getBytes());
                    }
                    if (mag) {
                        output.write(("<container size=\"0\">mag_time</container>").getBytes());
                        output.write(("<container size=\"0\">magX</container>").getBytes());
                        output.write(("<container size=\"0\">magY</container>").getBytes());
                        output.write(("<container size=\"0\">magZ</container>").getBytes());
                    }
                    if (pressure) {
                        output.write(("<container size=\"0\">pressure_time</container>").getBytes());
                        output.write(("<container size=\"0\">pressure</container>").getBytes());
                    }
                    if (prox) {
                        output.write(("<container size=\"0\">prox_time</container>").getBytes());
                        output.write(("<container size=\"0\">prox</container>").getBytes());
                    }
                    if (temp) {
                        output.write(("<container size=\"0\">temp_time</container>").getBytes());
                        output.write(("<container size=\"0\">temp</container>").getBytes());
                    }
                    output.write("</data-containers>".getBytes());

                    //Inputs for each sensor
                    output.write("<input>".getBytes());
                    if (acc)
                        output.write(("<sensor type=\"accelerometer\" rate=\"" + rate + "\" ><output component=\"x\">accX</output><output component=\"y\">accY</output><output component=\"z\">accZ</output><output component=\"t\">acc_time</output></sensor>").getBytes());
                    if (gyr)
                        output.write(("<sensor type=\"gyroscope\" rate=\"" + rate + "\" ><output component=\"x\">gyrX</output><output component=\"y\">gyrY</output><output component=\"z\">gyrZ</output><output component=\"t\">gyr_time</output></sensor>").getBytes());
                    if (hum)
                        output.write(("<sensor type=\"humidity\" rate=\"" + rate + "\" ><output component=\"x\">hum</output><output component=\"t\">hum_time</output></sensor>").getBytes());
                    if (light)
                        output.write(("<sensor type=\"light\" rate=\"" + rate + "\" ><output component=\"x\">light</output><output component=\"t\">light_time</output></sensor>").getBytes());
                    if (lin)
                        output.write(("<sensor type=\"linear_acceleration\" rate=\"" + rate + "\" ><output component=\"x\">linX</output><output component=\"y\">linY</output><output component=\"z\">linZ</output><output component=\"t\">lin_time</output></sensor>").getBytes());
                    if (loc)
                        output.write(("<location><output component=\"lat\">locLat</output><output component=\"lon\">locLon</output><output component=\"z\">locZ</output><output component=\"t\">loc_time</output><output component=\"v\">locV</output><output component=\"dir\">locDir</output><output component=\"accuracy\">locAccuracy</output><output component=\"zAccuracy\">locZAccuracy</output><output component=\"status\">locStatus</output><output component=\"satellites\">locSatellites</output></location>").getBytes());
                    if (mag)
                        output.write(("<sensor type=\"magnetic_field\" rate=\"" + rate + "\" ><output component=\"x\">magX</output><output component=\"y\">magY</output><output component=\"z\">magZ</output><output component=\"t\">mag_time</output></sensor>").getBytes());
                    if (pressure)
                        output.write(("<sensor type=\"pressure\" rate=\"" + rate + "\" ><output component=\"x\">pressure</output><output component=\"t\">pressure_time</output></sensor>").getBytes());
                    if (prox)
                        output.write(("<sensor type=\"proximity\" rate=\"" + rate + "\" ><output component=\"x\">prox</output><output component=\"t\">prox_time</output></sensor>").getBytes());
                    if (temp)
                        output.write(("<sensor type=\"temperature\" rate=\"" + rate + "\" ><output component=\"x\">temp</output><output component=\"t\">temp_time</output></sensor>").getBytes());
                    output.write("</input>".getBytes());

                    //Views for each sensor
                    output.write("<views>".getBytes());
                    if (acc) {
                        output.write("<view label=\"Accelerometer\">".getBytes());
                        output.write(("<graph label=\"Acceleration X\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accX</input></graph>").getBytes());
                        output.write(("<graph label=\"Acceleration Y\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accY</input></graph>").getBytes());
                        output.write(("<graph label=\"Acceleration Z\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (gyr) {
                        output.write("<view label=\"Gyroscope\">".getBytes());
                        output.write(("<graph label=\"Gyroscope X\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrX</input></graph>").getBytes());
                        output.write(("<graph label=\"Gyroscope Y\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrY</input></graph>").getBytes());
                        output.write(("<graph label=\"Gyroscope Z\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (hum) {
                        output.write("<view label=\"Humidity\">".getBytes());
                        output.write(("<graph label=\"Humidity\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Relative Humidity (%)\" partialUpdate=\"true\"><input axis=\"x\">hum_time</input><input axis=\"y\">hum</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (light) {
                        output.write("<view label=\"Light\">".getBytes());
                        output.write(("<graph label=\"Illuminance\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Ev (lx)\" partialUpdate=\"true\"><input axis=\"x\">light_time</input><input axis=\"y\">light</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (lin) {
                        output.write("<view label=\"Linear Acceleration\">".getBytes());
                        output.write(("<graph label=\"Linear Acceleration X\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linX</input></graph>").getBytes());
                        output.write(("<graph label=\"Linear Acceleration Y\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linY</input></graph>").getBytes());
                        output.write(("<graph label=\"Linear Acceleration Z\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (loc) {
                        output.write("<view label=\"Location\">".getBytes());
                        output.write(("<graph label=\"Latitude\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Latitude (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locLat</input></graph>").getBytes());
                        output.write(("<graph label=\"Longitude\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Longitude (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locLon</input></graph>").getBytes());
                        output.write(("<graph label=\"Height\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"z (m)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locZ</input></graph>").getBytes());
                        output.write(("<graph label=\"Velocity\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"v (m/s)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locV</input></graph>").getBytes());
                        output.write(("<graph label=\"Direction\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"heading (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locDir</input></graph>").getBytes());
                        output.write(("<value label=\"Horizontal Accuracy\" size=\"1\" precision=\"1\" unit=\"m\"><input>locAccuracy</input></value>").getBytes());
                        output.write(("<value label=\"Vertical Accuracy\" size=\"1\" precision=\"1\" unit=\"m\"><input>locZAccuracy</input></value>").getBytes());
                        output.write(("<value label=\"Satellites\" size=\"1\" precision=\"0\"><input>locSatellites</input></value>").getBytes());
                        output.write(("<value label=\"Status\" size=\"1\" precision=\"0\"><input>locStatus</input><map max=\"-1\">GPS disabled</map><map max=\"0\">Waiting for signal</map><map max=\"1\">Active</map></value>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (mag) {
                        output.write("<view label=\"Magnetometer\">".getBytes());
                        output.write(("<graph label=\"Magnetic field X\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magX</input></graph>").getBytes());
                        output.write(("<graph label=\"Magnetic field Y\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magY</input></graph>").getBytes());
                        output.write(("<graph label=\"Magnetic field Z\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (pressure) {
                        output.write("<view label=\"Pressure\">".getBytes());
                        output.write(("<graph label=\"Pressure\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"P (hPa)\" partialUpdate=\"true\"><input axis=\"x\">pressure_time</input><input axis=\"y\">pressure</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (prox) {
                        output.write("<view label=\"Proximity\">".getBytes());
                        output.write(("<graph label=\"Proximity\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Distance (cm)\" partialUpdate=\"true\"><input axis=\"x\">prox_time</input><input axis=\"y\">prox</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (temp) {
                        output.write("<view label=\"Temperature\">".getBytes());
                        output.write(("<graph label=\"Temperature\" timeOnX=\"true\" labelX=\"t (s)\" labelY=\"Temperature (°C)\" partialUpdate=\"true\"><input axis=\"x\">temp_time</input><input axis=\"y\">temp</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    output.write("</views>".getBytes());

                    //Export definitions for each sensor
                    output.write("<export>".getBytes());
                    if (acc) {
                        output.write("<set name=\"Accelerometer\">".getBytes());
                        output.write("<data name=\"Time (s)\">acc_time</data>".getBytes());
                        output.write("<data name=\"Acceleration x (m/s^2)\">accX</data>".getBytes());
                        output.write("<data name=\"Acceleration y (m/s^2)\">accY</data>".getBytes());
                        output.write("<data name=\"Acceleration z (m/s^2)\">accZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (gyr) {
                        output.write("<set name=\"Gyroscope\">".getBytes());
                        output.write("<data name=\"Time (s)\">gyr_time</data>".getBytes());
                        output.write("<data name=\"Gyroscope x (rad/s)\">gyrX</data>".getBytes());
                        output.write("<data name=\"Gyroscope y (rad/s)\">gyrY</data>".getBytes());
                        output.write("<data name=\"Gyroscope z (rad/s)\">gyrZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (hum) {
                        output.write("<set name=\"Humidity\">".getBytes());
                        output.write("<data name=\"Time (s)\">hum_time</data>".getBytes());
                        output.write("<data name=\"Relative Humidity (%)\">hum</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (light) {
                        output.write("<set name=\"Light\">".getBytes());
                        output.write("<data name=\"Time (s)\">light_time</data>".getBytes());
                        output.write("<data name=\"Illuminance (lx)\">light</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (lin) {
                        output.write("<set name=\"Linear Acceleration\">".getBytes());
                        output.write("<data name=\"Time (s)\">lin_time</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration x (m/s^2)\">linX</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration y (m/s^2)\">linY</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration z (m/s^2)\">linZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (loc) {
                        output.write("<set name=\"Location\">".getBytes());
                        output.write("<data name=\"Time (s)\">loc_time</data>".getBytes());
                        output.write("<data name=\"Latitude (°)\">locLat</data>".getBytes());
                        output.write("<data name=\"Longitude (°)\">locLon</data>".getBytes());
                        output.write("<data name=\"Height (m)\">locZ</data>".getBytes());
                        output.write("<data name=\"Velocity (m/s)\">locV</data>".getBytes());
                        output.write("<data name=\"Direction (°)\">locDir</data>".getBytes());
                        output.write("<data name=\"Horizontal Accuracy (m)\">locAccuracy</data>".getBytes());
                        output.write("<data name=\"Vertical Accuracy (m)\">locZAccuracy</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (mag) {
                        output.write("<set name=\"Magnetometer\">".getBytes());
                        output.write("<data name=\"Time (s)\">mag_time</data>".getBytes());
                        output.write("<data name=\"Magnetic field x (µT)\">magX</data>".getBytes());
                        output.write("<data name=\"Magnetic field y (µT)\">magY</data>".getBytes());
                        output.write("<data name=\"Magnetic field z (µT)\">magZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (pressure) {
                        output.write("<set name=\"Pressure\">".getBytes());
                        output.write("<data name=\"Time (s)\">pressure_time</data>".getBytes());
                        output.write("<data name=\"Pressure (hPa)\">pressure</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (prox) {
                        output.write("<set name=\"Proximity\">".getBytes());
                        output.write("<data name=\"Time (s)\">prox_time</data>".getBytes());
                        output.write("<data name=\"Distance (cm)\">prox</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (temp) {
                        output.write("<set name=\"Temperature\">".getBytes());
                        output.write("<data name=\"Time (s)\">temp_time</data>".getBytes());
                        output.write("<data name=\"Temperature (°C)\">temp</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    output.write("</export>".getBytes());

                    //And finally, the closing tag
                    output.write("</phyphox>".getBytes());

                    output.close();

                    //Create an intent for this new file
                    Intent intent = new Intent(c, Experiment.class);
                    intent.putExtra(ExperimentItemAdapter.EXPERIMENT_XML, file);
                    intent.putExtra(EXPERIMENT_ISASSET, false);
                    intent.setAction(Intent.ACTION_VIEW);

                    //Start the new experiment
                    c.startActivity(intent);
                } catch (Exception e) {
                    Log.e("newExperiment", "Could not create new experiment.", e);
                }
            }
        });
        neDialog.setNegativeButton(res.getText(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //If the user aborts the dialog, we don't have to do anything
            }
        });

        //Finally, show the dialog
        neDialog.show();
    }

}

//This adapter is used to fill the gridView of the categories in the experiment list.
//So, this can be considered to be the experiment entries within an category
class ExperimentItemAdapter extends BaseAdapter {

    public final static String EXPERIMENT_XML = "com.dicon.phyphox.EXPERIMENT_XML";
    public final static String EXPERIMENT_RESOURCELIST = "com.dicon.phyphox.EXPERIMENT_RESOURCELIST";
    public final static String EXPERIMENT_ISTEMP = "com.dicon.phyphox.EXPERIMENT_ISTEMP";
    public final static String EXPERIMENT_ISASSET = "com.dicon.phyphox.EXPERIMENT_ISASSET";
    public final static String EXPERIMENT_UNAVAILABLESENSOR = "com.dicon.phyphox.EXPERIMENT_UNAVAILABLESENSOR";
    public final static String EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS= "com.dicon.phyphox.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS";

    final private Activity parentActivity; //Reference to the main activity for the alertDialog when deleting files
    final private boolean isSimpleExperiment, isSavedState;

    private String preselectedBluetoothAddress = null;

    private final ExperimentRepository experimentRepository;

    Resources res;

    //Experiment data

    Vector<ExperimentListActivity.ExperimentShortInfo> experimentShortInfos = new Vector<>();

    //The constructor takes the activity reference. That's all.
    public ExperimentItemAdapter(Activity parentActivity, String category, ExperimentRepository experimentRepository) {
        this.parentActivity = parentActivity;
        res = parentActivity.getResources();
        this.isSavedState = category.equals(res.getString(R.string.save_state_category));
        this.isSimpleExperiment = category.equals(res.getString(R.string.categoryNewExperiment));

        ExperimentListEnvironment environment = new ExperimentListEnvironment(parentActivity.getAssets(), parentActivity.getResources(), parentActivity, parentActivity );
        this.experimentRepository = experimentRepository;

    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        this.preselectedBluetoothAddress = preselectedBluetoothAddress;
    }

    //The number of elements is just the number of icons. (Any of the lists should do)
    public int getCount() {
        return experimentShortInfos.size();
    }

    //We don't need to pick an object with this interface, but it has to be implemented
    public Object getItem(int position) {
        return null;
    }

    //The index is used as an id. That's enough, but has to be implemented
    public long getItemId(int position) {
        return position;
    }

    //This starts the intent for an experiment if the user clicked an experiment.
    //It takes the index and the view that has been clicked (just for the animation)
    public void start(int position, View v) {
        //Create the intent and place the experiment location in it
        Intent intent = new Intent(v.getContext(), Experiment.class);
        intent.putExtra(EXPERIMENT_XML, experimentShortInfos.get(position).xmlFile);
        intent.putExtra(EXPERIMENT_ISTEMP, experimentShortInfos.get(position).isTemp);
        intent.putExtra(EXPERIMENT_ISASSET, experimentShortInfos.get(position).isAsset);
        intent.putExtra(EXPERIMENT_UNAVAILABLESENSOR, experimentShortInfos.get(position).unavailableSensor);
        if (this.preselectedBluetoothAddress != null)
            intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, this.preselectedBluetoothAddress);
        intent.setAction(Intent.ACTION_VIEW);

        //If we are on a recent API, we can add a nice zoom animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityOptions options = ActivityOptions.makeScaleUpAnimation(v, 0,
                    0, v.getWidth(), v.getHeight());
            v.getContext().startActivity(intent, options.toBundle());
        } else { //old API? Just fire up the experiment.
            v.getContext().startActivity(intent);
        }
    }

    //Called to fill the adapter with experiment.
    //For each experiment we need an icon, a title, a short description, the location of the
    // file and whether it can be found as an asset or a local file.
    public void addExperiment(ExperimentListActivity.ExperimentShortInfo shortInfo) {
        //Insert it alphabetically into out list. So find the element before which the new
        //title belongs.
        int i;
        for (i = 0; i < experimentShortInfos.size(); i++) {
            if (experimentShortInfos.get(i).title.compareTo(shortInfo.title) >= 0)
                break;
        }

        experimentShortInfos.insertElementAt(shortInfo, i);

        //Notify the adapter that we changed its contents
        this.notifyDataSetChanged();
    }

    //This mini class holds all the Android views to be displayed
    public class Holder {
        ImageView icon; //The icon
        TextView title; //The title text
        TextView info;  //The short description text
        ImageButton menuBtn; //A button for a context menu for local experiments (if they are not an asset)
    }

    public void showExperimentInfo(String title, String sensorNotAvailableInfo, String description, Map<String, String> links, Context c){
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle(title);

        LinearLayout ll = new LinearLayout(builder.getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        int marginX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_horizontal_padding), res.getDisplayMetrics());
        int marginY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_vertical_padding), res.getDisplayMetrics());
        ll.setPadding(marginX, marginY, marginX, marginY);

        TextView stateLabel = new TextView(builder.getContext());
        stateLabel.setText(sensorNotAvailableInfo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,Math.round(res.getDimension(R.dimen.font)));
        stateLabel.setLayoutParams(lp);
        stateLabel.setTextSize(12.0f);
        ll.addView(stateLabel);

        TextView description_ = new TextView(builder.getContext());
        description_.setText(description);

        ll.addView(description_);

        for (String label : links.keySet()) {
            Button btn = new Button(builder.getContext());
            btn.setText(label);
            final String url = links.get(label);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Uri uri = Uri.parse(url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    if (intent.resolveActivity(parentActivity.getPackageManager()) != null) {
                        parentActivity.startActivity(intent);
                    }
                }
            });
            ll.addView(btn);
        }

        ScrollView sv = new ScrollView(builder.getContext());
        sv.setHorizontalScrollBarEnabled(false);
        sv.setVerticalScrollBarEnabled(true);
        sv.addView(ll);

        builder.setView(sv);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    //Construct the view for an element.
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder; //Holds all views. loaded from convertView or reconstructed
        if(convertView == null) { //No convertView there. Let's build from scratch.

            //Create the convertView from our layout and create an onClickListener
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.experiment_item, null);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (experimentShortInfos.get(position).isLink != null) {
                        try {
                            Uri uri = Uri.parse(experimentShortInfos.get(position).isLink);
                            if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(parentActivity.getPackageManager()) != null) {
                                    parentActivity.startActivity(intent);
                                    return;
                                }
                            }
                        } catch (Exception ignored) {

                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage("This entry is just a link, but its URL is invalid.")
                                .setTitle("Invalid URL")
                                .setPositiveButton(R.string.ok, (dialog, id) -> {

                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    } else if (experimentShortInfos.get(position).unavailableSensor < 0)
                        start(position, v);
                    else {
                        String title = experimentShortInfos.get(position).title;
                        String sensorNotAvailableWarningText = res.getString(R.string.sensorNotAvailableWarningText1) + " " +
                                res.getString(experimentShortInfos.get(position).unavailableSensor) + " " +
                                res.getString(R.string.sensorNotAvailableWarningText2);
                        String description = experimentShortInfos.get(position).fullDescription;
                        Map<String, String> links = experimentShortInfos.get(position).links;

                        showExperimentInfo(title, sensorNotAvailableWarningText, description, links, parentActivity);
                    }
                }
            });

            //Create our holder and set its refernces to the views
            holder = new Holder();
            holder.icon = convertView.findViewById(R.id.expIcon);
            holder.title = convertView.findViewById(R.id.expTitle);
            holder.info = convertView.findViewById(R.id.expInfo);
            holder.menuBtn = convertView.findViewById(R.id.menuButton);

            //Connect the convertView and the holder to retrieve it later
            convertView.setTag(holder);
        } else {
            //There is an existing view. Retrieve its holder
            holder = (Holder) convertView.getTag();
        }

        //Update icons and texts
        holder.icon.setImageDrawable(experimentShortInfos.get(position).icon);
        holder.title.setText(experimentShortInfos.get(position).title);
        holder.info.setText(experimentShortInfos.get(position).description);

        if (experimentShortInfos.get(position).unavailableSensor >= 0) {
            holder.title.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
            holder.info.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
        }

        //Handle the menubutton. Set it visible only for non-assets
        if (experimentShortInfos.get(position).isTemp != null || experimentShortInfos.get(position).isAsset)
            holder.menuBtn.setVisibility(ImageView.GONE); //Asset - no menu button
        else {
            //No asset. Menu button visible and it needs an onClickListener
            holder.menuBtn.setVisibility(ImageView.VISIBLE);
            holder.menuBtn.setColorFilter(RGB.fromRGB(255, 255, 255).autoLightColor(res).intColor(), android.graphics.PorterDuff.Mode.SRC_IN);
            holder.menuBtn.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(new ContextThemeWrapper(parentActivity, R.style.Theme_Phyphox_DayNight), v);
                popup.getMenuInflater().inflate(R.menu.experiment_item_context, popup.getMenu());

                popup.getMenu().findItem(R.id.experiment_item_rename).setVisible(isSavedState);

                popup.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.experiment_item_share: {
                            File file = new File(parentActivity.getFilesDir(), "/"+experimentShortInfos.get(position).xmlFile);

                            final Uri uri = FileProvider.getUriForFile(parentActivity.getBaseContext(), parentActivity.getPackageName() + ".exportProvider", file);
                            final Intent intent = ShareCompat.IntentBuilder.from(parentActivity)
                                    .setType("application/octet-stream") //mime type from the export filter
                                    .setSubject(parentActivity.getString(R.string.save_state_subject))
                                    .setStream(uri)
                                    .getIntent()
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            List<ResolveInfo> resInfoList = parentActivity.getPackageManager().queryIntentActivities(intent, 0);
                            for (ResolveInfo ri : resInfoList) {
                                parentActivity.grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }

                            //Create chooser
                            Intent chooser = Intent.createChooser(intent, parentActivity.getString(R.string.share_pick_share));
                            //And finally grant permissions again for any activities created by the chooser
                            resInfoList = parentActivity.getPackageManager().queryIntentActivities(chooser, 0);
                            for (ResolveInfo ri : resInfoList) {
                                if (ri.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID
                                ))
                                    continue;
                                parentActivity.grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }
                            //Execute this intent
                            parentActivity.startActivity(chooser);
                            return true;
                        }
                        case R.id.experiment_item_delete: {
                            //Create dialog to ask the user if he REALLY wants to delete...
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(res.getString(R.string.confirmDelete))
                                    .setTitle(R.string.confirmDeleteTitle)
                                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Confirmed. Delete the item and reload the list
                                            long crc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                            File resFolder = new File(parentActivity.getFilesDir(), Long.toHexString(crc32).toLowerCase());
                                            Log.d("ExperimentList", "Deleting " + experimentShortInfos.get(position).xmlFile);
                                            parentActivity.deleteFile(experimentShortInfos.get(position).xmlFile);
                                            if (resFolder.isDirectory()) {
                                                Log.d("ExperimentList", "Also deleting resource folder " + Long.toHexString(crc32).toLowerCase());
                                                String[] files = resFolder.list();
                                                for (String file : files) {
                                                    if (new File(resFolder, file).delete()) {
                                                        Log.d("ExperimentList", "Done.");
                                                    } else {
                                                        Log.d("ExperimentList", "Failed.");
                                                    }
                                                }
                                            } else {
                                                Log.d("ExperimentList", "No resource folder found at " + resFolder.getAbsolutePath());
                                            }
                                            experimentRepository.loadExperimentList();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Aborted by user. Nothing to do.
                                        }
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return true;
                        }
                        case R.id.experiment_item_rename: {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            final EditText edit = new EditText(parentActivity);
                            edit.setText(experimentShortInfos.get(position).title);
                            builder.setView(edit)
                                    .setTitle(R.string.rename)
                                    .setPositiveButton(R.string.rename, (dialog, id) -> {
                                        String newName = edit.getText().toString();
                                        if (newName.replaceAll("\\s+", "").isEmpty())
                                            return;
                                        //Confirmed. Rename the item and reload the list

                                        long oldCrc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                        File oldResFolder = new File(parentActivity.getFilesDir(), Long.toHexString(oldCrc32).toLowerCase());

                                        if (isSavedState)
                                            Helper.replaceTagInFile(experimentShortInfos.get(position).xmlFile, parentActivity.getApplicationContext(), "/phyphox/state-title", newName);

                                        long newCrc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                        File newResFolder = new File(parentActivity.getFilesDir(), Long.toHexString(newCrc32).toLowerCase());

                                        if (oldResFolder.isDirectory()) {
                                            newResFolder.mkdirs();
                                            String[] files = oldResFolder.list();
                                            for (String file : files) {
                                                Log.d("ExperimentList", "Moving resource file " + file);
                                                if (new File(oldResFolder, file).renameTo(new File(newResFolder, file))) {
                                                    Log.d("ExperimentList", "Done.");
                                                } else {
                                                    Log.d("ExperimentList", "Failed.");
                                                }
                                            }
                                        }

                                        experimentRepository.loadExperimentList();
                                    })
                                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                                        //Aborted by user. Nothing to do.
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return true;
                        }

                    }
                    return false;
                });

                popup.show();
            });
        }

        return convertView;
    }
}

interface  ExperimentLoader {
    List<ExperimentListActivity.ExperimentShortInfo> loadExperiments();
    PhyphoxFile.PhyphoxStream loadExperiment(String xmlFile);
}

class AssetExperimentLoader{
    ExperimentListEnvironment environment;

    private final ExperimentRepository repository;

    Vector<ExperimentsInCategory> categories;

    private final HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    private final HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList  = new HashMap<>();  //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices


    public AssetExperimentLoader(ExperimentListEnvironment environment, ExperimentRepository repository) {
        this.environment = environment;
        this.repository = repository;
        categories = new Vector<>();
    }


    public ExperimentListActivity.ExperimentShortInfo loadExperimentsShortInfo(ExperimentLoadInfoData data) {

        return loadExperimentShortInfo(data);
    }

    public void setCategories(Vector<ExperimentsInCategory> categories) {
        if(!new HashSet<>(this.categories).containsAll(categories)){
            this.categories.addAll(categories);
        }

    }

    public HashMap<String, Vector<String>> getBluetoothDeviceNameList() {
        return bluetoothDeviceNameList;
    }

    public HashMap<UUID, Vector<String>> getBluetoothDeviceUUIDList() {
        return bluetoothDeviceUUIDList;
    }

    //The third addExperiment function:
    //ExperimentItemAdapter.addExperiment(...) is called by category.addExperiment(...), which in
    //turn will be called here.
    //This addExperiment(...) is called for each experiment found. It checks if the experiment's
    // category already exists and adds it to this category or creates a category for the experiment
    protected void addExperiment(ExperimentListActivity.ExperimentShortInfo shortInfo, String cat) {
        //this.categories.clear();
        //Check all categories for the category of the new experiment
        for (ExperimentsInCategory icat : this.categories) {
            if (icat.hasName(cat)) {
                //Found it. Add the experiment and return
                icat.addExperiment(shortInfo);
                return;
            }
        }
        //Category does not yet exist. Create it and add the experiment
        categories.add(new ExperimentsInCategory(cat, environment.parent, repository));
        categories.lastElement().addExperiment(shortInfo);
    }

    private void addInvalidExperiment(String xmlFile, String message, String isTemp, boolean isAsset, Vector<ExperimentsInCategory> categories) {
        Toast.makeText(environment.context, message, Toast.LENGTH_LONG).show();
        Log.e("list:loadExperiment", message);
        ExperimentListActivity.ExperimentShortInfo shortInfo = new ExperimentListActivity.ExperimentShortInfo();
        shortInfo.title = xmlFile;
        shortInfo.color = new RGB(0xffff0000);
        shortInfo.icon = new TextIcon("!", environment.context);
        shortInfo.description = message;
        shortInfo.xmlFile = xmlFile;
        shortInfo.isTemp = isTemp;
        shortInfo.isAsset = isAsset;
        shortInfo.unavailableSensor = -1;
        shortInfo.isLink = null;
        if (categories != null)
            addExperiment(shortInfo, environment.resources.getString(R.string.unknown));
    }

    public void showCurrentCameraAvaibility(){
        //We want to show current availability of experiments requiring cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cm = (CameraManager) environment.context.getSystemService(Context.CAMERA_SERVICE);
            CameraHelper.updateCameraList(cm);
        }
    }

    //Minimalistic loading function. This only retrieves the data necessary to list the experiment.
    private ExperimentListActivity.ExperimentShortInfo loadExperimentShortInfo(ExperimentLoadInfoData data) {
        //Class to hold results of the few items we care about
        ExperimentListActivity.ExperimentShortInfo shortInfo = new ExperimentListActivity.ExperimentShortInfo();

        XmlPullParser xpp;
        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            //Prepare the PullParser
            xpp = Xml.newPullParser();
            xpp.setInput(data.input, "UTF-8");
        } catch (XmlPullParserException e) {
            Toast.makeText(environment.context, "Cannot open " + data.experimentXML + ".", Toast.LENGTH_LONG).show();
            return shortInfo;
        }

        shortInfo.color = new RGB(environment.resources.getColor(R.color.phyphox_primary)); //Icon base color
        shortInfo.description = "";
        shortInfo.fullDescription = "";
        shortInfo.unavailableSensor = -1;
        shortInfo.resources = new ArraySet<>();
        shortInfo.links = new LinkedHashMap<>();
        String stateTitle = null; //A title given by the user for a saved experiment state
        String category = null;
        boolean customColor = false;
        String icon = ""; //Experiment icon (just the raw data as defined in the experiment file. Will be interpreted below)
        BaseColorDrawable image = null; //This will hold the icon

        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            int eventType = xpp.getEventType(); //should be START_DOCUMENT
            int phyphoxDepth = -1; //Depth of the first phyphox tag (We only care for title, icon, description and category directly below the phyphox tag)
            int translationBlockDepth = -1; //Depth of the translations block
            int translationDepth = -1; //Depth of a suitable translation, if found.

            //This part is used to check sensor availability before launching the experiment
            SensorManager sensorManager = (SensorManager) environment.context.getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...
            boolean inInput = false;
            boolean inOutput = false;
            boolean inViews = false;
            boolean inView = false;
            boolean isLink = false;
            String link = null;

            int languageRating = 0; //If we find a locale, it replaces previous translations as long as it has a higher rating than the previous one.
            while (eventType != XmlPullParser.END_DOCUMENT) { //Go through all tags until the end...
                switch (eventType) {
                    case XmlPullParser.START_TAG: //React to start tags
                        switch (xpp.getName()) {
                            case "phyphox": //The phyphox tag is the root element of the experiment we want to interpret
                                if (phyphoxDepth < 0) { //There should not be a phyphox tag within an phyphox tag, but who cares. Just ignore it if it happens
                                    phyphoxDepth = xpp.getDepth(); //Remember depth of phyphox tag
                                    String globalLocale = xpp.getAttributeValue(null, "locale");
                                    String isLinkStr = xpp.getAttributeValue(null, "isLink");
                                    if (isLinkStr != null)
                                        isLink = isLinkStr.toUpperCase().equals("TRUE");
                                    int thisLaguageRating = Helper.getLanguageRating(environment.resources, globalLocale);
                                    if (thisLaguageRating > languageRating)
                                        languageRating = thisLaguageRating;
                                }
                                break;
                            case "translations": //The translations block may contain a localized title and description
                                if (xpp.getDepth() != phyphoxDepth + 1) //Translations block has to be immediately below phyphox tag
                                    break;
                                if (translationBlockDepth < 0) {
                                    translationBlockDepth = xpp.getDepth(); //Remember depth of the block
                                }
                                break;
                            case "translation": //The translation block may contain our localized version
                                if (xpp.getDepth() != translationBlockDepth + 1) //The translation has to be immediately below he translations block
                                    break;
                                String thisLocale = xpp.getAttributeValue(null, "locale");
                                int thisLaguageRating = Helper.getLanguageRating(environment.resources, thisLocale);
                                if (translationDepth < 0 && thisLaguageRating > languageRating) {
                                    languageRating = thisLaguageRating;
                                    translationDepth = xpp.getDepth(); //Remember depth of the translation block
                                }
                                break;
                            case "title": //This should give us the experiment title
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) //May be in phyphox root or from a valid translation
                                    shortInfo.title = xpp.nextText().trim();
                                break;
                            case "state-title":
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) //May be in phyphox root or from a valid translation
                                    stateTitle = xpp.nextText().trim();
                                break;
                            case "icon": //This should give us the experiment icon (might be an acronym or a base64-encoded image)
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) { //May be in phyphox root or from a valid translation
                                    if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("base64")) { //Check the icon type
                                        //base64 encoded image. Decode it
                                        icon = xpp.nextText().trim();
                                        try {
                                            Bitmap bitmap = Helper.decodeBase64(icon);
                                            // This bitmap will be used for the icon used in contribution headline
                                            if (bitmap != null) {
                                                image = new BitmapIcon(bitmap, environment.context);
                                            }

                                        } catch (IllegalArgumentException e) {
                                            Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                        }
                                    } else if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("svg")) { //Check the icon type
                                        //SVG image. Handle it with AndroidSVG
                                        icon = xpp.nextText().trim();
                                        try {
                                            SVG svg = SVG.getFromString(icon);
                                            image = new VectorIcon(svg, environment.context);
                                        } catch (SVGParseException e) {
                                            Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                        }
                                    } else {
                                        //Just a string. Create an icon from it. We allow a maximum of three characters.
                                        icon = xpp.nextText().trim();
                                        if (icon.length() > 3)
                                            icon = icon.substring(0, 3);
                                        image = new TextIcon(icon, environment.context);
                                    }

                                }
                                break;
                            case "description": //This should give us the experiment description, but we only need the first line
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) {
                                    shortInfo.fullDescription = xpp.nextText().trim().replaceAll("(?m) +$", "").replaceAll("(?m)^ +", "");
                                    shortInfo.description = shortInfo.fullDescription.trim().split("\n", 2)[0];
                                } //May be in phyphox root or from a valid translation
                                //Remove any whitespaces and take the first line until the first line break
                                break;
                            case "category": //This should give us the experiment category
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) //May be in phyphox root or from a valid translation
                                    category = xpp.nextText().trim();
                                break;
                            case "link": //This should give us a link if the experiment is only a dummy entry with a link

                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) {
                                    //link = xpp.nextText().trim();
                                    if (xpp.getAttributeValue(null, "label") != null && xpp.getAttributeValue(null, "label").equals("Wiki")) {
                                        link = xpp.nextText().trim();
                                        shortInfo.links.put("Wiki", link);
                                        Log.d("loadExperimentInfo", "Found link: WIKI " + link);
                                    } else if (xpp.getAttributeValue(null, "label") != null && xpp.getAttributeValue(null, "label").equals("Video")) {
                                        link = xpp.nextText().trim();
                                        shortInfo.links.put("Video", link);
                                        Log.d("loadExperimentInfo", "Found link: Video " + link);
                                    } else if (xpp.getAttributeValue(null, "label") != null && xpp.getAttributeValue(null, "label").equals("x / y / z")) {
                                        link = xpp.nextText().trim();
                                        shortInfo.links.put("x / y / z", link);
                                        Log.d("loadExperimentInfo", "Found link: x / y / z " + link);
                                    }

                                } //May be in phyphox root or from a valid translation

                                break;
                            case "color": //This is the base color for design decisions (icon background color and category color)
                                if (xpp.getDepth() == phyphoxDepth + 1 || xpp.getDepth() == translationDepth + 1) { //May be in phyphox root or from a valid translation
                                    customColor = true;
                                    try {
                                        shortInfo.color = RGB.fromPhyphoxString(xpp.nextText().trim(), environment.resources, new RGB(environment.resources.getColor(R.color.phyphox_primary)));
                                    } catch (Exception e) {
                                        customColor = false;
                                    }
                                }
                                break;
                            case "input": //We just have to check if there are any sensors, which are not supported on this device
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inInput = true;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inOutput = true;
                                break;
                            case "views":
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inViews = true;
                                break;
                            case "view":
                                if (xpp.getDepth() == phyphoxDepth + 2 && inViews)
                                    inView = true;
                                break;
                            case "image":
                                if (!inView)
                                    break;
                                String src = xpp.getAttributeValue(null, "src");
                                shortInfo.resources.add(src);
                                break;
                            case "sensor":
                                if (!inInput || shortInfo.unavailableSensor >= 0)
                                    break;
                                String type = xpp.getAttributeValue(null, "type");
                                String typeFilterStr = xpp.getAttributeValue(null, "typeFilter");
                                int typeFilter = -1;
                                try {
                                    typeFilter = Integer.parseInt(typeFilterStr);
                                } catch (Exception ignored) {

                                }
                                String nameFilter = xpp.getAttributeValue(null, "nameFilter");
                                String ignoreUnavailableStr = xpp.getAttributeValue(null, "ignoreUnavailable");
                                boolean ignoreUnavailable = (ignoreUnavailableStr != null && Boolean.valueOf(ignoreUnavailableStr));
                                SensorInput testSensor;
                                try {
                                    testSensor = new SensorInput(type, nameFilter, typeFilter, ignoreUnavailable, 0, SensorInput.SensorRateStrategy.auto, 0, false, null, null, null);
                                    testSensor.attachSensorManager(sensorManager);
                                } catch (SensorInput.SensorException e) {
                                    shortInfo.unavailableSensor = SensorInput.getDescriptionRes(SensorInput.resolveSensorString(type));
                                    break;
                                }
                                if (!(testSensor.isAvailable() || testSensor.ignoreUnavailable)) {
                                    shortInfo.unavailableSensor = SensorInput.getDescriptionRes(SensorInput.resolveSensorString(type));
                                }
                                break;
                            case "location":
                                if (!inInput || shortInfo.unavailableSensor >= 0)
                                    break;
                                if (!GpsInput.isAvailable(environment.context)) {
                                    shortInfo.unavailableSensor = R.string.location;
                                }
                                break;
                            case "depth":
                                if (!inInput || shortInfo.unavailableSensor >= 0)
                                    break;
                                if (!DepthInput.isAvailable()) {
                                    shortInfo.unavailableSensor = R.string.sensorDepth;
                                }
                                break;
                            case "camera":
                                PackageManager pm = environment.context.getPackageManager();
                                if (!inInput || shortInfo.unavailableSensor >= 0)
                                    break;
                                if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
                                    shortInfo.unavailableSensor = R.string.sensorCamera;
                                break;
                            case "bluetooth":
                                if ((!inInput && !inOutput) || shortInfo.unavailableSensor >= 0) {
                                    break;
                                }
                                String name = xpp.getAttributeValue(null, "name");
                                String uuidStr = xpp.getAttributeValue(null, "uuid");
                                UUID uuid = null;
                                try {
                                    uuid = UUID.fromString(uuidStr);
                                } catch (Exception ignored) {

                                }


                                if (name != null && !name.isEmpty()) {
                                    if (bluetoothDeviceNameList != null) {
                                        if (!bluetoothDeviceNameList.containsKey(name))
                                            bluetoothDeviceNameList.put(name, new Vector<>());
                                        bluetoothDeviceNameList.get(name).add(data.experimentXML);
                                    }
                                }
                                if (uuid != null) {
                                    if (bluetoothDeviceUUIDList != null) {
                                        if (!bluetoothDeviceUUIDList.containsKey(uuid))
                                            bluetoothDeviceUUIDList.put(uuid, new Vector<String>());
                                        bluetoothDeviceUUIDList.get(uuid).add(data.experimentXML);
                                    }
                                }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                    shortInfo.unavailableSensor = R.string.bluetooth;
                                } else if (!Bluetooth.isSupported(environment.context)) {
                                    shortInfo.unavailableSensor = R.string.bluetooth;
                                }
                                if (!customColor)
                                    shortInfo.color = new RGB(environment.resources.getColor(R.color.phyphox_blue_100));
                                break;
                        }
                        break;
                    case XmlPullParser.END_TAG: //React to end tags
                        switch (xpp.getName()) {
                            case "phyphox": //We are leaving the phyphox tag
                                if (xpp.getDepth() == phyphoxDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    phyphoxDepth = -1;
                                }
                                break;
                            case "translations": //We are leaving the phyphox tag
                                if (xpp.getDepth() == translationBlockDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    translationBlockDepth = -1;
                                }
                                break;
                            case "translation": //We are leaving the phyphox tag
                                if (xpp.getDepth() == translationDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    translationDepth = -1;
                                }
                                break;
                            case "input":
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inInput = false;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inOutput = false;
                                break;
                            case "views":
                                if (xpp.getDepth() == phyphoxDepth + 1)
                                    inViews = false;
                                break;
                            case "view":
                                if (xpp.getDepth() == phyphoxDepth + 2)
                                    inView = false;
                                break;
                        }
                        break;

                }
                eventType = xpp.next(); //Next event in the file...
            }
            //Sanity check: We need a title!
            if (shortInfo.title == null) {
                addInvalidExperiment(data.experimentXML, "Invalid: \" + experimentXML + \" misses a title.", data.isTemp, data.isAsset, categories);
                return null; // TODO implement better approach
            }

            //Sanity check: We need a category!
            if (category == null) {
                addInvalidExperiment(data.experimentXML, "Invalid: \" + experimentXML + \" misses a category.", data.isTemp, data.isAsset, categories);
                return null;
            }

            if (stateTitle != null) {
                shortInfo.description = shortInfo.title;
                shortInfo.title = stateTitle;
                category = environment.resources.getString(R.string.save_state_category);
            }

            //Let's check the icon
            if (image == null) //No icon given. Create a TextIcon from the first three characters of the title
                image = new TextIcon(shortInfo.title.substring(0, Math.min(shortInfo.title.length(), 3)), environment.context);

            //We have all the information. Add the experiment.
            image.setBaseColor(shortInfo.color);
            if (categories != null) {
                shortInfo.icon = image;
                shortInfo.description = isLink ? "Link: " + link : shortInfo.description;
                shortInfo.xmlFile = data.experimentXML;
                shortInfo.isTemp = data.isTemp;
                shortInfo.isAsset = data.isAsset;
                shortInfo.isLink = isLink ? link : null;
                shortInfo.categoryName = category;

                //addExperiment(shortInfo, category, data.categories);
            }
        } catch (XmlPullParserException e) { //XML Pull Parser is unhappy... Abort and notify user.
            addInvalidExperiment(data.experimentXML, "Error loading " + data.experimentXML + " (XML Exception)", data.isTemp, data.isAsset, categories);
        } catch (IOException e) { //IOException... Abort and notify user.
            addInvalidExperiment(data.experimentXML, "Error loading " + data.experimentXML + " (IOException)", data.isTemp, data.isAsset, categories);
        }
        return shortInfo;
    }

    protected void loadAndAddExperimentFromLocalFile() {
        //Load experiments from local files
        try {
            //Get all files that end on ".phyphox"
            File[] files = environment.getFilesDir().listFiles((dir, filename) -> filename.endsWith(".phyphox"));

            for (File file : files) {
                if (file.isDirectory())
                    continue;
                //Load details for each experiment
                InputStream input = environment.context.openFileInput(file.getName());
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, file.getName(), null, false);
                ExperimentListActivity.ExperimentShortInfo shortInfo = loadExperimentsShortInfo(data);
                if (shortInfo != null) {
                    addExperiment(shortInfo, shortInfo.categoryName);
                }

            }

        } catch (IOException e) {
            Toast.makeText(environment.context, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();
        }

    }

    protected void loadAndAddExperimentFromAsset() {
        //Load experiments from assets
        try {

            final String[] experimentXMLs = environment.assetManager.list("experiments"); //All experiments are placed in the experiments folder
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                if (!experimentXML.endsWith(".phyphox"))
                    continue;
                InputStream input = environment.assetManager.open("experiments/" + experimentXML);
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, experimentXML, null, true);
                ExperimentListActivity.ExperimentShortInfo shortInfo = loadExperimentsShortInfo(data);
                if (shortInfo != null) {
                    addExperiment(shortInfo, shortInfo.categoryName);
                }
                //loadExperimentInfo(input, experimentXML, null,true, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(environment.context, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();
        }

    }

    //Load hidden bluetooth experiments - these are not shown but will be offered if a matching Bluetooth device is found during a scan
    protected void loadAndAddExperimentFromHiddenBluetooth() {
        try {
            final String[] experimentXMLs = environment.assetManager.list("experiments/bluetooth");
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                InputStream input = environment.assetManager.open("experiments/bluetooth/" + experimentXML);
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, experimentXML, null, true);
                ExperimentListActivity.ExperimentShortInfo shortInfo = loadExperimentsShortInfo(data);
                if (shortInfo != null) {
                    addExperiment(shortInfo, shortInfo.categoryName);
                }
            }
        } catch (IOException e) {
            Toast.makeText(environment.context, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }


    }

}



class ExperimentRepository{

    ExperimentListEnvironment environment;
    private final AssetExperimentLoader assetExperimentLoader;

    public ExperimentRepository(ExperimentListEnvironment environment) {
        this.environment = environment;

        assetExperimentLoader = new AssetExperimentLoader(environment, this);
    }

    public AssetExperimentLoader getAssetExperimentLoader(){
        return this.assetExperimentLoader;
    }

    public void loadExperimentList(){

        //Clear the old list first
        assetExperimentLoader.categories.clear();
        //bluetoothDeviceNameList.clear();
        //bluetoothDeviceUUIDList.clear();
        assetExperimentLoader.showCurrentCameraAvaibility();

        assetExperimentLoader.loadAndAddExperimentFromLocalFile();
        assetExperimentLoader.loadAndAddExperimentFromAsset();

        addExperimentCategoryToParent();

        assetExperimentLoader.loadAndAddExperimentFromHiddenBluetooth();

    }

    public void addExperimentCategoryToParent_(){
        Collections.sort(getAssetExperimentLoader().categories, new CategoryComparator(assetExperimentLoader.environment.resources));

        LinearLayout parentLayout = environment.parent.findViewById(R.id.experimentList);
        parentLayout.removeAllViews();

        for (ExperimentsInCategory cat : getAssetExperimentLoader().categories) {
            cat.addToParent(parentLayout);
        }
    }

    public void addExperimentCategoryToParent(){
        Collections.sort(getAssetExperimentLoader().categories, new CategoryComparator(assetExperimentLoader.environment.resources));

        LinearLayout parentLayout = environment.parent.findViewById(R.id.experimentList);
        parentLayout.removeAllViews();

        for (ExperimentsInCategory cat : getAssetExperimentLoader().categories) {
            cat.addToParent(parentLayout);
        }
    }
}

class ExperimentListEnvironment {
    public  AssetManager assetManager;
    public Resources resources;
    public Context context;
    public Activity parent;

    public ExperimentListEnvironment(AssetManager assetManager, Resources resources,
                                          Context context, Activity parent) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.assetManager = assetManager;
        this.resources = resources;
        this.parent = parent;

    }

    public  File getFilesDir(){
        return context.getFilesDir();
    }
}

class ExperimentLoadInfoData{
    public InputStream input;
    public String experimentXML;
    public String isTemp;
    public boolean isAsset;
    //public HashMap<String, Vector<String>> bluetoothDeviceNameList;
    //public HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList;

    public ExperimentLoadInfoData(InputStream input, String experimentXML, String isTemp, boolean isAsset) {
        this.input = input;
        this.experimentXML = experimentXML;
        this.isTemp = isTemp;
        this.isAsset = isAsset;
        //this.bluetoothDeviceNameList = bluetoothDeviceNameList;
        //this.bluetoothDeviceUUIDList = bluetoothDeviceUUIDList;
    }
}


//The category class wraps all experiment entries and their views of a category, including the
//grid view and the category headline
class ExperimentsInCategory{
    final private Context parentContext; //Needed to create views
    final private LinearLayout catLayout; //This is the base layout of the category, which will contain the headline and the gridView showing all the experiments
    final private TextView categoryHeadline; //The TextView to display the headline
    final private ExpandableHeightGridView experimentSubList; //The gridView holding experiment items. (See implementation below for the custom flavor "ExpandableHeightGridView")
    final ExperimentItemAdapter experimentItemAdapter; //Instance of the adapter to fill the gridView (implementation above)
    private final Resources res;

    static final String phyphoxCat = "phyphox.org";

    final public String name; //Category name (headline)
    final public Map<RGB, Integer> colorCount = new HashMap<>();

    //ExpandableHeightGridView is derived from the original Android GridView.
    //The structure of our experiment list is such that we want to scroll the entire list, which
    //itself is structured into multiple categories showing multiple grid views. The original
    //grid view only expands as far as it needs to and then only loads the elements it needs to
    //show. This is a good idea for very long (or dynamically loaded) lists, but would make
    //each category scrollable on its own, which is not what we want.
    //ExpandableHeightGridView can be told to expand to show all elements at any time. This
    //destroys the memory efficiency of the original grid view, but we do not expect the
    //experiment to get so huge to need such efficiency. Also, we want to use a gridView instead
    //of a common table to achieve lever on its ability to determine the number of columns on
    //its own.
    //This has been derived from: http://stackoverflow.com/questions/4523609/grid-of-images-inside-scrollview/4536955#4536955
    private class ExpandableHeightGridView extends GridView {

        boolean expanded = false; //The full expand attribute. Is it expanded?

        //Constructor
        public ExpandableHeightGridView(Context context) {
            super(context);
        }

        //Constructor 2
        public ExpandableHeightGridView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        //Constructor 3
        public ExpandableHeightGridView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        //Access to the expanded attribute
        public boolean isExpanded() {
            return expanded;
        }

        @Override
        //The expansion is achieved by overwriting the measured height in the onMeasure event
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (isExpanded()) {
                // Calculate entire height by providing a very large height startMenuItem.
                // View.MEASURED_SIZE_MASK represents the largest height possible.
                int expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST);
                //Send our height to the super onMeasure event
                super.onMeasure(widthMeasureSpec, expandSpec);

                ViewGroup.LayoutParams params = getLayoutParams();
                params.height = getMeasuredHeight();
            } else {
                //We should not expand. Just call the default onMeasure with the original parameters
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        //Interface to set the expanded attribute
        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

    }

    Activity parentActivity;


    //Constructor for the category class, takes a category name, the layout into which it should
    // place its views and the calling activity (mostly to display the dialog in the onClick
    // listener of the delete button for each element - maybe this should be restructured).
    ExperimentsInCategory(String name, Activity parentActivity, ExperimentRepository experimentRepository) {

        this.name = name;
        this.parentActivity = parentActivity;
        this.parentContext = parentActivity;


        //Create the base linear layout to hold title and list
        this.catLayout = new LinearLayout(parentContext);
        catLayout.setOrientation(LinearLayout.VERTICAL);

        this.res = parentActivity.getResources();

        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lllp.setMargins(
                res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-res.getDimensionPixelOffset(R.dimen.expElementMargin),
                0,
                res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-res.getDimensionPixelOffset(R.dimen.expElementMargin),
                res.getDimensionPixelOffset(R.dimen.activity_vertical_margin)
        );
        catLayout.setLayoutParams(lllp);

        //Create the headline text view
        categoryHeadline = new TextView(parentContext);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
//            layout.setMargins(context.getDimensionPixelOffset(R.dimen.expElementMargin), 0, context.getDimensionPixelOffset(R.dimen.expElementMargin), context.getDimensionPixelOffset(R.dimen.expElementMargin));
        categoryHeadline.setLayoutParams(layout);
        categoryHeadline.setText(name.equals(phyphoxCat) ? res.getString(R.string.categoryPhyphoxOrg) : name);
        categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.headline_font));
        categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        categoryHeadline.setPadding(res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10, res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10);

        //Create the gridView for the experiment items
        experimentSubList = new ExpandableHeightGridView(parentContext);
        experimentSubList.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        experimentSubList.setColumnWidth(res.getDimensionPixelOffset(R.dimen.expElementWidth));
        experimentSubList.setNumColumns(ExpandableHeightGridView.AUTO_FIT);
        experimentSubList.setStretchMode(ExpandableHeightGridView.STRETCH_COLUMN_WIDTH);
        experimentSubList.setExpanded(true);

        //Create the adapter and give it to the gridView
        experimentItemAdapter = new ExperimentItemAdapter(parentActivity, name, experimentRepository);
        experimentSubList.setAdapter(experimentItemAdapter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            catLayout.setElevation(res.getDimensionPixelOffset(R.dimen.expElementElevation));
            catLayout.setClipToPadding(false);
            catLayout.setClipChildren(false);
            experimentSubList.setClipToPadding(false);
            experimentSubList.setClipChildren(false);
        }

        //Add headline and experiment list to our base layout
        catLayout.addView(categoryHeadline);
        catLayout.addView(experimentSubList);
    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        experimentItemAdapter.setPreselectedBluetoothAddress(preselectedBluetoothAddress);
    }

    public void getParentScrollViewPosition(){
        //We want to show current availability of experiments requiring cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cm = (CameraManager) parentContext.getSystemService(Context.CAMERA_SERVICE);
            CameraHelper.updateCameraList(cm);
        }

        //Save scroll position to restore this later
        ScrollView sv = parentActivity.findViewById(R.id.experimentScroller);
        int scrollY = sv.getScrollY();

        sv.scrollTo(0, scrollY);
    }

    LinearLayout catList;

    public void addToParent(LinearLayout catList) {

        /**
        if(isDialogView){
            catList = parentActivity.findViewById(R.id.open_multiple_dialog_list);
        } else {
            catList = parentActivity.findViewById(R.id.experimentList);
        }

         */

        //Add the layout to the layout designated by the caller
        catList.addView(catLayout);
    }

    //Helper to check if the name of this category matches a given string
    public boolean hasName(String cat) {
        return cat.equals(name);
    }

    //Wrapper to add an experiment to this category. This just hands it over to the adapter and updates the category color.
    public void addExperiment(ExperimentListActivity.ExperimentShortInfo shortInfo) {
        experimentItemAdapter.addExperiment(shortInfo);
        Integer n =colorCount.get(shortInfo.color);
        if (n == null)
            colorCount.put(shortInfo.color, 1);
        else
            colorCount.put(shortInfo.color, n+1);
        int max = 0;
        RGB catColor = new RGB(0);
        if ( hasName(phyphoxCat)) {
            catColor = (new RGB(0xffffff)).autoLightColor(res);
        } else {
            for (Map.Entry<RGB, Integer> entry :  colorCount.entrySet()) {
                if (entry.getValue() > max) {
                    catColor = entry.getKey();
                    max = entry.getValue();
                }
            }
        }
        categoryHeadline.setBackgroundColor(catColor.intColor());
        categoryHeadline.setTextColor(catColor.overlayTextColor().intColor());
    }
}

class CategoryComparator implements Comparator<ExperimentsInCategory> {
    Resources res;
    CategoryComparator(Resources res){
        this.res = res;
    }

    public int compare(ExperimentsInCategory a, ExperimentsInCategory b) {
        if (a.name.equals(res.getString(R.string.categoryRawSensor)))
            return -1;
        if (b.name.equals(res.getString(R.string.categoryRawSensor)))
            return 1;
        if (a.name.equals(res.getString(R.string.save_state_category)))
            return -1;
        if (b.name.equals(res.getString(R.string.save_state_category)))
            return 1;
        if (a.name.equals(phyphoxCat))
            return 1;
        if (b.name.equals(phyphoxCat))
            return -1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    }
}


//This asyncTask stores the content of a data in a temporary file
//When it's done, it opens it as a single phyphox file
class HandleCopyIntent extends AsyncTask<String, Void, String> {
    private Intent intent; //The intent to read from
    private WeakReference<ExperimentListActivity> parent;
    private File file = null;

    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    HandleCopyIntent(Intent intent, ExperimentListActivity parent) {
        this.intent = intent;
        this.parent = new WeakReference<ExperimentListActivity>(parent);
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
            parent.get().showError(result);
            return;
        }

        if (file == null) {
            parent.get().showError("File is null.");
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
}

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
            if (tempPath.exists())
                FileUtils.deleteDirectory(tempPath);
            if (!tempPath.mkdirs())
                return "Could not create temporary directory to extract zip file.";

            ZipInputStream zis = new ZipInputStream(phyphoxStream.inputStream);

            ZipEntry entry;
            byte[] buffer = new byte[2048];
            while((entry = zis.getNextEntry()) != null) {
                File f = new File(tempPath, entry.getName());
                String canonicalPath = f.getCanonicalPath();
                if (!canonicalPath.startsWith(tempPath.getCanonicalPath())) {
                    return "Security exception: The zip file appears to be tempered with to perform a path traversal attack. Please contact the source of your experiment package or contact the phyphox team for details and help on this issue.";
                }
                if (!(entry.getName().endsWith(".phyphox") || f.getParentFile().getName().equals("res")))
                    continue;
                f.getParentFile().mkdirs();
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
            Log.e("zip", "Error loading zip file.", e);
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


//The BluetoothScanDialog has been written to block execution until a device is found, so we should not run it on the UI thread.
class BluetoothScanner extends AsyncTask<String, Void, BluetoothScanDialog.BluetoothDeviceInfo> {

    public interface BluetoothScanListener {
        void onBluetoothDeviceFound(BluetoothScanDialog.BluetoothDeviceInfo result);
        void onBluetoothScanError(String msg, Boolean isError, Boolean isFatal);
    }

    private final BluetoothScanListener listener;
    private final Resources res;

    private Activity parent;

    Set<String> bluetoothDeviceNameList;
    Set<UUID> bluetoothDeviceUUIDList;

    public BluetoothScanner(Activity parent, Set<String> bluetoothDeviceNameList, Set<UUID> bluetoothDeviceUUIDList,  BluetoothScanListener listener) {
        this.listener = listener;
        this.parent = parent;
        this.res = parent.getResources();
        this.bluetoothDeviceNameList = bluetoothDeviceNameList;
        this.bluetoothDeviceUUIDList = bluetoothDeviceUUIDList;

    }

    //Copying is done on a second thread...
    protected BluetoothScanDialog.BluetoothDeviceInfo doInBackground(String... params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent)) {
            listener.onBluetoothScanError(res.getString(R.string.bt_android_version), true, true);
            return null;
        } else {
           BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !Bluetooth.isEnabled()) {
                listener.onBluetoothScanError(res.getString(R.string.bt_exception_disabled), true, false);
                return null;
            }
            BluetoothScanDialog bsd = new BluetoothScanDialog(false, parent, parent, btAdapter);

            return bsd.getBluetoothDevice(null, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList, null);
        }
    }

    @Override
    protected void onPostExecute(BluetoothScanDialog.BluetoothDeviceInfo result) {
        if (result != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            listener.onBluetoothDeviceFound(result);
    }
}

