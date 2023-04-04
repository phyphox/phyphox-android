package de.rwth_aachen.phyphox.Experiments.data.xml;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.App;
import de.rwth_aachen.phyphox.BaseColorDrawable;
import de.rwth_aachen.phyphox.BitmapIcon;
import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Camera.DepthInput;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.GpsInput;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SensorInput;
import de.rwth_aachen.phyphox.TextIcon;
import de.rwth_aachen.phyphox.VectorIcon;

public class ExperimentInfoXMLParser {

    Context context;
    InputStream input;
    String experimentXML;
    String isTemp;
    boolean isAsset;
    Vector<ExperimentsInCategory> categories;
    HashMap<String, Vector<String>> bluetoothDeviceNameList;
    HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList;

    XmlPullParser xpp;

    private ExperimentDataModel experimentDataModel;

    //Strings to hold results of the few items we care about
    private String title = ""; //Experiment title
    private String stateTitle = ""; //A title given by the user for a saved experiment state
    private String category = ""; //Experiment category
    private int color; //Icon base color
    private boolean customColor = false;
    private String icon = ""; //Experiment icon (just the raw data as defined in the experiment file. Will be interpreted below)
    private String description = ""; //First line of the experiment's descriptions as a short info
    private BaseColorDrawable image = null; //This will hold the icon
    private BaseColorDrawable imageForContributionHeadline = null; //This will hold the icon for Contribution for light theme

    private int eventType;//should be START_DOCUMENT
    private int phyphoxDepth = -1; //Depth of the first phyphox tag (We only care for title, icon, description and category directly below the phyphox tag)
    private int translationBlockDepth = -1; //Depth of the translations block
    private int translationDepth = -1; //Depth of a suitable translation, if found.
    private int languageRating = 0; //If we find a locale, it replaces previous translations as long as it has a higher rating than the previous one.

    private boolean inInput = false;
    private boolean inOutput = false;
    private Integer unavailableSensor = -1;
    private boolean isLink = false;
    private String link = null;


    ExperimentInfoXMLParser(InputStream input,
                            String experimentXML,
                            String isTemp,
                            boolean isAsset,
                            Vector<ExperimentsInCategory> categories,
                            HashMap<String, Vector<String>> bluetoothDeviceNameList,
                            HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList){
        this.context = App.getContext();
        this.input = input;
        this.experimentXML = experimentXML;
        this.isAsset = isAsset;
        this.isTemp = isTemp;
        this.categories = categories;
        this.bluetoothDeviceNameList = bluetoothDeviceNameList;
        this.bluetoothDeviceUUIDList = bluetoothDeviceUUIDList;


        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            //Prepare the PullParser
            xpp = Xml.newPullParser();
            xpp.setInput(input, "UTF-8");
        } catch (XmlPullParserException e) {
            Toast.makeText(context, "Cannot open " + experimentXML + ".", Toast.LENGTH_LONG).show();
            return;
        }


        try {
            parseXML();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            //TODO  addInvalidExperiment(experimentXML,  "Error loading " + experimentXML + " (XML Exception)", isTemp, isAsset, categories);
        } catch (IOException e){
            e.printStackTrace();
            //TODO  addInvalidExperiment(experimentXML,  "Error loading " + experimentXML + " (IOException)", isTemp, isAsset, categories);
        }

    }

    private void parseXML() throws XmlPullParserException, IOException{

        eventType = xpp.getEventType();
        color = context.getResources().getColor(R.color.phyphox_primary);

        //This part is used to check sensor availability before launching the experiment
        SensorManager sensorManager = (SensorManager)context.getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...

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
                                int thisLaguageRating = Helper.getLanguageRating(context.getResources(), globalLocale);
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
                            int thisLaguageRating = Helper.getLanguageRating(context.getResources(), thisLocale);
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
                                            image = new BitmapIcon(bitmap, context);
                                            Bitmap bitmapDiffColor = Helper.changeColorOf(context, bitmap, R.color.phyphox_white_100);
                                            if(bitmapDiffColor != null)
                                                imageForContributionHeadline = new BitmapIcon(bitmapDiffColor, context);
                                        }

                                    } catch (IllegalArgumentException e) {
                                        Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                    }
                                } else if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("svg")) { //Check the icon type
                                    //SVG image. Handle it with AndroidSVG
                                    icon = xpp.nextText().trim();
                                    try {
                                        SVG svg = SVG.getFromString(icon);
                                        image = new VectorIcon(svg, context);
                                    } catch (SVGParseException e) {
                                        Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                    }
                                } else {
                                    //Just a string. Create an icon from it. We allow a maximum of three characters.
                                    icon = xpp.nextText().trim();
                                    if (icon.length() > 3)
                                        icon = icon.substring(0,3);
                                    image = new TextIcon(icon, context);
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
                                color = Helper.parseColor(xpp.nextText().trim(), context.getResources().getColor(R.color.phyphox_primary), context.getResources());
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
                            if (!GpsInput.isAvailable(context)) {
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
                            } else if (!Bluetooth.isSupported(context)) {
                                unavailableSensor = R.string.bluetooth;
                            }
                            if (!customColor)
                                color = context.getResources().getColor(R.color.phyphox_blue_100);
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
            //TODO addInvalidExperiment(experimentXML,  "Invalid: \" + experimentXML + \" misses a title.", isTemp, isAsset, categories);
            return;
        }

        //Sanity check: We need a category!
        if (category.equals("")) {
            //TODO addInvalidExperiment(experimentXML,  "Invalid: \" + experimentXML + \" misses a category.", isTemp, isAsset, categories);
            return;
        }

        if (!stateTitle.equals("")) {
            description = title;
            title = stateTitle;
            category = context.getString(R.string.save_state_category);
        }

        //Let's check the icon
        if (image == null) //No icon given. Create a TextIcon from the first three characters of the title
            image = new TextIcon(title.substring(0, Math.min(title.length(), 3)), context);
        image.setBaseColor(color);


        //We have all the information. Add the experiment.
        // Following condition is for setting the proper image and its color in the Contribution Headline
        BaseColorDrawable mImage;
        if(category.equals("phyphox.org") && !Helper.isDarkTheme(context.getResources())){
            mImage = imageForContributionHeadline;
            mImage.setColorFilter( 0xff000000, PorterDuff.Mode.MULTIPLY );
        } else {
            mImage = image;
            mImage.setBaseColor(color);
        }
        experimentDataModel = new ExperimentDataModel( category, color, mImage, title, isLink ? "Link: " + link : description, experimentXML, isTemp, isAsset, unavailableSensor, (isLink ? link : null));
            //TODO addExperiment(title, category, color, image, isLink ? "Link: " + link : description, experimentXML, isTemp, isAsset, unavailableSensor, (isLink ? link : null), categories);

    }

    public ExperimentDataModel getExperimentDataModel() {
        return experimentDataModel;
    }
}
