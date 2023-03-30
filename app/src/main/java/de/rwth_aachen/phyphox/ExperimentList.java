package de.rwth_aachen.phyphox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
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
import android.graphics.PorterDuff;
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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import de.rwth_aachen.phyphox.Camera.CameraHelper;
import de.rwth_aachen.phyphox.Camera.DepthInput;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.PhyphoxSharedPreference;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;

//ExperimentList implements the activity which lists all experiments to the user. This is the start
//activity for this app if it is launched without an intent.

public class ExperimentList extends AppCompatActivity {

     //Change this to reactivate the phyphox support category hint on the next update. We set it to the version in which it is supposed to be re-enabled, so we can easily understand its meaning.

    //A resource reference for easy access
    private Resources res;

    ProgressDialog progress = null;

    BluetoothExperimentLoader bluetoothExperimentLoader = null;
    long currentQRcrc32 = -1;
    int currentQRsize = -1;
    byte[][] currentQRdataPackets = null;

    boolean newExperimentDialogOpen = false;

    private Vector<ExperimentsInCategory> categories = new Vector<>(); //The list of categories. The ExperimentsInCategory class (see below) holds a ExperimentsInCategory and all its experiment items
    private HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    private HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>(); //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices

    PopupWindow popupWindow = null;

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
            if (!getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).versionName.equals(phyphoxCatHintRelease))
                return;
        } catch (Exception e) {
            return;
        }


        String lastSupportHint = PhyphoxSharedPreference.getLastSupportHint(this);
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

    //This adapter is used to fill the gridView of the categories in the experiment list.
    //So, this can be considered to be the experiment entries within an category




    private void addInvalidExperiment(String xmlFile, String message, String isTemp, boolean isAsset, Vector<ExperimentsInCategory> categories) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e("list:loadExperiment", message);
        if (categories != null)
            addExperiment(xmlFile, getString(R.string.unknown), 0xffff0000, new TextIcon("!", this), message, xmlFile, isTemp, isAsset, -1, null, categories);
    }

    //Minimalistic loading function. This only retrieves the data necessary to list the experiment.
    private void loadExperimentInfo(InputStream input, String experimentXML, String isTemp, boolean isAsset, Vector<ExperimentsInCategory> categories, HashMap<String, Vector<String>> bluetoothDeviceNameList, HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList) {
        XmlPullParser xpp;
        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            //Prepare the PullParser
            xpp = Xml.newPullParser();
            xpp.setInput(input, "UTF-8");
        } catch (XmlPullParserException e) {
            Toast.makeText(this, "Cannot open " + experimentXML + ".", Toast.LENGTH_LONG).show();
            return;
        }

        //Strings to hold results of the few items we care about
        String title = ""; //Experiment title
        String stateTitle = ""; //A title given by the user for a saved experiment state
        String category = ""; //Experiment category
        int color = getResources().getColor(R.color.phyphox_primary); //Icon base color
        boolean customColor = false;
        String icon = ""; //Experiment icon (just the raw data as defined in the experiment file. Will be interpreted below)
        String description = ""; //First line of the experiment's descriptions as a short info
        BaseColorDrawable image = null; //This will hold the icon
        BaseColorDrawable imageForContributionHeadline = null; //This will hold the icon for Contribution for light theme

        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            int eventType = xpp.getEventType(); //should be START_DOCUMENT
            int phyphoxDepth = -1; //Depth of the first phyphox tag (We only care for title, icon, description and category directly below the phyphox tag)
            int translationBlockDepth = -1; //Depth of the translations block
            int translationDepth = -1; //Depth of a suitable translation, if found.

            //This part is used to check sensor availability before launching the experiment
            SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...
            boolean inInput = false;
            boolean inOutput = false;
            Integer unavailableSensor = -1;
            boolean isLink = false;
            String link = null;

            int languageRating = 0; //If we find a locale, it replaces previous translations as long as it has a higher rating than the previous one.
            while (eventType != XmlPullParser.END_DOCUMENT){ //Go through all tags until the end...
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
                                    int thisLaguageRating = Helper.getLanguageRating(res, globalLocale);
                                    if (thisLaguageRating > languageRating)
                                        languageRating = thisLaguageRating;
                                }
                                break;
                            case "translations": //The translations block may contain a localized title and description
                                if (xpp.getDepth() != phyphoxDepth+1) //Translations block has to be immediately below phyphox tag
                                    break;
                                if (translationBlockDepth < 0) {
                                    translationBlockDepth = xpp.getDepth(); //Remember depth of the block
                                }
                                break;
                            case "translation": //The translation block may contain our localized version
                                if (xpp.getDepth() != translationBlockDepth+1) //The translation has to be immediately below he translations block
                                    break;
                                String thisLocale = xpp.getAttributeValue(null, "locale");
                                int thisLaguageRating = Helper.getLanguageRating(res, thisLocale);
                                if (translationDepth < 0 && thisLaguageRating > languageRating) {
                                    languageRating = thisLaguageRating;
                                    translationDepth = xpp.getDepth(); //Remember depth of the translation block
                                }
                                break;
                            case "title": //This should give us the experiment title
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    title = xpp.nextText().trim();
                                break;
                            case "state-title":
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    stateTitle = xpp.nextText().trim();
                                break;
                            case "icon": //This should give us the experiment icon (might be an acronym or a base64-encoded image)
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) { //May be in phyphox root or from a valid translation
                                    if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("base64")) { //Check the icon type
                                        //base64 encoded image. Decode it
                                        icon = xpp.nextText().trim();
                                        try {
                                            Bitmap bitmap = Helper.decodeBase64(icon);
                                            // This bitmap will be used for the icon used in contribution headline
                                            if(bitmap != null) {
                                                image = new BitmapIcon(bitmap, this);
                                                Bitmap bitmapDiffColor = Helper.changeColorOf(this, bitmap, R.color.phyphox_white_100);
                                                if(bitmapDiffColor != null)
                                                    imageForContributionHeadline = new BitmapIcon(bitmapDiffColor, this);
                                            }

                                        } catch (IllegalArgumentException e) {
                                            Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                        }
                                    } else if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("svg")) { //Check the icon type
                                        //SVG image. Handle it with AndroidSVG
                                        icon = xpp.nextText().trim();
                                        try {
                                            SVG svg = SVG.getFromString(icon);
                                            image = new VectorIcon(svg, this);
                                        } catch (SVGParseException e) {
                                            Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                        }
                                    } else {
                                        //Just a string. Create an icon from it. We allow a maximum of three characters.
                                        icon = xpp.nextText().trim();
                                        if (icon.length() > 3)
                                            icon = icon.substring(0,3);
                                        image = new TextIcon(icon, this);
                                    }

                                }
                                break;
                            case "description": //This should give us the experiment description, but we only need the first line
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    description = xpp.nextText().trim().split("\n", 2)[0]; //Remove any whitespaces and take the first line until the first line break
                                break;
                            case "category": //This should give us the experiment category
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    category = xpp.nextText().trim();
                                break;
                            case "link": //This should give us a link if the experiment is only a dummy entry with a link
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    link = xpp.nextText().trim();
                                break;
                            case "color": //This is the base color for design decisions (icon background color and category color)
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) { //May be in phyphox root or from a valid translation
                                    customColor = true;
                                    color = Helper.parseColor(xpp.nextText().trim(), getResources().getColor(R.color.phyphox_primary), getResources());
                                }
                                break;
                            case "input": //We just have to check if there are any sensors, which are not supported on this device
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inInput = true;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inOutput = true;
                                break;
                            case "sensor":
                                if (!inInput || unavailableSensor >= 0)
                                    break;
                                String type = xpp.getAttributeValue(null, "type");
                                String ignoreUnavailableStr = xpp.getAttributeValue(null, "ignoreUnavailable");
                                boolean ignoreUnavailable = (ignoreUnavailableStr != null && Boolean.valueOf(ignoreUnavailableStr));
                                SensorInput testSensor;
                                try {
                                    testSensor = new SensorInput(type, ignoreUnavailable,0, SensorInput.SensorRateStrategy.auto, 0, false, null, null, null);
                                    testSensor.attachSensorManager(sensorManager);
                                } catch (SensorInput.SensorException e) {
                                    unavailableSensor = SensorInput.getDescriptionRes(SensorInput.resolveSensorString(type));
                                    break;
                                }
                                if (!(testSensor.isAvailable() || testSensor.ignoreUnavailable)) {
                                    unavailableSensor = SensorInput.getDescriptionRes(SensorInput.resolveSensorString(type));
                                }
                                break;
                            case "location":
                                if (!inInput || unavailableSensor >= 0)
                                    break;
                                if (!GpsInput.isAvailable(this)) {
                                    unavailableSensor = R.string.location;
                                }
                                break;
                            case "depth":
                                if (!inInput || unavailableSensor >= 0)
                                    break;
                                if (!DepthInput.isAvailable()) {
                                    unavailableSensor = R.string.sensorDepth;
                                }
                                break;
                            case "bluetooth":
                                if ((!inInput && !inOutput) || unavailableSensor >= 0) {
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
                                        bluetoothDeviceNameList.get(name).add(experimentXML);
                                    }
                                }
                                if (uuid != null) {
                                    if (bluetoothDeviceUUIDList != null) {
                                        if (!bluetoothDeviceUUIDList.containsKey(uuid))
                                            bluetoothDeviceUUIDList.put(uuid, new Vector<String>());
                                        bluetoothDeviceUUIDList.get(uuid).add(experimentXML);
                                    }
                                }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                    unavailableSensor = R.string.bluetooth;
                                } else if (!Bluetooth.isSupported(this)) {
                                    unavailableSensor = R.string.bluetooth;
                                }
                                if (!customColor)
                                    color = getResources().getColor(R.color.phyphox_blue_100);
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
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inInput = false;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inOutput = false;
                                break;
                        }
                        break;

                }
                eventType = xpp.next(); //Next event in the file...
            }

            //Sanity check: We need a title!
            if (title.equals("")) {
                addInvalidExperiment(experimentXML,  "Invalid: \" + experimentXML + \" misses a title.", isTemp, isAsset, categories);
                return;
            }

            //Sanity check: We need a category!
            if (category.equals("")) {
                addInvalidExperiment(experimentXML,  "Invalid: \" + experimentXML + \" misses a category.", isTemp, isAsset, categories);
                return;
            }

            if (!stateTitle.equals("")) {
                description = title;
                title = stateTitle;
                category = getString(R.string.save_state_category);
            }

            //Let's check the icon
            if (image == null) //No icon given. Create a TextIcon from the first three characters of the title
                image = new TextIcon(title.substring(0, Math.min(title.length(), 3)), this);



            //We have all the information. Add the experiment.
            BaseColorDrawable mImage;
            if (categories != null){
                // Following condition is for setting the proper image and its color in the Contribution Headline
                if(category.equals("phyphox.org") && !Helper.isDarkTheme(getResources())){
                    mImage = imageForContributionHeadline;
                    mImage.setColorFilter( 0xff000000, PorterDuff.Mode.MULTIPLY );
                } else {
                    mImage = image;
                    mImage.setBaseColor(color);
                }
                addExperiment(title, category, color, mImage, isLink ? "Link: " + link : description, experimentXML, isTemp, isAsset, unavailableSensor, (isLink ? link : null), categories);
            }


        } catch (XmlPullParserException e) { //XML Pull Parser is unhappy... Abort and notify user.
            addInvalidExperiment(experimentXML,  "Error loading " + experimentXML + " (XML Exception)", isTemp, isAsset, categories);
        } catch (IOException e) { //IOException... Abort and notify user.
            addInvalidExperiment(experimentXML,  "Error loading " + experimentXML + " (IOException)", isTemp, isAsset, categories);
        }
    }

    //Load all experiments from assets and from local files
    private void loadExperimentList() {

        //We want to show current availability of experiments requiring cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraHelper.updateCameraList(cm);
        }

        //Save scroll position to restore this later
        ScrollView sv = findViewById(R.id.experimentScroller);
        int scrollY = sv.getScrollY();

        //Clear the old list first
        categories.clear();
        bluetoothDeviceNameList.clear();
        bluetoothDeviceUUIDList.clear();
        LinearLayout catList = findViewById(R.id.experimentList);
        catList.removeAllViews();

        //Load experiments from local files
        try {
            //Get all files that end on ".phyphox"
            File[] files = getFilesDir().listFiles((dir, filename) -> filename.endsWith(".phyphox"));

            for (File file : files) {
                if (file.isDirectory())
                    continue;
                //Load details for each experiment
                InputStream input = openFileInput(file.getName());
                loadExperimentInfo(input, file.getName(), null, false, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();
        }

        //Load experiments from assets
        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments"); //All experiments are placed in the experiments folder
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                if (!experimentXML.endsWith(".phyphox"))
                    continue;
                InputStream input = assetManager.open("experiments/" + experimentXML);
                loadExperimentInfo(input, experimentXML, null,true, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list. " + e, Toast.LENGTH_LONG).show();
        }

        Collections.sort(categories, new categoryComparator());

        for (ExperimentsInCategory cat : categories) {
            cat.addToParent(catList);
        }

        //Load hidden bluetooth experiments - these are not shown but will be offered if a matching Bluetooth device is found during a scan
        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments/bluetooth");
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                InputStream input = assetManager.open("experiments/bluetooth/" + experimentXML);
                loadExperimentInfo(input, experimentXML, null,true, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

        sv.scrollTo(0, scrollY);
    }

    @Override
    //If we return to this activity we want to reload the experiment list as other activities may
    //have changed it
    protected void onResume() {
        super.onResume();
        loadExperimentList();
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
            final File[] files = tempPath.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".phyphox");
                }
            });
            if (files.length == 0) {
                Toast.makeText(this, "Error: There is no valid phyphox experiment in this zip file.", Toast.LENGTH_LONG).show();
            } else if (files.length == 1) {
                //Create an intent for this file
                Intent intent = new Intent(this, Experiment.class);
                intent.setData(Uri.fromFile(files[0]));
                if (preselectedDevice != null)
                    intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, preselectedDevice.getAddress());
                intent.setAction(Intent.ACTION_VIEW);

                //Open the file
                startActivity(intent);
            } else {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.open_multipe_dialog, null);
                final Activity parent = this;
                builder.setView(view)
                        .setPositiveButton(R.string.open_save_all, (dialog, id) -> {
                            for (File file : files) {
                                if (!Helper.experimentInCollection(file, parent))
                                    file.renameTo(new File(getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                            }
                            loadExperimentList();
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
                AlertDialog dialog = builder.create();

                ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

                dialog.setTitle(getResources().getString(R.string.open_zip_title));

                Vector<ExperimentsInCategory> zipExperiments = new Vector<>();

                //Load experiments from local files
                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        loadExperimentInfo(input, file.getName(), "temp_zip", false, zipExperiments, null, null);
                        input.close();
                    } catch (IOException e) {
                        Log.e("zip", e.getMessage());
                        Toast.makeText(this, "Error: Could not load experiment \"" + file + "\" from zip file.", Toast.LENGTH_LONG).show();
                    }
                }

                Collections.sort(zipExperiments, new categoryComparator());

                for (ExperimentsInCategory cat : zipExperiments) {
                    if (preselectedDevice != null)
                        cat.setPreselectedBluetoothAddress(preselectedDevice.getAddress());
                    cat.addToParent(catList);
                }

                dialog.show();
            }
        } else {
            Toast.makeText(ExperimentList.this, result, Toast.LENGTH_LONG).show();
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void loadExperimentFromBluetoothDevice(final BluetoothDevice device) {
        final ExperimentList parent = this;
        if (bluetoothExperimentLoader == null) {
            bluetoothExperimentLoader = new BluetoothExperimentLoader(getBaseContext(), new BluetoothExperimentLoader.BluetoothExperimentLoaderCallback() {
                @Override
                public void updateProgress(int transferred, int total) {
                    if (total > 0) {
                        if (progress.isIndeterminate()) {
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
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
                                }
                            });
                        } else {
                            progress.setProgress(transferred);
                        }
                    }
                }

                @Override
                public void dismiss() {
                    progress.dismiss();
                }

                @Override
                public void error(String msg) {
                    showBluetoothExperimentReadError(msg, device);
                }

                @Override
                public void success(Uri experimentUri, boolean isZip) {
                    Intent intent = new Intent(parent, Experiment.class);
                    intent.setData(experimentUri);
                    intent.setAction(Intent.ACTION_VIEW);
                    if (isZip) {
                        new ExperimentList.handleZipIntent(intent, parent, device).execute();
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission") //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        Set<String> experiments = new HashSet<>();
        if (device.getName() != null) {
            for (String name : bluetoothDeviceNameList.keySet()) {
                if (device.getName().contains(name))
                    experiments.addAll(bluetoothDeviceNameList.get(name));
            }
        }

        for (UUID uuid : uuids) {
            Vector<String> experimentsForUUID = bluetoothDeviceUUIDList.get(uuid);
            if (experimentsForUUID != null)
                experiments.addAll(experimentsForUUID);
        }
        final Set<String> supportedExperiments = experiments;

        if (supportedExperiments.isEmpty() && phyphoxService) {
            //We do not have any experiments for this device, so there is no choice. Just load the experiment provided by the device.
            loadExperimentFromBluetoothDevice(device);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.open_multipe_dialog, null);
        builder.setView(view);
        final Activity parent = this;
        if (!supportedExperiments.isEmpty()) {
            builder.setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AssetManager assetManager = getAssets();
                    try {
                        for (String file : supportedExperiments) {
                            InputStream in = assetManager.open("experiments/bluetooth/" + file);
                            OutputStream out = new FileOutputStream(new File(getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
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
                        Toast.makeText(ExperimentList.this, "Error: Could not retrieve assets.", Toast.LENGTH_LONG).show();
                    }

                    loadExperimentList();
                    dialog.dismiss();
                }

                });
        }
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }

            });

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

        LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

        dialog.setTitle(getResources().getString(R.string.open_bluetooth_assets_title));

        //Load experiments from assets
        AssetManager assetManager = getAssets();
        Vector<ExperimentsInCategory> bluetoothExperiments = new Vector<>();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/bluetooth/"+file);
                loadExperimentInfo(input, "bluetooth/"+file, "bluetooth", true, bluetoothExperiments, null, null);
                input.close();
            } catch (IOException e) {
                Log.e("bluetooth", e.getMessage());
                Toast.makeText(this, "Error: Could not load experiment \"" + file + "\" from asset.", Toast.LENGTH_LONG).show();
            }
        }

        Collections.sort(bluetoothExperiments, new categoryComparator());

        for (ExperimentsInCategory cat : bluetoothExperiments) {
            cat.setPreselectedBluetoothAddress(device.getAddress());
            cat.addToParent(catList);
        }

        dialog.show();
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
                    Toast.makeText(ExperimentList.this, "Unexpected error: Could not retrieve data from QR code.", Toast.LENGTH_LONG).show();
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
                    new handleZipIntent(zipIntent, this).execute();
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


        //Basics. Call super-constructor and inflate the layout.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_list);

        res = getResources(); //Get Resource reference for easy access.

        if (!displayDoNotDamageYourPhone()) { //Show the do-not-damage-your-phone-warning
            showSupportHintIfRequired();
        }

        Activity parentActivity = this;

        //Set the on-click-listener for the credits
        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Context wrapper = new ContextThemeWrapper(ExperimentList.this, R.style.Theme_Phyphox_DayNight);
                PopupMenu popup = new PopupMenu(wrapper, v);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_credits) {
                            //Create the credits as an AlertDialog
                            ContextThemeWrapper ctw = new ContextThemeWrapper(ExperimentList.this, R.style.rwth);
                            AlertDialog.Builder credits = new AlertDialog.Builder(ctw);
                            LayoutInflater creditsInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
                            View creditLayout = creditsInflater.inflate(R.layout.credits, null);




                            tv.setText(creditsNamesSpannable);


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

                            return true;
                        } else if (item.getItemId() == R.id.action_settings) {
                                Intent intent = new Intent(parentActivity, Settings.class);
                                startActivity(intent);
                                return true;
                            } else if (item.getItemId() == R.id.action_deviceInfo) {


                            final Spanned text = Html.fromHtml(sb.toString());
                            ContextThemeWrapper ctw = new ContextThemeWrapper( ExperimentList.this, R.style.Theme_Phyphox_DayNight);
                            AlertDialog.Builder builder = new AlertDialog.Builder(ctw);
                            builder.setMessage(text)
                                    .setTitle(R.string.deviceInfo)
                                    .setPositiveButton(R.string.copyToClipboard, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Copy the device info to the clipboard and notify the user

                                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                            ClipData data = ClipData.newPlainText(res.getString(R.string.deviceInfo), text);
                                            cm.setPrimaryClip(data);

                                            Toast.makeText(ExperimentList.this, res.getString(R.string.deviceInfoCopied), Toast.LENGTH_SHORT).show();
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
        final ExperimentList thisRef = this; //Context needs to be accessed in the onClickListener


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
        boolean skipWarning = settings.getBoolean("skipWarning", false);
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
                    Toast.makeText(ExperimentList.this, "Invaid sensor rate. Fall back to fastest rate.", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(ExperimentList.this, "No sensor selected. Adding accelerometer as default.", Toast.LENGTH_LONG).show();
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
