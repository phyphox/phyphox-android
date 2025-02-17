package de.rwth_aachen.phyphox.ExperimentList;

import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_ISASSET;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_ISTEMP;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_XML;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.PREFS_NAME;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.phyphoxCatHintRelease;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import de.rwth_aachen.phyphox.Bluetooth.BluetoothExperimentLoader;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList.handler.BluetoothScanner;
import de.rwth_aachen.phyphox.ExperimentList.handler.CategoryComparator;
import de.rwth_aachen.phyphox.ExperimentList.handler.HandleCopyIntent;
import de.rwth_aachen.phyphox.ExperimentList.handler.HandleZipIntent;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentLoadInfoData;
import de.rwth_aachen.phyphox.ExperimentList.datasource.ExperimentRepository;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;
import de.rwth_aachen.phyphox.PhyphoxFile;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SensorInput;
import de.rwth_aachen.phyphox.SettingsActivity.SettingsActivity;
import de.rwth_aachen.phyphox.SettingsActivity.SettingsFragment;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

public class ExperimentListActivity extends AppCompatActivity {

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
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(lp);

        popupWindow = new PopupWindow(hintView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (Build.VERSION.SDK_INT >= 21) {
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

        ReportingScrollView sv = ((ReportingScrollView) findViewById(R.id.experimentScroller));
        sv.setOnScrollChangedListener(new ReportingScrollView.OnScrollChangedListener() {
            @Override
            public void onScrollChanged(ReportingScrollView scrollView, int x, int y, int oldx, int oldy) {
                int bottom = scrollView.getChildAt(scrollView.getChildCount() - 1).getBottom();
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


    public void showError(String error) {
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
                //Load experiments from local files
                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, tempPath.toURI().relativize(file.toURI()).getPath(), "temp_zip", false);
                        ExperimentShortInfo shortInfo = experimentRepository.getAssetExperimentLoader().loadExperimentShortInfo(data);
                        if (shortInfo != null) {
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

                ((TextView) view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                LinearLayout catList = (LinearLayout) view.findViewById(R.id.open_multiple_dialog_list);

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

    public void handleIntent(Intent intent) {


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
        final FloatingActionButton newExperimentBluetooth = (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
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
        final FloatingActionButton newExperimentBluetooth = (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
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
                long crc32 = (((long) (data[7] & 0xff) << 24) | ((long) (data[8] & 0xff) << 16) | ((long) (data[9] & 0xff) << 8) | ((long) (data[10] & 0xff)));
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
                    byte[] dataReceived = new byte[totalSize];
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

                    byte[] zipData = Helper.inflatePartialZip(dataReceived);

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
                            Intent intent = new Intent(parentActivity, SettingsActivity.class);
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

                            if (Helper.isDarkTheme(res)) {
                                sb.append(" <font color='white'");
                            } else {
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
                            ContextThemeWrapper ctw = new ContextThemeWrapper(ExperimentListActivity.this, R.style.Theme_Phyphox_DayNight);
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
        final View experimentListDimmer = findViewById(R.id.experimentListDimmer);
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

            Set<String> bluetoothNameKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceNameList().keySet();
            Set<UUID> bluetoothUUIDKeySet = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceUUIDList().keySet();

            new BluetoothScanner(parentActivity, bluetoothNameKeySet, bluetoothUUIDKeySet, new BluetoothScanner.BluetoothScanListener() {
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

        ExperimentListEnvironment environment = new ExperimentListEnvironment(getAssets(), getResources(), parentActivity.getApplicationContext(), parentActivity);

        experimentRepository = new ExperimentRepository(environment);

        handleIntent(getIntent());

    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission")
    //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        final HashMap<String, Vector<String>> mBluetoothDeviceNameList = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceNameList();
        final HashMap<UUID, Vector<String>> mBluetoothDeviceUUIDList = experimentRepository.getAssetExperimentLoader().getBluetoothDeviceUUIDList();

        final ExperimentListActivity parent = this;
        Set<String> experiments = new HashSet<>();
        if (device.getName() != null) {
            for (String name : mBluetoothDeviceNameList.keySet()) {
                if (device.getName().contains(name)) {
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

        ((TextView) view.findViewById(R.id.open_multiple_dialog_instructions)).setText(instructions);

        dialog.setTitle(parent.getResources().getString(R.string.open_bluetooth_assets_title));

        Vector<ExperimentsInCategory> bluetoothExperiments = new Vector<>();
        AssetManager assetManager = parent.getAssets();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/bluetooth/" + file);
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, "bluetooth/" + file, "bluetooth", true);
                ExperimentShortInfo shortInfo = experimentRepository.getAssetExperimentLoader().loadExperimentShortInfo(data);
                if (shortInfo != null) {
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

                    new BluetoothScanner(parent, bluetoothNameKeySet, bluetoothUUIDKeySet, new BluetoothScanner.BluetoothScanListener() {
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
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.Theme_Phyphox_DayNight);
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
                    output.write(("<title>" + title.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("&", "&amp;") + "</title>").getBytes());
                    output.write(("<category>" + res.getString(R.string.categoryNewExperiment) + "</category>").getBytes());
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
                    intent.putExtra(EXPERIMENT_XML, file);
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

