package de.rwth_aachen.phyphox.ExperimentList;

import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_ISTEMP;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.PREFS_NAME;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.phyphoxCatHintRelease;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.content.res.Configuration;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import de.rwth_aachen.phyphox.Bluetooth.BluetoothExperimentLoader;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList.datasource.AssetExperimentLoader;
import de.rwth_aachen.phyphox.ExperimentList.handler.BluetoothScanner;
import de.rwth_aachen.phyphox.ExperimentList.handler.CopyIntentHandler;
import de.rwth_aachen.phyphox.ExperimentList.handler.ZipIntentHandler;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentLoadInfoData;
import de.rwth_aachen.phyphox.ExperimentList.datasource.ExperimentRepository;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.ExperimentList.handler.SimpleExperimentCreator;
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

    PopupWindow popupWindow = null;

    private ExperimentRepository experimentRepository;

    ImageView creditsV;
    FloatingActionButton newExperimentButton, newExperimentBluetooth, newExperimentQR, newExperimentSimple;
    TextView newExperimentBluetoothLabel, newExperimentSimpleLabel, newExperimentQRLabel;
    ReportingScrollView sv;
    View backgroundDimmer;

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

        creditsV = findViewById(R.id.credits);
        newExperimentButton = findViewById(R.id.newExperiment);

        newExperimentSimple = findViewById(R.id.newExperimentSimple);
        newExperimentSimpleLabel = findViewById(R.id.newExperimentSimpleLabel);
        newExperimentBluetooth = findViewById(R.id.newExperimentBluetooth);
        newExperimentQR = findViewById(R.id.newExperimentQR);
        newExperimentBluetoothLabel = findViewById(R.id.newExperimentBluetoothLabel);
        newExperimentQRLabel = findViewById(R.id.newExperimentQRLabel);
        backgroundDimmer = findViewById(R.id.experimentListDimmer);

        if (!displayDoNotDamageYourPhone()) { //Show the do-not-damage-your-phone-warning
            showSupportHintIfRequired();
        }

        Helper.WindowInsetHelper.setWindowInsets(findViewById(R.id.experimentScroller), this, true, false);
        Helper.WindowInsetHelper.setToolbarWindowInset(findViewById(R.id.expListHeader), this);
        Helper.WindowInsetHelper.setWindowInsets(findViewById(R.id.newExperiment), this, true, false);

        setUpOnClickListener();

        experimentRepository = new ExperimentRepository();

        handleIntent(getIntent());

    }



    @Override
    //If we return to this activity we want to reload the experiment list as other activities may
    //have changed it
    protected void onResume() {
        super.onResume();
        experimentRepository.loadAndShowMainExperimentList(this);
    }

    @Override
    public void onUserInteraction() {
        if (popupWindow != null)
            popupWindow.dismiss();
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

    private void setUpOnClickListener(){

        View.OnClickListener ocl = this::showPopupMenu;

        creditsV.setOnClickListener(ocl);

        Button.OnClickListener neocl = v -> {
            if (newExperimentDialogOpen)
                hideNewExperimentDialog();
            else
                showNewExperimentDialog();

        };

        View experimentListDimmer = findViewById(R.id.experimentListDimmer);
        newExperimentButton.setOnClickListener(neocl);
        experimentListDimmer.setOnClickListener(neocl);

        Button.OnClickListener neoclSimple = v -> {
            hideNewExperimentDialog();
            openSimpleExperimentConfigurationDialog(this);
        };

        newExperimentSimple.setOnClickListener(neoclSimple);
        newExperimentSimpleLabel.setOnClickListener(neoclSimple);

        Button.OnClickListener neoclBluetooth = v -> {
            hideNewExperimentDialog();

            Set<String> bluetoothNameKeySet = experimentRepository.getBluetoothDeviceNameList().keySet();
            Set<UUID> bluetoothUUIDKeySet = experimentRepository.getBluetoothDeviceUUIDList().keySet();

            new BluetoothScanner(this, bluetoothNameKeySet, bluetoothUUIDKeySet, new BluetoothScanner.BluetoothScanListener() {
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

        newExperimentBluetooth.setOnClickListener(neoclBluetooth);
        newExperimentBluetoothLabel.setOnClickListener(neoclBluetooth);

        Button.OnClickListener neoclQR = v -> {
            hideNewExperimentDialog();
            scanQRCode();
        };

        newExperimentQR.setOnClickListener(neoclQR);
        newExperimentQRLabel.setOnClickListener(neoclQR);

        sv = findViewById(R.id.experimentScroller);
        sv.setOnScrollChangedListener((scrollView, x, y, oldx, oldy) -> {
            int bottom = scrollView.getChildAt(scrollView.getChildCount() - 1).getBottom();
            if (y + 10 > bottom - scrollView.getHeight()) {
                scrollView.setOnScrollChangedListener(null);
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("lastSupportHint", phyphoxCatHintRelease);
                editor.apply();
            }
        });

    }

    private void showPopupMenu(View v) {
        Context wrapper = new ContextThemeWrapper(ExperimentListActivity.this, R.style.Theme_Phyphox_DayNight);
        PopupMenu popup = new PopupMenu(wrapper, v);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_privacy) {
                openLink(res.getString(R.string.privacyPolicyURL));
                return true;
            }
            else if (item.getItemId() == R.id.action_credits) {
                openCreditDialog();
                return true;
            }
            else if (item.getItemId() == R.id.action_helpExperiments) {
                openLink(res.getString(R.string.experimentsPhyphoxOrgURL));
                return true;
            }
            else if (item.getItemId() == R.id.action_helpFAQ) {
                openLink(res.getString(R.string.faqPhyphoxOrgURL));
                return true;
            }
            else if (item.getItemId() == R.id.action_helpRemote) {
                openLink(res.getString(R.string.remotePhyphoxOrgURL));
                return true;
            }
            else if (item.getItemId() == R.id.action_settings) {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            else if (item.getItemId() == R.id.action_deviceInfo) {
                openDeviceInfoDialog();
                return true;
            }
            else {
                return false;
            }
        });
        popup.inflate(R.menu.menu_help);
        popup.show();
    }

    private void openLink(String URLString) {
        Uri uri = Uri.parse(URLString);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void openDeviceInfoDialog() {
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
                sb.append("- Reporting Mode: ");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    sb.append(sensor.getReportingMode());
                } else {
                    sb.append("API < 21");
                }
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
    }

    private void openCreditDialog() {
        //Create the credits as an AlertDialog
        ContextThemeWrapper ctw = new ContextThemeWrapper(ExperimentListActivity.this, R.style.Theme_Phyphox);
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

        //The following texts are not translateable. Get them from the basic English version.
        Configuration conf = res.getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(Locale.ENGLISH);
        Resources localizedRes = getBaseContext().createConfigurationContext(conf).getResources();

        TextView tvA = (TextView) creditLayout.findViewById(R.id.creditsApache);
        tvA.setText(Html.fromHtml(localizedRes.getString(R.string.creditsApache)));
        TextView tvB = (TextView) creditLayout.findViewById(R.id.creditsZxing);
        tvB.setText(Html.fromHtml(localizedRes.getString(R.string.creditsZxing)));
        TextView tvC = (TextView) creditLayout.findViewById(R.id.creditsPahoMQTT);
        tvC.setText(Html.fromHtml(localizedRes.getString(R.string.creditsPahoMQTT)));

        //Finish alertDialog builder
        credits.setView(creditLayout);
        credits.setPositiveButton(res.getText(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //Nothing to do. Just close the thing.
            }
        });

        //Present the dialog
        credits.show();
    }

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
                ExperimentRepository zipRepository = new ExperimentRepository();
                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, tempPath.toURI().relativize(file.toURI()).getPath(), "temp_zip", false);
                        ExperimentShortInfo shortInfo = AssetExperimentLoader.loadExperimentShortInfo(data, new ExperimentListEnvironment(this));
                        if (shortInfo != null) {
                            zipRepository.addExperiment(shortInfo, this);
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
                builder.setView(view)
                        .setPositiveButton(R.string.open_save_all, (dialog, id) -> {
                            zipRepository.saveExperimentsToMainList(new ExperimentListEnvironment(this));
                            experimentRepository.loadAndShowMainExperimentList(this);
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
                AlertDialog dialog = builder.create();

                ((TextView) view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                LinearLayout catList = (LinearLayout) view.findViewById(R.id.open_multiple_dialog_list);

                dialog.setTitle(getResources().getString(R.string.open_zip_title));

                zipRepository.addExperimentCategoriesToLinearLayout(catList, this.getResources());

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
                        new ZipIntentHandler(intent, parent, device).execute();
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
                new ZipIntentHandler(intent, this).execute();
            }
        } else if (scheme.equals(ContentResolver.SCHEME_CONTENT) || scheme.equals("phyphox") || scheme.equals("http") || scheme.equals("https")) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new CopyIntentHandler(intent, this).execute();
        }
    }

    protected void showNewExperimentDialog() {
        newExperimentDialogOpen = true;

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
                    new ZipIntentHandler(zipIntent, this).execute();
                } else {
                    showQRScanError(res.getString(R.string.newExperimentQRCodesMissing1) + " " + currentQRsize + " " + res.getString(R.string.newExperimentQRCodesMissing2) + " " + missing, false);
                }
            } else {
                //QR code does not contain or reference a phyphox experiment
                showQRScanError(res.getString(R.string.newExperimentQRNoExperiment), true);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission")
    //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        final ExperimentListActivity parent = this;

        ExperimentRepository hiddenBluetoothRepository = new ExperimentRepository();
        hiddenBluetoothRepository.loadHiddenBluetoothExperiments(this);

        Set<String> experiments = new HashSet<>();
        if (device.getName() != null) {
            for (String name : hiddenBluetoothRepository.getBluetoothDeviceNameList().keySet()) {
                if (device.getName().contains(name)) {
                    Vector<String> experimentsForName = hiddenBluetoothRepository.getBluetoothDeviceNameList().get(name);
                    if (experimentsForName != null)
                        experiments.addAll(experimentsForName);
                }
            }
        }

        for (UUID uuid : uuids) {
            Vector<String> experimentsForUUID = hiddenBluetoothRepository.getBluetoothDeviceUUIDList().get(uuid);
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

        ExperimentRepository bleRepository = new ExperimentRepository();

        if (!supportedExperiments.isEmpty()) {
            builder.setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    bleRepository.saveExperimentsToMainList(new ExperimentListEnvironment(parent));
                    experimentRepository.loadAndShowMainExperimentList(parent);
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

        AssetManager assetManager = parent.getAssets();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/" + file);
                ExperimentLoadInfoData data = new ExperimentLoadInfoData(input, file, null, true);
                ExperimentShortInfo shortInfo = AssetExperimentLoader.loadExperimentShortInfo(data, new ExperimentListEnvironment(parent));
                if (shortInfo != null) {
                    bleRepository.addExperiment(shortInfo, this);
                }
                input.close();
            } catch (IOException e) {
                Log.e("ExperimentList", "Error: Could not load experiment \"" + file + "\" from asset.");
                Toast.makeText(parent, "Error: Could not load experiment \"" + file + "\" from asset.", Toast.LENGTH_LONG).show();
            }
        }

        LinearLayout parentLayout = view.findViewById(R.id.open_multiple_dialog_list);
        bleRepository.setPreselectedBluetoothAddress(device.getAddress());
        bleRepository.addExperimentCategoriesToLinearLayout(parentLayout, this.getResources());

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
                    Set<String> bluetoothNameKeySet = experimentRepository.getBluetoothDeviceNameList().keySet();
                    Set<UUID> bluetoothUUIDKeySet = experimentRepository.getBluetoothDeviceUUIDList().keySet();

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
    private void openSimpleExperimentConfigurationDialog(final Context c) {
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder neDialog = new AlertDialog.Builder(ctw);
        LayoutInflater neInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View neLayout = neInflater.inflate(R.layout.new_experiment, null);

        neDialog.setView(neLayout);
        neDialog.setTitle(R.string.newExperiment);
        neDialog.setPositiveButton(res.getText(R.string.ok), (dialog, which) -> {
            //Here we have to create the experiment definition file
            SimpleExperimentCreator creator = new SimpleExperimentCreator(c, neLayout);
            creator.generateAndOpenSimpleExperiment();
        });
        neDialog.setNegativeButton(res.getText(R.string.cancel), (dialog, which) -> {
            //If the user aborts the dialog, we don't have to do anything
        });

        neDialog.show();
    }

}

