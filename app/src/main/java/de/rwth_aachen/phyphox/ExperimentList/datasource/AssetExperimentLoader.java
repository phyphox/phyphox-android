package de.rwth_aachen.phyphox.ExperimentList.datasource;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import androidx.collection.ArraySet;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentLoadInfoData;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.Helper.baseColorDrawable.BaseColorDrawable;
import de.rwth_aachen.phyphox.Helper.baseColorDrawable.BitmapIcon;
import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.GpsInput;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SensorInput;
import de.rwth_aachen.phyphox.Helper.baseColorDrawable.TextIcon;
import de.rwth_aachen.phyphox.Helper.baseColorDrawable.VectorIcon;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

public class AssetExperimentLoader {
    ExperimentListEnvironment environment;

    private final ExperimentRepository repository;

    Vector<ExperimentsInCategory> categories;

    private final HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    private final HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>();  //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices


    public AssetExperimentLoader(ExperimentListEnvironment environment, ExperimentRepository repository) {
        this.environment = environment;
        this.repository = repository;
        categories = new Vector<>();
    }

    public void setCategories(Vector<ExperimentsInCategory> categories) {
        if (!new HashSet<>(this.categories).containsAll(categories)) {
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
    public void addExperiment(ExperimentShortInfo shortInfo, String cat) {
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
        ExperimentShortInfo shortInfo = new ExperimentShortInfo();
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

    public void showCurrentCameraAvaibility() {
        //We want to show current availability of experiments requiring cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cm = (CameraManager) environment.context.getSystemService(Context.CAMERA_SERVICE);
            CameraHelper.updateCameraList(cm);
        }
    }

    //Minimalistic loading function. This only retrieves the data necessary to list the experiment.
    public ExperimentShortInfo loadExperimentShortInfo(ExperimentLoadInfoData data) {
        //Class to hold results of the few items we care about
        ExperimentShortInfo shortInfo = new ExperimentShortInfo();

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
                ExperimentShortInfo shortInfo = loadExperimentShortInfo(data);
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
                ExperimentShortInfo shortInfo = loadExperimentShortInfo(data);
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
                ExperimentShortInfo shortInfo = loadExperimentShortInfo(data);
                if (shortInfo != null) {
                    addExperiment(shortInfo, shortInfo.categoryName);
                }
            }
        } catch (IOException e) {
            Toast.makeText(environment.context, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }


    }

}
