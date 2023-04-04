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
import androidx.annotation.Nullable;
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
import de.rwth_aachen.phyphox.Experiments.CommonMethods;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.PhyphoxSharedPreference;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;

//ExperimentList implements the activity which lists all experiments to the user. This is the start
//activity for this app if it is launched without an intent.

public class ExperimentList {/* extends AppCompatActivity {

     //Change this to reactivate the phyphox support category hint on the next update. We set it to the version in which it is supposed to be re-enabled, so we can easily understand its meaning.

    //A resource reference for easy access
    private Resources res;

    //ProgressDialog progress = null;

    BluetoothExperimentLoader bluetoothExperimentLoader = null;


    private Vector<ExperimentsInCategory> categories = new Vector<>(); //The list of categories. The ExperimentsInCategory class (see below) holds a ExperimentsInCategory and all its experiment items








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

*/



}
