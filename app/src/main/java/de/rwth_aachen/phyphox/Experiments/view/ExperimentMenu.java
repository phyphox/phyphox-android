package de.rwth_aachen.phyphox.Experiments.view;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.content.Context.SENSOR_SERVICE;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.rwth_aachen.phyphox.Camera.CameraHelper;
import de.rwth_aachen.phyphox.Camera.DepthInput;
import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.PhyphoxAlertBuilder;
import de.rwth_aachen.phyphox.PhyphoxFile;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SensorInput;
import de.rwth_aachen.phyphox.Settings;

public class ExperimentMenu extends PopupMenu {
    Context base;

    public ExperimentMenu(Context base, View v){
        super(base, v);
        this.base = base;

        this.setOnMenuItemClickListener((OnMenuItemClickListener) item -> {
            if(item.getItemId() == R.id.action_privacy){
                openLink(base.getString(R.string.privacyPolicyURL));
                return true;
            }
            if(item.getItemId() == R.id.action_credits){
                createCreditDialog();
                return true;
            }
            if(item.getItemId() == R.id.action_helpExperiments){
                openLink(base.getString(R.string.experimentsPhyphoxOrgURL));
                return true;
            }
            if(item.getItemId() == R.id.action_helpFAQ){
                openLink(base.getString(R.string.faqPhyphoxOrgURL));
                return true;
            }
            if(item.getItemId() == R.id.action_helpRemote){
                openLink(base.getString(R.string.remotePhyphoxOrgURL));
                return true;
            }
            if(item.getItemId() == R.id.action_settings){
                Intent intent = new Intent(base, Settings.class);
                base.startActivity(intent);
                return true;
            }
            if(item.getItemId() == R.id.action_deviceInfo){
                showDeviceInfoDialog();
                return true;
            }
            return false;
        });
    }


    private void showDeviceInfoDialog() {
        final Spanned text = Html.fromHtml(getDeviceInfoString().toString());
        ContextThemeWrapper ctw = new ContextThemeWrapper( base, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctw);
        builder.setMessage(text)
                .setTitle(R.string.deviceInfo)
                .setPositiveButton(R.string.copyToClipboard, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //Copy the device info to the clipboard and notify the user

                        ClipboardManager cm = (ClipboardManager) base.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData data = ClipData.newPlainText(base.getString(R.string.deviceInfo), text);
                        cm.setPrimaryClip(data);

                        Toast.makeText(base, base.getString(R.string.deviceInfoCopied), Toast.LENGTH_SHORT).show();
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


    private void openLink(String uriString) {
        Uri uri = Uri.parse(uriString);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (intent.resolveActivity(base.getPackageManager()) != null) {
            base.startActivity(intent);
        }
    }

    private void createCreditDialog() {

        //Create the credits as an AlertDialog
        ContextThemeWrapper ctw = new ContextThemeWrapper(base, R.style.rwth);
        AlertDialog.Builder credits = new AlertDialog.Builder(ctw);
        LayoutInflater creditsInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View creditLayout = creditsInflater.inflate(R.layout.credits, null);

        //Set the credit texts, which require HTML markup
        TextView tv = (TextView) creditLayout.findViewById(R.id.creditNames);

        SpannableStringBuilder creditsNamesSpannable = new SpannableStringBuilder();
        boolean first = true;
        for (String line : base.getString(R.string.creditsNames).split("\\n")) {
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
        tvA.setText(Html.fromHtml(base.getString(R.string.creditsApache)));
        TextView tvB = (TextView) creditLayout.findViewById(R.id.creditsZxing);
        tvB.setText(Html.fromHtml(base.getString(R.string.creditsZxing)));
        TextView tvC = (TextView) creditLayout.findViewById(R.id.creditsPahoMQTT);
        tvC.setText(Html.fromHtml(base.getString(R.string.creditsPahoMQTT)));

        //Finish alertDialog builder
        credits.setView(creditLayout);
        credits.setPositiveButton(base.getText(R.string.close), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //Nothing to do. Just close the thing.
            }
        });

        //Present the dialog
        credits.show();
    }


    private SpannableStringBuilder buildSpannableString(){
        SpannableStringBuilder creditsNamesSpannable = new SpannableStringBuilder();
        boolean first = true;
        for (String line : base.getString(R.string.creditsNames).split("\\n")) {
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
        return creditsNamesSpannable;
    }

    private StringBuilder getDeviceInfoString(){
        StringBuilder sb = new StringBuilder();

        PackageInfo pInfo;
        try {
            pInfo = base.getPackageManager().getPackageInfo(base.getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (Exception e) {
            pInfo = null;
        }

        if(Helper.isDarkTheme(base.getResources())){
            sb.append(" <font color='white'");
        }else{
            sb.append(" <font color='black'");
        }

        sb.append("<b>phyphox</b><br />");
        if (pInfo != null) {
            sb.append("Version: ").append(pInfo.versionName).append("<br />").append("Build: ").append(pInfo.versionCode).append("<br />");
        } else {
            sb.append("Version: Unknown<br />").append("Build: Unknown<br />");
        }
        sb.append("File format: ").append(PhyphoxFile.phyphoxFileVersion).append("<br /><br />");

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
        SensorManager sensorManager = (SensorManager) base.getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) {
            sb.append("Unkown<br />");
        } else {
            for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                sb.append("<b>");
                sb.append(base.getString(SensorInput.getDescriptionRes(sensor.getType())));
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

        return sb;
    }


}
