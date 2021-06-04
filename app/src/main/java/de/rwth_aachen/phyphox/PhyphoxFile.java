package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Xml;
import android.view.Gravity;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothInput;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothOutput;
import de.rwth_aachen.phyphox.Bluetooth.ConversionsConfig;
import de.rwth_aachen.phyphox.Bluetooth.ConversionsInput;
import de.rwth_aachen.phyphox.Bluetooth.ConversionsOutput;
import de.rwth_aachen.phyphox.NetworkConnection.Mqtt.MqttCsv;
import de.rwth_aachen.phyphox.NetworkConnection.Mqtt.MqttJson;
import de.rwth_aachen.phyphox.NetworkConnection.Mqtt.MqttTlsCsv;
import de.rwth_aachen.phyphox.NetworkConnection.Mqtt.MqttTlsJson;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConversion;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkDiscovery;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

//phyphoxFile implements the loading of an experiment from a *.phyphox file as well as the copying
//of a remote phyphox-file to the local collection. Both are implemented as an AsyncTask
public abstract class PhyphoxFile {

    public final static String phyphoxFileVersion = "1.13";

    //translation maps any term for which a suitable translation is found to the current locale or, as fallback, to English
    private static Map<String, String> translation = new HashMap<>();
    private static int languageRating = 0; //If we find a locale, it replaces previous translations as long as it has a higher rating than the previous one.

    //Simple helper to return either the translated term or the original one, if no translation could be found
    private static String translate(String input) {
        if (input == null)
            return null;
        if (translation.containsKey(input.trim()))
            return translation.get(input.trim());
        else
            return input;
    }

    //Returns true if the string is a valid identifier for a dataBuffer, very early versions had some rules here, but we now allow anything as long as it is not empty.
    public static boolean isValidIdentifier(String s) {
        if (s.isEmpty()) {
            return false;
        }
        return true;
    }

    //PhyphoxStream bundles the result of an opened stream
    public static class PhyphoxStream {
        boolean isLocal;                //is the stream a local resource? (asset or private file)
        InputStream inputStream = null; //the input stream or null on error
        byte source[] = null;           //A copy of the input for non-local sources
        String errorMessage = "";       //Error message that can be displayed to the user
        long crc32;
    }

    //Helper function to read an input stream into memory and return an input stream to the data in memory as well as the data
    public static void remoteInputToMemory(PhyphoxStream stream) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        CRC32 crc32 = new CRC32();

        int n;
        byte[] buffer = new byte[1024];
        while ((n = stream.inputStream.read(buffer, 0, 1024)) != -1) {
            os.write(buffer, 0, n);
            crc32.update(buffer, 0, n);
        }

        os.flush();
        stream.source = os.toByteArray();
        stream.inputStream = new ByteArrayInputStream(stream.source);
        stream.crc32 = crc32.getValue();
    }

    //Helper function to open an inputStream from various intents
    public static PhyphoxStream openXMLInputStream(Intent intent, Activity parent) {
        languageRating = 0;//If we find a locale, it replaces previous translations as long as it has a higher rating than the previous one.
        translation = new HashMap<>();

        PhyphoxStream phyphoxStream = new PhyphoxStream();

        //We only respond to view-action-intents
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_VIEW)) {

            //We need to perform slightly different actions for all the different schemes
            String scheme = intent.getScheme();

            if (intent.getStringExtra(ExperimentList.EXPERIMENT_XML) != null) { //If the file location is found in the extra EXPERIMENT_XML, it is a local file
                phyphoxStream.isLocal = !intent.getBooleanExtra(ExperimentList.EXPERIMENT_ISTEMP, false);
                if (intent.getBooleanExtra(ExperimentList.EXPERIMENT_ISASSET, true)) { //The local file is an asser
                    AssetManager assetManager = parent.getAssets();
                    try {
                        phyphoxStream.inputStream = assetManager.open("experiments/" + intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                        remoteInputToMemory(phyphoxStream);
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from assets: " + e.getMessage();
                    }
                } else if (intent.getStringExtra(ExperimentList.EXPERIMENT_ISTEMP) != null) {
                    //This is a temporary file. Typically from a zip file. It's in the private directory, but in a subfolder called "temp"
                    try {
                        File tempDir = new File(parent.getFilesDir(), intent.getStringExtra(ExperimentList.EXPERIMENT_ISTEMP));
                        phyphoxStream.inputStream = new FileInputStream(new File(tempDir, intent.getStringExtra(ExperimentList.EXPERIMENT_XML)));
                        remoteInputToMemory(phyphoxStream);
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from local storage: " +e.getMessage();
                    }
                } else { //The local file is in the private directory
                    try {
                        phyphoxStream.inputStream = parent.openFileInput(intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                        remoteInputToMemory(phyphoxStream);
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from local storage: " +e.getMessage();
                    }
                }
                return phyphoxStream;

            } else if (scheme.equals(ContentResolver.SCHEME_FILE )) {//The intent refers to a file
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                if (uri == null) {
                    phyphoxStream.errorMessage = "Missing uri.";
                    return phyphoxStream;
                }
                ContentResolver resolver = parent.getContentResolver();
                //We will need read permissions for pretty much any file...
                if (!uri.getPath().startsWith(parent.getFilesDir().getPath()) && ContextCompat.checkSelfPermission(parent, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    //Android 6.0: No permission? Request it!
                    ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    //We will stop with a no permission error. If the user grants the permission, the permission callback will restart the action with the same intent
                    phyphoxStream.errorMessage = "Permission needed to read external storage.";
                    return phyphoxStream;
                }
                try {
                    phyphoxStream.inputStream = resolver.openInputStream(uri);
                    remoteInputToMemory(phyphoxStream);
                } catch (Exception e) {
                    phyphoxStream.errorMessage = "Error loading experiment from file: " + e.getMessage();
                }
                return phyphoxStream;

            } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {//The intent refers to a content (like the attachment from a mailing app)
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = parent.getContentResolver();
                try {
                    phyphoxStream.inputStream = resolver.openInputStream(uri);
                    remoteInputToMemory(phyphoxStream);
                } catch (Exception e) {
                    phyphoxStream.errorMessage = "Error loading experiment from content: " + e.getMessage();
                }
                return phyphoxStream;
            } else if (scheme.equals("phyphox")) { //The intent refers to an online resource, but we need to figure out if we can use https or should fallback to http
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                try {
                    URL url = new URL("https", uri.getHost(), uri.getPort(), uri.getPath() + (uri.getQuery() != null ? ("?" + uri.getQuery()) : ""));
                    phyphoxStream.inputStream = url.openStream();
                    remoteInputToMemory(phyphoxStream);
                } catch (Exception e) {
                    //ok, https did not work. Maybe we success with http?
                    try {
                        URL url = new URL("http", uri.getHost(), uri.getPort(), uri.getPath() + (uri.getQuery() != null ? ("?" + uri.getQuery()) : ""));
                        phyphoxStream.inputStream = url.openStream();
                        remoteInputToMemory(phyphoxStream);
                    } catch (Exception e2) {
                        phyphoxStream.errorMessage = "Error loading experiment from phyphox: " + e2.getMessage();
                    }
                }
                return phyphoxStream;
            }
            phyphoxStream.errorMessage = "Unknown scheme.";
            return phyphoxStream;
        } else {
            phyphoxStream.errorMessage = "No run-intent.";
            return phyphoxStream;
        }
    }

    //Exceptions caused by a bad phyphox file
    public static class phyphoxFileException extends Exception {
        public phyphoxFileException(String message) {
            super(message);
        }

        public phyphoxFileException(String message, int line) {
            super("Line " + line + ": " + message);
        }
    }

    //A xmlBlockParser loads all the xml data into the experiment within a specific xml block
    //For each block type, this a class has to be derived, which overrides processStartTag and
    // processEndTag
    protected static class xmlBlockParser {
        protected Experiment parent; //For some elements we need access to the parent activity
        private String tag; //The tag of the block that should be handled by this parser
        private int rootDepth; //The depth of the base of the block handled by this parser
        protected XmlPullParser xpp; //The pull parser used handed to this parser
        protected PhyphoxExperiment experiment; //The experiment to be loaded

        private boolean textAdvanced;

        //The constructor takes the tag, and the experiment to fill
        xmlBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            this.xpp = xpp;
            this.experiment = experiment;
            this.parent = parent;
        }

        //Helper to receive the text block of a tag
        protected String getText() throws XmlPullParserException, IOException {
            String text = xpp.nextText();
            textAdvanced = true;
            if (text != null)
                return text.trim();
            else
                return null;
        }

        //Helper to receive a string typed attribute
        protected String getStringAttribute(String identifier) {
            return xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier);
        }

        //Helper to receive a string typed attribute and translate it
        protected String getTranslatedAttribute(String identifier) {
            return translate(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier));
        }

        //Helper to receive an integer typed attribute, if invalid or not present, return default
        protected int getIntAttribute(String identifier, int defaultValue) {
            try {
                return Integer.valueOf(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        //Helper to receive a double typed attribute, if invalid or not present, return default
        protected double getDoubleAttribute(String identifier, double defaultValue) {
            try {
                return Double.valueOf(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        //Helper to receive a boolean attribute, if invalid or not present, return default
        protected boolean getBooleanAttribute(String identifier, boolean defaultValue) {
            final String att = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier);
            if (att == null)
                return defaultValue;
            return Boolean.valueOf(att);
        }

        //Helper to receive a color attribute, if invalid or not present, return default
        protected int getColorAttribute(String identifier, int defaultValue) {
            final String att = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, identifier);

            return Helper.parseColor(att, defaultValue, parent.getResources());
        }

        //These functions should be overriden with block-specific code
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {

        }
        protected void processEndTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {

        }
        protected void done() throws IOException, XmlPullParserException, phyphoxFileException {

        }

        public void process() throws IOException, XmlPullParserException, phyphoxFileException {
            int eventType = xpp.getEventType();
            if (eventType != XmlPullParser.START_TAG)
                throw new phyphoxFileException("xmlBlockParser called on something else than a start tag.", xpp.getLineNumber());

            //Remember our entry point
            this.rootDepth = xpp.getDepth();
            this.tag = xpp.getName();

            //That's it for the root element. Start to distribute the next events to processStartTag and processEndTag
            eventType = xpp.next();

            //Loop until we leave the block again. So unless the depth matches root level and we see
            //our tag as an end tag, we should continue
            while (xpp.getDepth() != rootDepth || eventType != XmlPullParser.END_TAG || !xpp.getName().equalsIgnoreCase(tag)) {
                textAdvanced = false;
                switch (eventType) {
                    case XmlPullParser.END_DOCUMENT: //We should not reach the end of the document wthin this block
                        throw new phyphoxFileException("Unexpected end of document.", xpp.getLineNumber());
                    case XmlPullParser.START_TAG:
                        processStartTag(xpp.getName());
                        break;
                    case XmlPullParser.END_TAG:
                        processEndTag(xpp.getName());
                        break;
                }
                if (!textAdvanced)
                    eventType = xpp.next();
                else
                    eventType = xpp.getEventType();
            }

            done();
        }
    }

    //Blockparser for AudioOutput plugin section
    private static class AudioOutputPluginBlockParser extends xmlBlockParser {
        AudioOutput audioOutput;
        AudioOutput.AudioOutputPlugin currentPlugin = null;
        int level = 0;

        AudioOutputPluginBlockParser (XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, AudioOutput audioOutput) {
            super(xpp, experiment, parent);
            this.audioOutput = audioOutput;
        }

        @Override
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            level++;
            switch (tag.toLowerCase()) {
                case "input": {
                    String parameter = getStringAttribute("parameter");

                    DataInput input;
                    String type = getStringAttribute("type");
                    if (type == null)
                        type = "buffer";

                    if (type.equals("buffer")) {
                        String bufferName = getText();
                        DataBuffer buffer = experiment.getBuffer(bufferName);
                        if (buffer == null) {
                            throw new phyphoxFileException("Buffer \"" + bufferName + "\" not defined.", xpp.getLineNumber());
                        }
                        input = new DataInput(buffer, false);
                    } else if (type.equals("value")) {
                        double value;
                        try {
                            value = Double.valueOf(getText());
                        } catch (NumberFormatException e) {
                            throw new phyphoxFileException("Invalid number format.", xpp.getLineNumber());
                        }
                        input = new DataInput(value);
                    } else {
                        throw new phyphoxFileException("Unknown input type \""+type+"\".", xpp.getLineNumber());
                    }
                    if (level == 1) {
                        //Direct input
                        currentPlugin = audioOutput.new AudioOutputPluginDirect(input);
                    } else if (level == 2) {
                        //Parameter
                        if (currentPlugin != null) {
                            if (parameter == null)
                                throw new phyphoxFileException("Parameter attribute required for this plugin.", xpp.getLineNumber());

                            if (!currentPlugin.setParameter(parameter, input))
                                throw new phyphoxFileException("Parameter \""+parameter+"\" not supported by this plugin.", xpp.getLineNumber());
                        } else {
                            throw new phyphoxFileException("Unexpected input tag. No related audio plugin.", xpp.getLineNumber());
                        }
                    } else {
                        throw new phyphoxFileException("Unexpected input tag.", xpp.getLineNumber());
                    }
                    break;
                }
                case "tone": {
                    if (level == 1) {
                        //Tone plugin
                        currentPlugin = audioOutput.new AudioOutputPluginTone();
                    } else {
                        throw new phyphoxFileException("Unexpected tone tag.", xpp.getLineNumber());
                    }
                    break;
                }
                case "noise": {
                    if (level == 1) {
                        //Noise plugin
                        currentPlugin = audioOutput.new AudioOutputPluginNoise();
                    } else {
                        throw new phyphoxFileException("Unexpected noise tag.", xpp.getLineNumber());
                    }
                    break;
                }
                default: {
                    throw new phyphoxFileException("Unexpected tag \"" + tag + "\"", xpp.getLineNumber());
                }
            }
        }

        @Override
        protected void processEndTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            level--;
            if (level == 0 && currentPlugin != null) {
                audioOutput.attachPlugin(currentPlugin);
                currentPlugin = null;
            }
        }
    }

    // Blockparser for input or output assignments inside a bluetooth-block
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static class bluetoothIoBlockParser extends xmlBlockParser {
        protected static Class conversionsInput = (new ConversionsInput()).getClass();
        protected static Class conversionsOutput = (new ConversionsOutput()).getClass();
        protected static Class conversionsConfig = (new ConversionsConfig()).getClass();
        Vector<DataOutput> outputList;
        Vector<DataInput> inputList;
        Vector<Bluetooth.CharacteristicData> characteristics; // characteristics of the bluetooth input / output
        HashSet<UUID> characteristicsWithExtraTime; // uuids of all characteristics that have extra=time to make sure they can't have it twice

        bluetoothIoBlockParser (XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, Vector<DataOutput> outputList, Vector<DataInput> inputList, Vector<Bluetooth.CharacteristicData> characteristics) {
            super(xpp, experiment, parent);
            this.outputList = outputList;
            this.inputList = inputList;
            this.characteristics = characteristics;
            characteristicsWithExtraTime = new HashSet<>();
        }

        @Override
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            // get and check "char" attribute
            String charUuid = getStringAttribute("char");
            if (charUuid == null) {
                throw new phyphoxFileException("Tag needs a char attribute.", xpp.getLineNumber());
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(charUuid);
            } catch (IllegalArgumentException e) {
                throw new phyphoxFileException("invalid UUID.", xpp.getLineNumber());
            }

            // get "conversion" attribute
            String conversionFunctionName = getStringAttribute("conversion");
            ConversionsConfig.ConfigConversion configConversionFunction = null;
            ConversionsInput.InputConversion inputConversionFunction = null;
            ConversionsOutput.OutputConversion outputConversionFunction = null;

            switch (tag.toLowerCase()) {
                case "input": {
                    // check if "input" is allowed here
                    if (inputList == null) {
                        throw new phyphoxFileException("No output expected.", xpp.getLineNumber());
                    }

                    // check conversion attribute
                    if (conversionFunctionName == null) {
                        throw new phyphoxFileException("Tag needs a conversion attribute.", xpp.getLineNumber());
                    }
                    try {
                        try {
                            try {
                                Class conversionClass = Class.forName("de.rwth_aachen.phyphox.Bluetooth.ConversionsOutput$" + conversionFunctionName);
                                Constructor constructor = conversionClass.getConstructor(XmlPullParser.class);
                                constructor.setAccessible(true);
                                outputConversionFunction = (ConversionsOutput.OutputConversion) constructor.newInstance(xpp);
                            } catch (Exception e) {
                                Method conversionMethod = conversionsOutput.getDeclaredMethod(conversionFunctionName, new Class[]{DataBuffer.class});
                                outputConversionFunction = new ConversionsOutput.SimpleOutputConversion(conversionMethod);
                            }
                        } catch (Exception e) {
                            Method conversionMethod = conversionsOutput.getDeclaredMethod(conversionFunctionName, new Class[]{double.class});
                            outputConversionFunction = new ConversionsOutput.SimpleOutputConversion(conversionMethod);
                        }
                    } catch (NoSuchMethodException e) {
                        throw new phyphoxFileException("invalid conversion function: " + conversionFunctionName, xpp.getLineNumber());
                    }

                    // check if buffer exists
                    String bufferName = getText();
                    DataBuffer buffer = experiment.getBuffer(bufferName);
                    if (buffer == null) {
                        throw new phyphoxFileException("Buffer \"" + bufferName + "\" not defined.", xpp.getLineNumber());
                    }

                    inputList.add(new DataInput(buffer, false));

                    // add data to characteristics
                    characteristics.add(new Bluetooth.OutputData(uuid, inputList.size()-1, outputConversionFunction));

                    break;
                }

                case "output": {
                    // check if "output" is allowed here
                    if (outputList == null) {
                        throw new phyphoxFileException("No output expected.", xpp.getLineNumber());
                    }

                    // get and check "extra" attribute
                    boolean extraTime = false;
                    String extra = this.getStringAttribute("extra");
                    if (extra != null) {
                        if (extra.equals("time")) {
                            extraTime = true;
                            if (characteristicsWithExtraTime.contains(uuid)) {
                                throw new phyphoxFileException("extra=time can be used only once for a characteristic.");
                            } else {
                                characteristicsWithExtraTime.add(uuid);
                            }
                        } else {
                            throw new phyphoxFileException("unknown value for extra attribute.", xpp.getLineNumber());
                        }
                    }

                    // check conversion attribute
                    if (!extraTime) {
                       if (conversionFunctionName == null) {
                           throw new phyphoxFileException("Tag needs a conversion attribute.", xpp.getLineNumber());
                       }
                        try {
                            try {
                                Class conversionClass = Class.forName("de.rwth_aachen.phyphox.Bluetooth.ConversionsInput$" + conversionFunctionName);
                                Constructor constructor = conversionClass.getDeclaredConstructor(new Class[]{XmlPullParser.class});
                                constructor.setAccessible(true);
                                inputConversionFunction = (ConversionsInput.InputConversion)constructor.newInstance(xpp);
                            } catch (Exception e) {
                                Method conversionMethod = conversionsInput.getDeclaredMethod(conversionFunctionName, new Class[]{byte[].class});
                                inputConversionFunction = new ConversionsInput.SimpleInputConversion(conversionMethod, xpp);
                            }
                        } catch (NoSuchMethodException e) {
                            throw new phyphoxFileException("invalid conversion function: " + conversionFunctionName, xpp.getLineNumber());
                        }
                    }

                    // check if buffer exists
                    String bufferName = getText();
                    DataBuffer buffer = experiment.getBuffer(bufferName);
                    if (buffer == null) {
                        throw new phyphoxFileException("Buffer \"" + bufferName + "\" not defined.", xpp.getLineNumber());
                    }

                    outputList.add(new DataOutput(buffer, false));

                    // add data to characteristics
                    characteristics.add(new Bluetooth.InputData(uuid, extraTime, outputList.size()-1, inputConversionFunction));
                    break;
                }

                case "config": {
                    // check if conversion attribute exists
                    if (conversionFunctionName == null) {
                        throw new phyphoxFileException("Tag needs a conversion attribute.", xpp.getLineNumber());
                    }
                    try {
                        try {
                            Class conversionClass = Class.forName("de.rwth_aachen.phyphox.Bluetooth.ConversionsConfig$" + conversionFunctionName);
                            Constructor constructor = conversionClass.getConstructor(XmlPullParser.class);
                            constructor.setAccessible(true);
                            configConversionFunction = (ConversionsConfig.ConfigConversion)constructor.newInstance(xpp);
                        } catch (Exception e) {
                            Method conversionMethod = conversionsConfig.getDeclaredMethod(conversionFunctionName, String.class);
                            configConversionFunction = new ConversionsConfig.SimpleConfigConversion(conversionMethod);
                        }
                    } catch (NoSuchMethodException e) {
                        throw new phyphoxFileException("invalid conversion function: " + conversionFunctionName, xpp.getLineNumber());
                    }
                    try {
                        // add data to configs
                        String text = getText();
                        characteristics.add(new Bluetooth.ConfigData(uuid, text, configConversionFunction));
                    } catch (NumberFormatException e) {
                        throw new phyphoxFileException("Configuration data has to be a valid double value.", xpp.getLineNumber());
                    } catch (phyphoxFileException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber()); // throw it again but with LineNumber
                    }
                    break;
                }
                default: {
                    throw new phyphoxFileException("Unknown tag \""+tag+"\"", xpp.getLineNumber());
                }
            }
        }

    }

    //Blockparser for any input and output assignments
    private static class ioBlockParser extends xmlBlockParser {

        public static class ioMapping {
            String name;
            boolean asRequired = true;
            int repeatableOffset = -1;
            boolean valueAllowed = true;
            boolean emptyAllowed = false;
            int minCount = 0;
            int maxCount = 0;
            int count = 0;
        }

        public static class AdditionalTag {
            String name;
            String content;
            Map<String, String> attributes = new HashMap<>();
        }
        Vector<AdditionalTag> additionalTags;

        Vector<DataInput> inputList;
        Vector<DataOutput> outputList;
        ioMapping[] inputMapping;
        ioMapping[] outputMapping;
        String mappingAttribute;

        ioBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, Vector<DataInput> inputList, Vector<DataOutput> outputList, ioMapping[] inputMapping, ioMapping[] outputMapping, String mappingAttribute) {
            this(xpp, experiment, parent, inputList, outputList, inputMapping, outputMapping, mappingAttribute, null);
        }

        ioBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, Vector<DataInput> inputList, Vector<DataOutput> outputList, ioMapping[] inputMapping, ioMapping[] outputMapping, String mappingAttribute, Vector<AdditionalTag> additionalTags) {
            super(xpp, experiment, parent);
            this.inputList = inputList;
            this.outputList = outputList;
            this.inputMapping = inputMapping;
            this.outputMapping = outputMapping;
            this.mappingAttribute = mappingAttribute;
            this.additionalTags = additionalTags;
        }

        @Override
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            int targetIndex = -1; //This will hold the index of the inputList or outputList entry, that should be mapped to the given buffer
            int mappingIndex = -1; //This will hold the index of the inputMapping or outputMapping, that holds the rules for this mapping
            String mapping;

            //Get the mapping
            if (mappingAttribute != null)
                mapping = getStringAttribute(mappingAttribute);
            else
                mapping = null;

            AdditionalTag at = null;
            if (additionalTags != null) {
                at = new AdditionalTag();
                for (int i = 0; i < xpp.getAttributeCount(); i++)
                    at.attributes.put(xpp.getAttributeName(i).toLowerCase(), xpp.getAttributeValue(i));
                at.name = tag.toLowerCase();
            }

            switch (tag.toLowerCase()) {
                case "input":   //Input tag
                    if (inputMapping == null) //We did not even expect inputs here...
                        throw new phyphoxFileException("No input expected.", xpp.getLineNumber());

                    //Check the type
                    String type = getStringAttribute("type");
                    if (type == null)
                        type = "buffer";

                    if (mapping != null) {
                        //An explicit mapping has been given

                        //Check if there is a matching inputMapping
                        for (int i = 0; i < inputMapping.length; i++) {
                            if (inputMapping[i].name.equals(mapping)) {
                                targetIndex = i;
                                mappingIndex = i;
                                break;
                            }
                        }

                        if (targetIndex < 0) //No mapping found at all
                            throw new phyphoxFileException("Could not find mapping for input \""+mapping+"\".", xpp.getLineNumber());

                        //Increase the inputList if necessary
                        if (targetIndex >= inputList.size())
                            inputList.setSize(targetIndex+1);

                        //If the targetIndex is not yet mapped, we are done here. If not, we have to check if this entry is repeatable, so we can map it again
                        if (inputList.get(targetIndex) != null || inputMapping[targetIndex].repeatableOffset >= 0) {
                            if (inputMapping[targetIndex].repeatableOffset >= 0) {
                                //It is repeatable. Let's calculate a new index according to the repeatable offset
                                int repeatPeriod = inputMapping[inputMapping.length-1].repeatableOffset+1;
                                //If the value is repeatable, we want to add it to the last current repeatable group
                                while (targetIndex-inputMapping[mappingIndex].repeatableOffset+repeatPeriod < inputList.size())
                                    targetIndex += repeatPeriod;
                                //Increase the inputList if necessary
                                if (targetIndex >= inputList.size())
                                    inputList.setSize(targetIndex+1);
                                //Recalculate the index while the input entry is still in use
                                while (inputList.get(targetIndex) != null) {
                                    targetIndex += repeatPeriod;
                                    if (targetIndex >= inputList.size())
                                        inputList.setSize(targetIndex+1);
                                }
                            } else {
                                //Already set and not repeatable.
                                throw new phyphoxFileException("The input \""+mapping+"\" has already been defined.", xpp.getLineNumber());
                            }
                        }
                    } else {
                        //No explicit mapping, we have to fill the entries that do not require the "as" attribute
                        int firstRepeatable = -1; //If there is a repeatable entry, we will remember its index here

                        //First search for an empty entry, that does not require "as"
                        for (int i = 0; i < inputMapping.length; i++) {
                            if (!inputMapping[i].asRequired) {
                                //While we are already iterating this list: Remember the repeatable entries
                                if (inputMapping[i].repeatableOffset >= 0) {
                                    if (firstRepeatable < 0)
                                        firstRepeatable = i;
                                }
                                //Resize inputList if necessary
                                if (i >= inputList.size())
                                    inputList.setSize(i+1);

                                //Is this entry empty? Great, we have found our target
                                if (inputList.get(i) == null) {
                                    targetIndex = i;
                                    mappingIndex = i;
                                    break;
                                }
                            }
                        }
                        //Target not found? Let's try to fill repeatables.
                        if (targetIndex < 0) {
                            if (firstRepeatable >= 0) {
                                //We have repeatables. So let's just dump our inputs at the end of the list
                                int repeatPeriod = inputMapping[inputMapping.length-1].repeatableOffset+1;
                                targetIndex = inputMapping.length;
                                int repeatIndex = 0; //We have to keep track of where we place it, so we know which mapping we just used
                                if (targetIndex >= inputList.size())
                                    inputList.setSize(targetIndex+1);
                                while (inputList.get(targetIndex) != null || inputMapping[firstRepeatable+repeatIndex].asRequired) {
                                    targetIndex++; //Still not empty. Next one.
                                    repeatIndex = (repeatIndex+1)%repeatPeriod; //Next also means, that we have the next mapping. At the end of all repeatables we start over again.
                                    if (targetIndex >= inputList.size())
                                        inputList.setSize(targetIndex+1);
                                }
                                mappingIndex = firstRepeatable + repeatIndex;
                            } else //Not found and no repeatables. Let's complain.
                                throw new phyphoxFileException("The non-mapped input from buffer " + getText() + " could not be matched.", xpp.getLineNumber());
                        }
                    }

                    //targetIndex should now point to the index in input list, where the input should be placed.
                    //mappingIndex points to the index in inputMapping, which describes its mapping
                    inputMapping[mappingIndex].count++;

                    //The input may have different types...
                    if (type.equals("value")) {
                        //Just a value, Is this allowed?
                        if (inputMapping[mappingIndex].valueAllowed) {
                            double value;
                            try {
                                value = Double.valueOf(getText());
                            } catch (NumberFormatException e) {
                                throw new phyphoxFileException("Invalid number format.", xpp.getLineNumber());
                            }
                            inputList.set(targetIndex, new DataInput(value));
                        } else {
                            throw new phyphoxFileException("Value-type not allowed for input \""+inputMapping[mappingIndex].name+"\".", xpp.getLineNumber());
                        }
                    } else if (type.equals("buffer")) {

                        //Check the type
                        boolean clearAfterRead = getBooleanAttribute("clear", true);

                        //This is a buffer. Let's see if it exists
                        String bufferName = getText();
                        if (additionalTags != null)
                            at.content = bufferName;
                        DataBuffer buffer = experiment.getBuffer(bufferName);
                        if (buffer == null)
                            throw new phyphoxFileException("Buffer \""+bufferName+"\" not defined.", xpp.getLineNumber());
                        else {
                            inputList.set(targetIndex, new DataInput(buffer, clearAfterRead));
                        }
                    } else if (type.equals("empty")) {
                        //No input, Is this allowed?
                        if (inputMapping[mappingIndex].emptyAllowed) {
                            inputList.set(targetIndex, new DataInput());
                        } else {
                            throw new phyphoxFileException("Value-type not allowed for input \""+inputMapping[mappingIndex].name+"\".", xpp.getLineNumber());
                        }
                    } else {
                        throw new phyphoxFileException("Unknown input type \""+type+"\".", xpp.getLineNumber());
                    }

                    break;
                case "output":
                    if (outputMapping == null)
                        throw new phyphoxFileException("No output expected.", xpp.getLineNumber());

                    //Check the type
                    boolean clearBeforeWrite = getBooleanAttribute("clear", true);

                    if (mapping != null) {
                        for (int i = 0; i < outputMapping.length; i++) {
                            if (outputMapping[i].name.equals(mapping)) {
                                targetIndex = i;
                                mappingIndex = i;
                                break;
                            }
                        }
                        if (targetIndex < 0)
                            throw new phyphoxFileException("Could not find mapping for output \""+mapping+"\".", xpp.getLineNumber());
                        if (targetIndex >= outputList.size())
                            outputList.setSize(targetIndex+1);
                        if (outputList.get(targetIndex) != null) {
                            if (outputMapping[targetIndex].repeatableOffset >= 0) {
                                targetIndex = outputMapping.length + outputMapping[targetIndex].repeatableOffset;
                                if (targetIndex >= outputList.size())
                                    outputList.setSize(targetIndex+1);
                                while (outputList.get(targetIndex) != null) {
                                    targetIndex += outputMapping[outputMapping.length-1].repeatableOffset+1;
                                    if (targetIndex >= outputList.size())
                                        outputList.setSize(targetIndex+1);
                                }
                            } else {
                                throw new phyphoxFileException("The output \""+mapping+"\" has already been defined.", xpp.getLineNumber());
                            }
                        }
                    } else {
                        int firstRepeatable = -1;
                        int repeatPeriod = 0;
                        for (int i = 0; i < outputMapping.length; i++) {
                            if (!outputMapping[i].asRequired) {
                                if (outputMapping[i].repeatableOffset >= 0) {
                                    if (firstRepeatable < 0)
                                        firstRepeatable = i;
                                    repeatPeriod = outputMapping[i].repeatableOffset+1;
                                }
                                if (i >= outputList.size())
                                    outputList.setSize(i+1);
                                if (outputList.get(i) == null) {
                                    targetIndex = i;
                                    mappingIndex = i;
                                    break;
                                }
                            }
                        }
                        if (targetIndex < 0) {
                            if (firstRepeatable >= 0) {
                                targetIndex = outputMapping.length;
                                int repeatIndex = 0;
                                if (targetIndex >= outputList.size())
                                    outputList.setSize(targetIndex+1);
                                while (outputList.get(targetIndex) != null) {
                                    targetIndex++;
                                    repeatIndex = (repeatIndex+1)%repeatPeriod;
                                    if (targetIndex >= outputList.size())
                                        outputList.setSize(targetIndex+1);
                                }
                                mappingIndex = firstRepeatable + repeatIndex;
                            } else
                                throw new phyphoxFileException("The non-mapped output could not be matched.", xpp.getLineNumber());
                        }
                    }

                    //targetIndex should now point to the index in output list, where the output should be placed.
                    //mappingIndex points to the index in outputMapping, which describes its mapping
                    outputMapping[mappingIndex].count++;


                    String bufferName = getText();
                    if (additionalTags != null)
                        at.content = bufferName;
                    DataBuffer buffer = experiment.getBuffer(bufferName);
                    if (buffer == null)
                        throw new phyphoxFileException("Buffer \""+bufferName+"\" not defined.", xpp.getLineNumber());
                    else {
                        outputList.set(targetIndex, new DataOutput(buffer, clearBeforeWrite));
                    }
                    break;
                default: //Unknown tag...
                    if (additionalTags == null)
                        throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
                    at.content = getText();
            }
            if (additionalTags != null) {
                if (targetIndex > -1) {
                    if (targetIndex >= additionalTags.size())
                        additionalTags.setSize(targetIndex + 1);
                    additionalTags.set(targetIndex, at);
                } else
                    additionalTags.add(at);
            }
        }

        @Override
        protected void done() throws phyphoxFileException {
            //Check if the number of inputs and outputs are valid
            if (inputMapping != null) {
                for (int i = 0; i < inputMapping.length; i++) {
                    if ((inputMapping[i].maxCount > 0 && inputMapping[i].count > inputMapping[i].maxCount))
                        throw new phyphoxFileException("A maximum of " + inputMapping[i].maxCount + " inputs was expected for " + inputMapping[i].name + " but " + inputMapping[i].count + " were found.", xpp.getLineNumber());
                    if (inputMapping[i].count < inputMapping[i].minCount)
                        throw new phyphoxFileException("A minimum of " + inputMapping[i].minCount + " inputs was expected for " + inputMapping[i].name + " but " + inputMapping[i].count + " were found.", xpp.getLineNumber());
                }
            }
            if (outputMapping != null) {
                for (int i = 0; i < outputMapping.length; i++) {
                    if ((outputMapping[i].maxCount > 0 && outputMapping[i].count > outputMapping[i].maxCount))
                        throw new phyphoxFileException("A maximum of " + outputMapping[i].maxCount + " outputs was expected for " + outputMapping[i].name + " but " + outputMapping[i].count + " were found.", xpp.getLineNumber());
                    if (outputMapping[i].count < outputMapping[i].minCount)
                        throw new phyphoxFileException("A minimum of " + outputMapping[i].minCount + " outputs was expected for " + outputMapping[i].name + " but " + outputMapping[i].count + " were found.", xpp.getLineNumber());
                }
            }
        }
    }

    //Blockparser for the root element
    private static class phyphoxBlockParser extends xmlBlockParser {

        phyphoxBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "title": //The experiment's title (might be replaced by a later translation block)
                    experiment.baseTitle = getText();
                    experiment.title = experiment.baseTitle;
                    break;
                case "state-title":
                    experiment.stateTitle = getText();
                    break;
                case "icon": //The experiment's icon
                    // We currently do not show the icon while the experiment is open, so we do not need to read it.
                    //experiment.icon = getText();
                    break;
                case "color": //The experiment's base color
                    // We currently do not use this color in the experiment view.
                    break;
                case "description": //The experiment's description (might be replaced by a later translation block)
                    experiment.description = getText().trim().replaceAll("(?m) +$", "").replaceAll("(?m)^ +", "");
                    break;
                case "link": //Links to external sources like documentation (might be replaced by a later translation block)
                    boolean highlighted = getBooleanAttribute("highlight", false);
                    String label = getStringAttribute("label");
                    String link = getText().trim().replaceAll("(?m) +$", "").replaceAll("(?m)^ +", "");
                    experiment.links.put(label, link);
                    if (highlighted)
                        experiment.highlightedLinks.put(label, link);
                    break;
                case "category": //The experiment's category (might be replaced by a later translation block)
                    experiment.baseCategory = getText();
                    experiment.category = experiment.baseCategory;
                    break;
                case "translations": //A translations block may containing multiple translation-blocks
                    (new translationsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "data-containers": //The data-containers block defines all buffers used in this experiment
                    (new dataContainersBlockParser(xpp, experiment, parent)).process();
                    break;
                case "events": //The events block stores events and their timestamps
                    (new eventsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "views": //A Views block may contain multiple view-blocks
                    (new viewsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "input": //Holds inputs like sensors or the microphone
                    (new inputBlockParser(xpp, experiment, parent)).process();
                    break;
                case "network": //Holds inputs like sensors or the microphone
                    (new networkBlockParser(xpp, experiment, parent)).process();
                    break;
                case "analysis": //Holds a number of math modules which will be executed in the order they occur
                    experiment.analysisSleep = getDoubleAttribute("sleep", 0.02); //Time between executions
                    String dynamicSleep = getStringAttribute("dynamicSleep"); //Time between executions
                    if (dynamicSleep != null) {
                        if (experiment.getBuffer(dynamicSleep) != null)
                            experiment.analysisDynamicSleep = experiment.getBuffer(dynamicSleep);
                        else
                            throw new phyphoxFileException("Dynamic sleep buffer " + dynamicSleep + " has not been defined as a buffer.", xpp.getLineNumber());
                    }
                    experiment.analysisOnUserInput = getBooleanAttribute("onUserInput", false); //Only execute when the user changed something?
                    experiment.timedRun = getBooleanAttribute("timedRun", false);
                    experiment.timedRunStartDelay = getDoubleAttribute("timedRunStartDelay", 3.0);
                    experiment.timedRunStopDelay = getDoubleAttribute("timedRunStopDelay", 10.0);
                    (new analysisBlockParser(xpp, experiment, parent)).process();
                    break;
                case "output": //Holds outputs like the speaker
                    (new outputBlockParser(xpp, experiment, parent)).process();
                    break;
                case "export": //Holds multiple set-blocks, which in turn describe which buffer should be exported as a set
                    (new exportBlockParser(xpp, experiment, parent)).process();
                    break;
                default: //Unknown tag,,,
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }
    }

    //Blockparser for the translations block
    private static class translationsBlockParser extends xmlBlockParser {

        translationsBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "translation": //A translation block holds all translation information for a single language
                    String thisLocale = getStringAttribute("locale");
                    int thisLaguageRating = Helper.getLanguageRating(parent.getResources(), thisLocale);
                    if (thisLaguageRating > languageRating) { //Check if the language matches better than previous ones...
                        languageRating = thisLaguageRating;
                        (new translationBlockParser(xpp, experiment, parent)).process(); //Jepp, use it!
                    } else
                        (new xmlBlockParser(xpp, experiment, parent)).process(); //Nope. Use the empty block parser to skip it
                    break;
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for a specific translation block
    private static class translationBlockParser extends xmlBlockParser {

        translationBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "title": //A title in our language? Great, take it!
                    experiment.title = getText();
                    break;
                case "category": //Category in the correct language
                    experiment.category = getText();
                    break;
                case "description": //Description in the correct language
                    experiment.description = getText().trim().replaceAll("(?m) +$", "").replaceAll("(?m)^ +", "");
                    break;
                case "link": //Links to external sources like documentation
                    boolean highlighted = getBooleanAttribute("highlight", false);
                    String label = getStringAttribute("label");
                    String link = getText().trim().replaceAll("(?m) +$", "").replaceAll("(?m)^ +", "");
                    experiment.links.put(label, link);
                    if (highlighted)
                        experiment.highlightedLinks.put(label, link);
                    break;
                case "string": //Some other translation. In labels and names of view elements, the string defined here as the attribute "original" will be replaced by the text in this tag
                    translation.put(getStringAttribute("original"), getText()); //Store it in our translation mapping
                    break;
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the data-containers block
    private static class dataContainersBlockParser extends xmlBlockParser {

        dataContainersBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "container": //A view defines an arangement of elements displayed to the user
                    String type = getStringAttribute("type");
                    if (type != null && !type.equals("buffer")) //There currently is only one buffer type. This tag is for future additions.
                        throw new phyphoxFileException("Unknown container type \"" + type + "\".", xpp.getLineNumber());

                    int size = getIntAttribute("size",1);
                    String strInit = getStringAttribute("init");
                    boolean isStatic = getBooleanAttribute("static", false);

                    String name = getText();
                    if (!isValidIdentifier(name))
                        throw new phyphoxFileException("\"" + name + "\" is not a valid name for a data-container.", xpp.getLineNumber());

                    DataBuffer newBuffer = experiment.createBuffer(name, size, experiment.experimentTimeReference);
                    newBuffer.setStatic(isStatic);

                    if (strInit != null && !strInit.isEmpty()) {
                        String strInitArray[] = strInit.split(",");
                        Double init[] = new Double[strInitArray.length];
                        for (int i = 0; i < init.length; i++) {
                            try {
                                init[i] = Double.parseDouble(strInitArray[i].trim());
                            } catch (Exception e) {
                                init[i] = Double.NaN;
                            }
                        }
                        newBuffer.setInit(init);
                    }
                    break;
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the events block
    private static class eventsBlockParser extends xmlBlockParser {

        eventsBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            ExperimentTimeReference.TimeMappingEvent event;
            switch (tag.toLowerCase()) {
                case "start":
                    event = ExperimentTimeReference.TimeMappingEvent.START;
                    break;
                case "pause":
                    event = ExperimentTimeReference.TimeMappingEvent.PAUSE;
                    break;
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
            Double experimentTime = getDoubleAttribute("experimentTime", -1.0);
            String systemTimeStr = getStringAttribute("systemTime");
            if (systemTimeStr == null)
                throw new phyphoxFileException("An event requires both, an experiment time and a system time.", xpp.getLineNumber());
            Long systemTime = Long.parseLong(systemTimeStr);
            if (experimentTime < 0 || systemTime < 0)
                throw new phyphoxFileException("An event requires both, an experiment time and a system time.", xpp.getLineNumber());
            experiment.experimentTimeReference.timeMappings.add(experiment.experimentTimeReference.new TimeMapping(event, experimentTime, 0, systemTime));
        }

    }

    //Blockparser for the views block
    private static class viewsBlockParser extends xmlBlockParser {

        viewsBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "view": //A view defines an arangement of elements displayed to the user
                    ExpView newView = new ExpView(); //Create a new view
                    newView.name = getTranslatedAttribute("label"); //Fill its name
                    (new viewBlockParser(xpp, experiment, parent, newView)).process(); //And load its elements
                    if (newView.name != null && newView.elements.size() > 0) //We will only add it if it has a name and at least a single view
                        experiment.experimentViews.add(newView);
                    else {
                        //No name or no views. Complain!
                        throw new phyphoxFileException("Invalid view.", xpp.getLineNumber());
                    }
                    break;
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for a single view block
    private static class viewBlockParser extends xmlBlockParser {
        private ExpView newView;

        GraphView.scaleMode parseScaleMode(String attribute) {
            String scaleStr = getStringAttribute(attribute);
            GraphView.scaleMode scale = GraphView.scaleMode.auto;
            if (scaleStr != null) {
                switch (scaleStr) {
                    case "auto": scale = GraphView.scaleMode.auto;
                        break;
                    case "extend": scale = GraphView.scaleMode.extend;
                        break;
                    case "fixed": scale = GraphView.scaleMode.fixed;
                        break;
                }
            }
            return scale;
        }

        //The viewBlockParser takes an additional argument, which is the expView instance it should fill
        viewBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, ExpView newView) {
            super(xpp, experiment, parent);
            this.newView = newView;
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            String label = getTranslatedAttribute("label");
            double factor = getDoubleAttribute("factor", 1.);
            String unit = getTranslatedAttribute("unit");
            Vector<DataInput> inputs = new Vector<>();
            Vector<DataOutput> outputs = new Vector<>();
            switch (tag.toLowerCase()) {
                case "value": { //A value element displays a single value to the user
                    int precision = getIntAttribute("precision", 2);
                    boolean scientific = getBooleanAttribute("scientific", false);
                    double size = getDoubleAttribute("size", 1.0);
                    int color = getColorAttribute("color", parent.getResources().getColor(R.color.mainExp));

                    //Allowed input/output configuration
                    Vector<ioBlockParser.AdditionalTag> ats = new Vector<>();
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, null, ats)).process(); //Load inputs and outputs

                    Vector<String> inStrings = new Vector<>();
                    inStrings.add(inputs.get(0).buffer.name);
                    ExpView.valueElement ve = newView.new valueElement(label, null, inStrings, parent.getResources()); //Only a value input
                    for (ioBlockParser.AdditionalTag at : ats) {
                        if (at.name.equals("input"))
                            continue;
                        if (!at.name.equals("map")) {
                            throw new phyphoxFileException("Unknown tag "+at.name+" found by ioBlockParser.", xpp.getLineNumber());
                        }
                        ExpView.valueElement.Mapping map = ve.new Mapping(translate(at.content));
                        if (at.attributes.containsKey("min")) {
                            try {
                                map.min = Double.valueOf(at.attributes.get("min"));
                            } catch (Exception e) {
                                throw new phyphoxFileException("Could not parse min of map tag.", xpp.getLineNumber());
                            }
                        }
                        if (at.attributes.containsKey("max")) {
                            try {
                                map.max = Double.valueOf(at.attributes.get("max"));
                            } catch (Exception e) {
                                throw new phyphoxFileException("Could not parse max of map tag.", xpp.getLineNumber());
                            }
                        }
                        ve.addMapping(map);
                    }
                    ve.setPrecision(precision); //Floating point precision
                    ve.setScientificNotation(scientific); //Scientific notation vs. fixed point
                    ve.setUnit(unit); //We can have a unit after the value
                    ve.setFactor(factor); //A conversion factor. Usually for the unit
                    ve.setSize(size); //A conversion factor. Usually for the unit
                    ve.setColor(color);
                    newView.elements.add(ve);
                    break;
                }
                case "info": { //An info element just shows some text
                    int color = getColorAttribute("color", parent.getResources().getColor(R.color.mainExp));
                    boolean bold = getBooleanAttribute("bold", false);
                    boolean italic = getBooleanAttribute("italic", false);
                    String gravityString = getStringAttribute("align");
                    int gravity = Gravity.START;
                    if (gravityString != null && gravityString.equals("right"))
                        gravity = Gravity.END;
                    else if (gravityString != null && gravityString.equals("center"))
                        gravity = Gravity.CENTER;
                    float size = (float)getDoubleAttribute("size", 1.0);

                    ExpView.infoElement infoe = newView.new infoElement(label, null, null, parent.getResources()); //No inputs, just the label and resources
                    infoe.setColor(color);
                    infoe.setFormatting(bold, italic, gravity, size);
                    newView.elements.add(infoe);
                    break;
                }
                case "separator": //An info element just shows some text
                    ExpView.separatorElement separatore = newView.new separatorElement(null, null, parent.getResources()); //No inputs, just the label and resources
                    int c = getColorAttribute("color", parent.getResources().getColor(R.color.backgroundExp));
                    float height = (float)getDoubleAttribute("height", 0.1);
                    separatore.setColor(c);
                    separatore.setHeight(height);
                    newView.elements.add(separatore);
                    break;
                case "graph": { //A graph element displays a graph of an y array or two arrays x and y
                    double aspectRatio = getDoubleAttribute("aspectRatio", 2.5);
                    String lineStyle = getStringAttribute("style"); //Line style defaults to "line", but may be "dots"
                    int mapWidth= getIntAttribute("mapWidth", 0);
                    boolean partialUpdate = getBooleanAttribute("partialUpdate", false);
                    int history = getIntAttribute("history", 1);
                    String labelX = getTranslatedAttribute("labelX");
                    String labelY = getTranslatedAttribute("labelY");
                    String labelZ = getTranslatedAttribute("labelZ");
                    String unitX = getTranslatedAttribute("unitX");
                    String unitY = getTranslatedAttribute("unitY");
                    String unitZ = getTranslatedAttribute("unitZ");
                    String unitYX = getTranslatedAttribute("unitYperX");

                    Vector<Integer> colorScale = new Vector<>();
                    int colorStepIndex = 1;
                    while (xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE,"mapColor"+colorStepIndex) != null) {
                        int color = getColorAttribute("mapColor"+colorStepIndex, parent.getResources().getColor(R.color.highlight));
                        colorScale.add(color);
                        colorStepIndex++;
                    }

                    if (unitX == null && unitY == null && unitZ == null && labelX != null && labelY != null) {
                        Pattern pattern = Pattern.compile("^(.+)\\ \\((.+)\\)$");

                        Matcher matcherX = pattern.matcher(labelX);
                        if (matcherX.find()) {
                            labelX =  matcherX.group(1);
                            unitX =  matcherX.group(2);
                        }

                        Matcher matcherY = pattern.matcher(labelY);
                        if (matcherY.find()) {
                            labelY =  matcherY.group(1);
                            unitY =  matcherY.group(2);
                        }

                        if (labelZ != null) {
                            Matcher matcherZ = pattern.matcher(labelZ);
                            if (matcherZ.find()) {
                                labelZ = matcherZ.group(1);
                                unitZ = matcherZ.group(2);
                            }
                        }
                    }
                    boolean timeOnX = getBooleanAttribute("timeOnX", false);
                    boolean timeOnY = getBooleanAttribute("timeOnY", false);
                    boolean systemTime = getBooleanAttribute("systemTime", false);
                    boolean linearTime = getBooleanAttribute("linearTime", false);
                    boolean logX = getBooleanAttribute("logX", false);
                    boolean logY = getBooleanAttribute("logY", false);
                    boolean logZ = getBooleanAttribute("logZ", false);
                    double lineWidth = getDoubleAttribute("lineWidth", 1.0);
                    int xPrecision = getIntAttribute("xPrecision", 3);
                    int yPrecision = getIntAttribute("yPrecision", 3);
                    int zPrecision = getIntAttribute("zPrecision", 3);
                    int color = parent.getResources().getColor(R.color.highlight);
                    boolean globalColor = false;
                    if (xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "color") != null) {
                        color = getColorAttribute("color", parent.getResources().getColor(R.color.highlight));
                        globalColor = true;
                    }

                    GraphView.scaleMode scaleMinX = parseScaleMode("scaleMinX");
                    GraphView.scaleMode scaleMaxX = parseScaleMode("scaleMaxX");
                    GraphView.scaleMode scaleMinY = parseScaleMode("scaleMinY");
                    GraphView.scaleMode scaleMaxY = parseScaleMode("scaleMaxY");
                    GraphView.scaleMode scaleMinZ = parseScaleMode("scaleMinZ");
                    GraphView.scaleMode scaleMaxZ = parseScaleMode("scaleMaxZ");

                    double minX = getDoubleAttribute("minX", 0.);
                    double maxX = getDoubleAttribute("maxX", 0.);
                    double minY = getDoubleAttribute("minY", 0.);
                    double maxY = getDoubleAttribute("maxY", 0.);
                    double minZ = getDoubleAttribute("minZ", 0.);
                    double maxZ = getDoubleAttribute("maxZ", 0.);


                    //Allowed input/output configuration
                    Vector<ioBlockParser.AdditionalTag> ats = new Vector<>();
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0;}},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 0; valueAllowed = false; repeatableOffset = 1;}},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 0; maxCount = 0; valueAllowed = false; repeatableOffset = 2;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, "axis", ats)).process(); //Load inputs and outputs

                    Vector<String> inStrings = new Vector<>();
                    for (int i = 0; i < inputs.size(); i++) {
                        if (i % 3 == 2) {
                            //This is a z entry. For efficiency reasons, we only handle x and y and encode z as an additional graph of different style
                            if (inputs.get(i) != null) {
                                inStrings.add(inputs.get(i).buffer.name);
                                inStrings.add(null);
                                ioBlockParser.AdditionalTag at = new ioBlockParser.AdditionalTag();
                                at.name = ats.get(i).name;
                                at.attributes.put("style", "mapZ");
                                ats.add(i+1, at);
                            }
                        } else {
                            if (inputs.get(i) != null)
                                inStrings.add(inputs.get(i).buffer.name);
                            else
                                inStrings.add(null);
                        }
                    }

                    ExpView.graphElement ge = newView.new graphElement(label, null, inStrings, parent.getResources()); //Two array inputs
                    ge.setAspectRatio(aspectRatio); //Aspect ratio of the whole element area icluding axes


                    if (lineStyle != null) {
                        ge.setStyle(GraphView.styleFromStr(lineStyle));
                    }
                    ge.setMapWidth(mapWidth);
                    ge.setColorScale(colorScale);
                    ge.setLineWidth(lineWidth);
                    ge.setColor(color);
                    ge.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
                    ge.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
                    ge.setScaleModeZ(scaleMinZ, minZ, scaleMaxZ, maxZ);
                    ge.setPartialUpdate(partialUpdate); //Will data only be appended? Will save bandwidth if we do not need to update the whole graph each time, especially on the web-interface
                    ge.setHistoryLength(history); //If larger than 1 the previous n graphs remain visible in a different color
                    ge.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);  //x- and y- label and units
                    ge.setTimeAxes(timeOnX, timeOnY, systemTime, linearTime);
                    ge.setLogScale(logX, logY, logZ); //logarithmic scales for x/y axes
                    ge.setPrecision(xPrecision, yPrecision, zPrecision); //logarithmic scales for x/y axes
                    if (!globalColor) {
                        for (int i = 0; i < Math.ceil(ats.size() / 3); i++) {
                            switch (i % 6) {
                                case 0: ge.setColor(parent.getResources().getColor(R.color.presetOrange), i);
                                    break;
                                case 1: ge.setColor(parent.getResources().getColor(R.color.presetGreen), i);
                                    break;
                                case 2: ge.setColor(parent.getResources().getColor(R.color.presetBlue), i);
                                    break;
                                case 3: ge.setColor(parent.getResources().getColor(R.color.presetYellow), i);
                                    break;
                                case 4: ge.setColor(parent.getResources().getColor(R.color.presetMagenta), i);
                                    break;
                                case 5: ge.setColor(parent.getResources().getColor(R.color.presetRed), i);
                                    break;
                            }
                        }
                    }
                    for (int i = 0; i < ats.size(); i++) {
                        ioBlockParser.AdditionalTag at = ats.get(i);
                        if (at == null)
                            continue;
                        if (!at.name.equals("input")) {
                            throw new phyphoxFileException("Unknown tag "+at.name+" found by ioBlockParser.", xpp.getLineNumber());
                        }
                        if (at.attributes.containsKey("style")) {
                            try {
                                GraphView.Style style = GraphView.styleFromStr(at.attributes.get("style"));
                                if (style == GraphView.Style.unknown)
                                    throw new phyphoxFileException("Unknown value for style of input tag.", xpp.getLineNumber());
                                ge.setStyle(style, i/3);
                            } catch (Exception e) {
                                throw new phyphoxFileException("Could not parse style of input tag.", xpp.getLineNumber());
                            }
                        }
                        if (at.attributes.containsKey("color")) {
                            int localColor = Helper.parseColor(at.attributes.get("color"), parent.getResources().getColor(R.color.presetOrange), parent.getResources());
                            ge.setColor(localColor | 0xff000000, i/3);
                        }
                        if (at.attributes.containsKey("linewidth")) {
                            try {
                                ge.setLineWidth(Double.valueOf(at.attributes.get("linewidth")), i/3);
                            } catch (Exception e) {
                                throw new phyphoxFileException("Could not parse linewidth of input tag.", xpp.getLineNumber());
                            }
                        }
                        if (at.attributes.containsKey("mapwidth")) {
                            try {
                                ge.setMapWidth(Integer.valueOf(at.attributes.get("mapwidth")), i/3);
                            } catch (Exception e) {
                                throw new phyphoxFileException("Could not parse mapWidth of input tag.", xpp.getLineNumber());
                            }
                        }
                    }

                    newView.elements.add(ge);
                    break;
                }
                case "edit": { //The edit element can take input from the user
                    boolean signed = getBooleanAttribute("signed", true);
                    boolean decimal = getBooleanAttribute("decimal", true);
                    double defaultValue = getDoubleAttribute("default", 0.);

                    double min = getDoubleAttribute("min", Double.NEGATIVE_INFINITY);
                    double max = getDoubleAttribute("max", Double.POSITIVE_INFINITY);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; }}
                    };
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, null)).process(); //Load inputs and outputs

                    ExpView.editElement ie = newView.new editElement(label, outputs.get(0).buffer.name, null, parent.getResources()); //Ouput only
                    ie.setUnit(unit); //A unit displayed next to the input box
                    ie.setFactor(factor); //A scaling factor. Mostly for matching units
                    ie.setSigned(signed); //May the entered number be negative?
                    ie.setDecimal(decimal); //May the user enter a decimal point (non-integer values)?
                    ie.setDefaultValue(defaultValue); //Default value before the user entered anything
                    ie.setLimits(min, max);
                    newView.elements.add(ie);
                    break;
                }
                case "button": { //The edit element can take input from the user
                    //Allowed input/output configuration
                    Vector<ioBlockParser.AdditionalTag> ats = new Vector<>();
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 0; maxCount = 0; valueAllowed = true; emptyAllowed = true; repeatableOffset = 0;}},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 0; maxCount = 0; repeatableOffset = 0;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, null, ats)).process(); //Load inputs and outputs

                    ExpView.buttonElement be = newView.new buttonElement(label, null, null, parent.getResources()); //This one is user-event driven and does not regularly read or write values
                    be.setIO(inputs, outputs);
                    Vector<String> triggers = new Vector<>();
                    for (ioBlockParser.AdditionalTag at : ats) {
                        if (at.name.equals("input"))
                            continue;
                        if (at.name.equals("output"))
                            continue;
                        if (!at.name.equals("trigger")) {
                            throw new phyphoxFileException("Unknown tag " + at.name + " found by ioBlockParser.", xpp.getLineNumber());
                        }
                        triggers.add(at.content);
                    }
                    be.setTriggers(triggers);
                    newView.elements.add(be);
                    break;
                }
                case "svg": //A (parametric) svg image
                    //Allowed input/output configuration
                    Vector<ioBlockParser.AdditionalTag> ats = new Vector<>();
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 0; maxCount = 0; valueAllowed = false; repeatableOffset = 0;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, null, ats)).process(); //Load inputs and outputs

                    Vector<String> inStrings = new Vector<>();
                    for (DataInput input : inputs)
                        inStrings.add(input.buffer.name);

                    ExpView.svgElement svge = newView.new svgElement(null, null, inStrings, parent.getResources()); //No inputs, just the label and resources

                    String svgCode = null;
                    for (ioBlockParser.AdditionalTag at : ats) {
                        if (at.name.equals("input"))
                            continue;
                        if (!at.name.equals("source")) {
                            throw new phyphoxFileException("Unknown tag " + at.name + " found by ioBlockParser.", xpp.getLineNumber());
                        }
                        svgCode = at.content;
                    }

                    if (svgCode == null) {
                        throw new phyphoxFileException("SVG source code missing.", xpp.getLineNumber());
                    } else
                        svge.setSvgParts(svgCode);

                    int color = getColorAttribute("color", parent.getResources().getColor(R.color.backgroundExp));
                    svge.setBackgroundColor(color);

                    newView.elements.add(svge);
                    break;
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the input block
    private static class inputBlockParser extends xmlBlockParser {

        inputBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "sensor": { //A sensor input (in the sense of android sensor)
                    double rate = getDoubleAttribute("rate", 0.); //Aquisition rate (we always request fastest rate, but average or just pick every n-th readout)
                    boolean average = getBooleanAttribute("average", false); //Average if we have a lower rate than the sensor can deliver?

                    String type = getStringAttribute("type");
                    boolean ignoreUnavailable = getBooleanAttribute("ignoreUnavailable", false);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "t"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "abs"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "accuracy"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}}
                    };
                    Vector<DataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, "component")).process(); //Load inputs and outputs

                    //Add a sensor. If the string is unknown, sensorInput throws a phyphoxFileException
                    try {
                        experiment.inputSensors.add(new SensorInput(type, ignoreUnavailable, rate, average, outputs, experiment.dataLock, experiment.experimentTimeReference));
                        experiment.inputSensors.lastElement().attachSensorManager(parent.sensorManager);
                    } catch (SensorInput.SensorException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber());
                    }

                    //Check if the sensor is available on this device
                    if (!(experiment.inputSensors.lastElement().isAvailable() || experiment.inputSensors.lastElement().ignoreUnavailable)) {
                        throw new phyphoxFileException(parent.getResources().getString(R.string.sensorNotAvailableWarningText1) + " " + parent.getResources().getString(experiment.inputSensors.lastElement().getDescriptionRes()) + " " + parent.getResources().getString(R.string.sensorNotAvailableWarningText2));
                    }
                    break;
                }
                case "location": { //GPS input
                    //Check for recording permission
                    if (ContextCompat.checkSelfPermission(parent, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        //No permission? Request it (Android 6+, only)
                        ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                        throw new phyphoxFileException("Need permission to receive location through GPS."); //We will throw an error here, but when the user grants the permission, the activity will be restarted from the permission callback
                    }

                    //Allowed input/output configuration
                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "lat"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "lon"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "zwgs84"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "v"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "dir"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "t"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "accuracy"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "zAccuracy"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "status"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "satellites"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}}
                    };
                    Vector<DataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, "component")).process(); //Load inputs and outputs

                    experiment.gpsIn = new GpsInput(outputs, experiment.dataLock, experiment.experimentTimeReference);
                    experiment.gpsIn.attachLocationManager((LocationManager)parent.getSystemService(Context.LOCATION_SERVICE));

                    if (!GpsInput.isAvailable(parent)) {
                        throw new phyphoxFileException(parent.getResources().getString(R.string.sensorNotAvailableWarningText1) + " " + parent.getResources().getString(R.string.location) + " " + parent.getResources().getString(R.string.sensorNotAvailableWarningText2));
                    }

                    break;
                }
                case "audio": { //Audio input, aka microphone
                    //Check for recording permission
                    if (ContextCompat.checkSelfPermission(parent, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        //No permission? Request it (Android 6+, only)
                        ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                        throw new phyphoxFileException("Need permission to record audio."); //We will throw an error here, but when the user grants the permission, the activity will be restarted from the permission callback
                    }
                    experiment.micRate = getIntAttribute("rate", 48000); //Recording rate

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "rate"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}}
                    };
                    Vector<DataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, "component")).process(); //Load inputs and outputs

                    experiment.micOutput = outputs.get(0).buffer.name;
                    experiment.micBufferSize = outputs.get(0).size()*2; //Output-buffer size
                    if (outputs.size() > 1)
                        experiment.micRateOutput = outputs.get(1).buffer.name;
                    else
                        experiment.micRateOutput = "";

                    //Devices have a minimum buffer size. We might need to increase our buffer...
                    experiment.minBufferSize = AudioRecord.getMinBufferSize(experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)/2;
                    if (experiment.minBufferSize < 0) {
                        throw new phyphoxFileException("Could not initialize recording. (" + experiment.minBufferSize + ")", xpp.getLineNumber());
                    }
                    if (experiment.minBufferSize > experiment.micBufferSize) {
                        experiment.micBufferSize = experiment.minBufferSize;
                        Log.w("loadExperiment", "Audio buffer size had to be adjusted to " + experiment.minBufferSize);
                    }

                    break;
                }
                case "bluetooth": { //A bluetooth input
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent)) {
                            throw new phyphoxFileException(parent.getResources().getString(R.string.bt_android_version));
                        } else {
                            double rate = getDoubleAttribute("rate", 0.); //Aquisition rate

                            String idString = getTranslatedAttribute("id");

                            String nameFilter = getStringAttribute("name");
                            String addressFilter = getStringAttribute("address");
                            String uuidFilterStr = getStringAttribute("uuid");
                            UUID uuidFilter = null;
                            if (uuidFilterStr != null && !uuidFilterStr.isEmpty()) {
                                try {
                                    uuidFilter = UUID.fromString(uuidFilterStr);
                                } catch (Exception e) {
                                    throw new phyphoxFileException("Invalid UUID: " + uuidFilterStr, xpp.getLineNumber());
                                }
                            }
                            Boolean autoConnect = getBooleanAttribute("autoConnect", false);

                            String modeStr = getStringAttribute("mode");
                            if (modeStr == null)
                                modeStr = "notification";
                            else
                                modeStr = modeStr.toLowerCase();

                            String modeFilter;
                            switch (modeStr) {
                                case "poll": {
                                    modeFilter = "poll";
                                    break;
                                }
                                case "notification": {
                                    modeFilter = "notification";
                                    break;
                                }
                                case "indication": {
                                    modeFilter = "indication";
                                    break;
                                }
                                default: {
                                    throw new phyphoxFileException("Unknown bluetooth mode: " + modeStr, xpp.getLineNumber());
                                }
                            }

                            boolean subscribeOnStart = getBooleanAttribute("subscribeOnStart", false);
                            int mtu = getIntAttribute("mtu", 0);

                            Vector<DataOutput> outputs = new Vector<>();
                            Vector<Bluetooth.CharacteristicData> characteristics = new Vector<>();
                            (new bluetoothIoBlockParser(xpp, experiment, parent, outputs, null, characteristics)).process();
                            try {
                                BluetoothInput b = new BluetoothInput(idString, nameFilter, addressFilter, modeFilter, uuidFilter, autoConnect, rate, subscribeOnStart, outputs, experiment.dataLock, parent, parent, characteristics, experiment.experimentTimeReference);
                                if (mtu > 0)
                                    b.requestMTU = mtu;
                                experiment.bluetoothInputs.add(b);
                            } catch (phyphoxFileException e) {
                                throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber()); // throw it again with LineNumber
                            }
                        }
                        break;
                }
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the network block
    private static class networkBlockParser extends xmlBlockParser {

        networkBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "connection":
                    String id = getStringAttribute("id");
                    String privacyURL = getStringAttribute("privacy");
                    String address = getStringAttribute("address");
                    String discoveryAddress = getStringAttribute("discoveryAddress");
                    boolean autoConnect = getBooleanAttribute("autoConnect", false);
                    double interval = getDoubleAttribute("interval", 0.0);

                    String discoveryStr = getStringAttribute("discovery");
                    NetworkDiscovery.Discovery discovery = null;
                    if (discoveryStr != null) {
                        switch (discoveryStr) {
                            case "http":
                                discovery = new NetworkDiscovery.Http(discoveryAddress);
                                break;
                            default:
                                throw new phyphoxFileException("Unknown discovery "+discoveryStr, xpp.getLineNumber());
                        }
                    }

                    String serviceStr = getStringAttribute("service");
                    NetworkService.Service service = null;
                    if (serviceStr != null) {
                        switch (serviceStr) {
                            case "http/get":
                                service = new NetworkService.HttpGet();
                                break;
                            case "http/post":
                                service = new NetworkService.HttpPost();
                                break;
                            case "mqtt/csv": {
                                    String receiveTopicStr = getStringAttribute("receiveTopic");
                                    if (receiveTopicStr == null)
                                        receiveTopicStr = "";
                                    service = new MqttCsv(receiveTopicStr, parent.getApplicationContext());
                                }
                                break;
                            case "mqtt/json": {
                                    String receiveTopicStr = getStringAttribute("receiveTopic");
                                    String sendTopicStr = getStringAttribute("sendTopic");
                                    boolean persistence = getBooleanAttribute("persistence",false);
                                    if (receiveTopicStr == null)
                                        receiveTopicStr = "";
                                    if (sendTopicStr == null || sendTopicStr.isEmpty())
                                        throw new phyphoxFileException("sendTopic must be set for the mqtt/json service. Use mqtt/csv if you do not intent to send anything.", xpp.getLineNumber());
                                    service = new MqttJson(receiveTopicStr, sendTopicStr, parent.getApplicationContext(),persistence);
                                }
                                break;
                            case "mqtts/json" : {
                                String receiveTopicStr = getStringAttribute("receiveTopic");
                                String sendTopicStr = getStringAttribute("sendTopic");
                                String password = getStringAttribute("password");
                                String username = getStringAttribute("username");
                                boolean persistence = getBooleanAttribute("persistence",false);

                                if (receiveTopicStr == null)
                                    receiveTopicStr = "";
                                if (sendTopicStr == null || sendTopicStr.isEmpty())
                                    throw new phyphoxFileException("sendTopic must be set for the mqtts/json service. Use mqtt/csv if you do not intent to send anything.", xpp.getLineNumber());
                                if (password == null || password.isEmpty())
                                    throw new phyphoxFileException("password must be set for the mqtts/json service.", xpp.getLineNumber());
                                if (username == null || username.isEmpty())
                                    throw new phyphoxFileException("username must be set for the mqtts/json service.", xpp.getLineNumber());
                                service = new MqttTlsJson(receiveTopicStr,sendTopicStr,username,password,parent.getApplicationContext(),persistence);
                            }
                            break;
                            case "mqtts/csv" : {
                                String receiveTopicStr = getStringAttribute("receiveTopic");
                                String password = getStringAttribute("password");
                                String username = getStringAttribute("username");

                                if (receiveTopicStr == null)
                                    receiveTopicStr = "";
                                if (password == null || password.isEmpty())
                                    throw new phyphoxFileException("password must be set for the mqtts/csv service.", xpp.getLineNumber());
                                if (username == null || username.isEmpty())
                                    throw new phyphoxFileException("username must be set for the mqtts/csv service.", xpp.getLineNumber());
                                service = new MqttTlsCsv(receiveTopicStr,username,password,parent.getApplicationContext());
                            }
                            break;
                            default:
                                throw new phyphoxFileException("Unknown service "+serviceStr, xpp.getLineNumber());
                        }
                    }

                    String conversionStr = getStringAttribute("conversion");
                    NetworkConversion.Conversion conversion = null;
                    if (conversionStr != null) {
                        switch (conversionStr) {
                            case "none":
                                conversion = new NetworkConversion.None();
                                break;
                            case "csv":
                                conversion = new NetworkConversion.Csv();
                                break;
                            case "json":
                                conversion = new NetworkConversion.Json();
                                break;
                            default:
                                throw new phyphoxFileException("Unknown conversion "+conversionStr, xpp.getLineNumber());
                        }
                    } else
                        conversion = new NetworkConversion.None();

                    Map<String, NetworkConnection.NetworkSendableData> send = new HashMap<>();
                    Map<String, NetworkConnection.NetworkReceivableData> receive = new HashMap<>();


                    (new networkConnectionBlockParser(xpp, experiment, parent, send, receive)).process();

                    experiment.networkConnections.add(new NetworkConnection(id, privacyURL, address, discovery, autoConnect, service, conversion, send, receive, interval, parent));

                    break;
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for a connection block within the network block
    private static class networkConnectionBlockParser extends xmlBlockParser {
        Map<String, NetworkConnection.NetworkSendableData> send;
        Map<String, NetworkConnection.NetworkReceivableData> receive;

        networkConnectionBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, Map<String, NetworkConnection.NetworkSendableData> send, Map<String, NetworkConnection.NetworkReceivableData> receive) {
            super(xpp, experiment, parent);
            this.send = send;
            this.receive = receive;
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "send": {
                    NetworkConnection.NetworkSendableData sendable;

                    String id = getStringAttribute("id");
                    if (id == null)
                        throw new phyphoxFileException("Missing id in send element.", xpp.getLineNumber());

                    String datatype = getStringAttribute("datatype");

                    String type = getStringAttribute("type");
                    if (type == null || type.equals("buffer")) {
                        boolean clear = getBooleanAttribute("clear", false);
                        String bufferName = getText();
                        DataBuffer buffer = experiment.getBuffer(bufferName);
                        if (buffer == null)
                            throw new phyphoxFileException("Buffer \"" + bufferName + "\" not defined.", xpp.getLineNumber());
                        sendable = new NetworkConnection.NetworkSendableData(buffer, clear);
                        if (datatype != null) {
                            sendable.additionalAttributes = new HashMap<>();
                            sendable.additionalAttributes.put("datatype", datatype);
                        }
                    } else if (type.equals("meta")) {
                        String metaName = getText();
                        try {
                            sendable = new NetworkConnection.NetworkSendableData(new Metadata(metaName, parent));
                        } catch (IllegalArgumentException e) {
                            throw new phyphoxFileException("Unknown meta data \"" + metaName + "\".", xpp.getLineNumber());
                        }
                    } else if (type.equals("time")) {
                        sendable = new NetworkConnection.NetworkSendableData(experiment.experimentTimeReference);
                    } else {
                        throw new phyphoxFileException("Unknown type \"" + type + "\".", xpp.getLineNumber());
                    }
                    send.put(id, sendable);
                    break;
                }
                case "receive": {
                    NetworkConnection.NetworkReceivableData receivable;

                    String id = getStringAttribute("id");
                    if (id == null)
                        throw new phyphoxFileException("Missing id in receive element.", xpp.getLineNumber());

                    boolean clear = getBooleanAttribute("clear", false);

                    String bufferName = getText();
                    DataBuffer buffer = experiment.getBuffer(bufferName);
                    if (buffer == null)
                        throw new phyphoxFileException("Buffer \"" + bufferName + "\" not defined.", xpp.getLineNumber());
                    receivable = new NetworkConnection.NetworkReceivableData(buffer, clear);

                    receive.put(id, receivable);
                    break;
                }
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the analysis block
    private static class analysisBlockParser extends xmlBlockParser {

        analysisBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {

            Vector<Analysis.AnalysisModule.CycleRange> cycles = new Vector<>();
            String cyclesStr = getStringAttribute("cycles");
            if (cyclesStr != null) {
                for (String cycleStr : cyclesStr.split(" ")) {
                    String[] cycleParts = cycleStr.trim().split("-", 3);
                    if (cycleParts.length == 1) {
                        try {
                            int value = Integer.parseInt(cycleParts[0]);
                            cycles.add(new Analysis.AnalysisModule.CycleRange(value, value));
                        } catch (Exception e) {
                            throw new phyphoxFileException("Invalid cycles attribute "+cyclesStr+".", xpp.getLineNumber());
                        }
                    } else if (cycleParts.length == 2) {
                        try {
                            int start, stop;
                            if (cycleParts[0].length() == 0)
                                start = -1;
                            else
                                start = Integer.parseInt(cycleParts[0]);
                            if (cycleParts[1].length() == 0)
                                stop = -1;
                            else
                                stop = Integer.parseInt(cycleParts[1]);
                            cycles.add(new Analysis.AnalysisModule.CycleRange(start, stop));
                        } catch (Exception e) {
                            throw new phyphoxFileException("Invalid cycles attribute "+cyclesStr+".", xpp.getLineNumber());
                        }
                    } else {
                        throw new phyphoxFileException("Invalid cycles attribute "+cyclesStr+".", xpp.getLineNumber());
                    }
                }
            }
            //The cycles string is simply set after the analysis module is instantiated below

            Vector<DataInput> inputs = new Vector<>(); //Will hold the inputs
            Vector<DataOutput> outputs = new Vector<>(); //Will hold the output buffers

            switch (tag.toLowerCase()) {
                case "timer": { //Start-time of analysis

                    boolean linearTime = getBooleanAttribute("linearTime", false);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "offset1970"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.timerAM(experiment, inputs, outputs, linearTime));
                } break;
                case "formula": {
                    String formula = getStringAttribute("formula");

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 0; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    if (formula == null)
                        throw new phyphoxFileException("Formula module needs a formula.", xpp.getLineNumber());
                    try {
                        experiment.analysis.add(new Analysis.formulaAM(experiment, inputs, outputs, formula));
                    } catch (FormulaParser.FormulaException e) {
                        throw new phyphoxFileException("Formula error: " + e.getMessage(), xpp.getLineNumber());
                    }
                } break;
                case "count": { //Absolute value

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "buffer"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "count"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.countAM(experiment, inputs, outputs));
                } break;
                case "if": { //Absolute value

                    boolean less = getBooleanAttribute("less", false);
                    boolean equal = getBooleanAttribute("equal", false);
                    boolean greater = getBooleanAttribute("greater", false);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "a"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "b"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "true"; asRequired = false; minCount = 0; maxCount = 1; valueAllowed = true; emptyAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "false"; asRequired = false; minCount = 0; maxCount = 1; valueAllowed = true; emptyAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "result"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.ifAM(experiment, inputs, outputs, less, equal, greater));
                } break;
                case "average": { //Absolute value

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "buffer"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "average"; asRequired = false; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "stddev"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.averageAM(experiment, inputs, outputs));
                } break;
                case "add": { //input1+input2+input3...

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "summand"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "sum"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.addAM(experiment, inputs, outputs));
                } break;
                case "subtract": { //input1-input2-input3...

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "minuend"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "subtrahend"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "difference"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.subtractAM(experiment, inputs, outputs));
                } break;
                case "multiply": { //input1*input2*input3...

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "factor"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "product"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.multiplyAM(experiment, inputs, outputs));
                } break;
                case "divide": { //input1/input2/input3...

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "dividend"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "divisor"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "quotient"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.divideAM(experiment, inputs, outputs));
                } break;
                case "power": {//(input1^input2)

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "base"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "exponent"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "power"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.powerAM(experiment, inputs, outputs));
                } break;
                case "gcd": { //Greatest common divisor

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 2; maxCount = 2; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "gcd"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.gcdAM(experiment, inputs, outputs));
                } break;
                case "lcm": { //Least common multiple

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 2; maxCount = 2; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "lcm"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.lcmAM(experiment, inputs, outputs));
                } break;
                case "abs": { //Absolute value

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "abs"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.absAM(experiment, inputs, outputs));
                } break;
                case "round": { //Round
                    boolean floor = getBooleanAttribute("floor", false);
                    boolean ceil = getBooleanAttribute("ceil", false);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "round"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.roundAM(experiment, inputs, outputs, floor, ceil));
                } break;
                case "log": { //nat. logarithm
                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "log"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.logAM(experiment, inputs, outputs));
                } break;
                case "sin": { //Sine
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "sin"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.sinAM(experiment, inputs, outputs, deg));
                } break;
                case "cos": { //Cosine
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "cos"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.cosAM(experiment, inputs, outputs, deg));
                } break;
                case "tan": { //Tangens
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "tan"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.tanAM(experiment, inputs, outputs, deg));
                } break;
                case "sinh": { //Sine

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "sinh"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.sinhAM(experiment, inputs, outputs));
                } break;
                case "cosh": { //Cosine

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "cosh"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.coshAM(experiment, inputs, outputs));
                } break;
                case "tanh": { //Tangens

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "tanh"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.tanhAM(experiment, inputs, outputs));
                } break;
                case "asin": { //Sine
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "asin"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.asinAM(experiment, inputs, outputs, deg));
                } break;
                case "acos": { //Cosine
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "acos"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.acosAM(experiment, inputs, outputs, deg));
                } break;
                case "atan": { //Tangens
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "atan"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.atanAM(experiment, inputs, outputs, deg));
                } break;
                case "atan2": { //Tangens
                    boolean deg = getBooleanAttribute("deg", false); //Use degree instead of radians

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "atan2"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.atan2AM(experiment, inputs, outputs, deg));
                } break;
                case "first": { //First value of each buffer

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "first"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.firstAM(experiment, inputs, outputs));
                } break;
                case "max": { //Maximum (takes y as first input and may take x as an optional second, same for outputs)
                    boolean multiple = getBooleanAttribute("multiple", false); //Positive or negative flank

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "threshold"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "max"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "position"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.maxAM(experiment, inputs, outputs, multiple));
                } break;
                case "min": { //Minimum (takes y as first input and may take x as an optional second, same for outputs)
                    boolean multiple = getBooleanAttribute("multiple", false); //Positive or negative flank

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "threshold"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "min"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "position"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.minAM(experiment, inputs, outputs, multiple));
                } break;
                case "threshold": { //Find the index at which the input crosses a threshold
                    boolean falling = getBooleanAttribute("falling", false); //Positive or negative flank

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "threshold"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "position"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.thresholdAM(experiment, inputs, outputs, falling));
                } break;
                case "binning": { //count number of values falling into binning ranges
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "x0"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "dx"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "binStarts"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "binCounts"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.binningAM(experiment, inputs, outputs));
                } break;
                case "map": { //rearrange data from unsorted x, y, and z values suitable for map graphs
                    String zModeStr = getStringAttribute("zMode"); //Positive or negative flank
                    if (zModeStr == null)
                        zModeStr = "average";

                    Analysis.mapAM.ZMode zMode = Analysis.mapAM.ZMode.average;

                    switch (zModeStr) {
                        case "count":   zMode = Analysis.mapAM.ZMode.count;
                                        break;
                        case "sum":     zMode = Analysis.mapAM.ZMode.sum;
                                        break;
                        case "average": zMode = Analysis.mapAM.ZMode.average;
                                        break;
                        default:        throw new phyphoxFileException("Unknown zMode " + zModeStr, xpp.getLineNumber());
                    }

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "mapWidth"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "minX"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "maxX"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "mapHeight"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "minY"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "maxY"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 1; maxCount = 1; repeatableOffset = -1; }}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.mapAM(experiment, inputs, outputs, zMode));
                } break;
                case "append": { //Append the inputs to each other

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; emptyAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.appendAM(experiment, inputs, outputs));
                } break;
                case "reduce": { //Reduce number of entries in a buffer

                    boolean averageX = getBooleanAttribute("averageX", false);
                    boolean sumY= getBooleanAttribute("sumY", false);
                    boolean averageY = getBooleanAttribute("averageY", false);

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "factor"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.reduceAM(experiment, inputs, outputs, averageX, sumY, averageY));
                } break;
                case "fft": { //Fourier transform

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "re"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "im"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "re"; asRequired = false; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "im"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.fftAM(experiment, inputs, outputs));
                } break;
                case "autocorrelation": { //Autocorrelation. First in/out is y, second in/out may be x

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "minX"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "maxX"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    Analysis.autocorrelationAM acAM = new Analysis.autocorrelationAM(experiment, inputs, outputs);
                    experiment.analysis.add(acAM);
                } break;
                case "periodicity": { //Periodicity

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "dx"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "overlap"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "min"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "max"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "time"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "period"; asRequired = true; minCount = 0; maxCount = 1; repeatableOffset = -1; }}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    Analysis.periodicityAM pAM = new Analysis.periodicityAM(experiment, inputs, outputs);
                    experiment.analysis.add(pAM);
                } break;
                case "differentiate": { //Differentiate by subtracting neighboring values

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.differentiateAM(experiment, inputs, outputs));
                } break;
                case "integrate": { //Integration from first value of buffer to each point in buffer

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.integrateAM(experiment, inputs, outputs));
                } break;
                case "crosscorrelation": { //Crosscorrelation requires two inputs and a single output. y only.

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 2; maxCount = 2; valueAllowed = false; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.crosscorrelationAM(experiment, inputs, outputs));
                } break;
                case "gausssmooth": { //Smooth the data with a Gauss profile
                    double sigma = getDoubleAttribute("sigma", 0.);

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    Analysis.gaussSmoothAM gsAM = new Analysis.gaussSmoothAM(experiment, inputs, outputs);
                    if (sigma > 0)
                        gsAM.setSigma(sigma);
                    experiment.analysis.add(gsAM);
                } break;
                case "loess": { //Smooth data with LOESS

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "d"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "xi"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "yi0"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                            new ioBlockParser.ioMapping() {{name = "yi1"; asRequired = true; minCount = 0; maxCount = 0; repeatableOffset = 0; }},
                            new ioBlockParser.ioMapping() {{name = "yi2"; asRequired = true; minCount = 0; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.loessAM(experiment, inputs, outputs));
                } break;
                case "interpolate": { //Smooth data with LOESS
                    String interpolationMethodStr = getStringAttribute("method");
                    if (interpolationMethodStr == null)
                        interpolationMethodStr = "linear";

                    Analysis.interpolateAM.InterpolationMethod method = Analysis.interpolateAM.InterpolationMethod.linear;

                    switch (interpolationMethodStr) {
                        case "previous":   method = Analysis.interpolateAM.InterpolationMethod.previous;
                            break;
                        case "next":   method = Analysis.interpolateAM.InterpolationMethod.next;
                            break;
                        case "nearest":   method = Analysis.interpolateAM.InterpolationMethod.nearest;
                            break;
                        case "linear":   method = Analysis.interpolateAM.InterpolationMethod.linear;
                            break;
                        default:        throw new phyphoxFileException("Unknown interpolation methode " + interpolationMethodStr, xpp.getLineNumber());
                    }

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = false; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "xi"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.interpolateAM(experiment, inputs, outputs, method));
                } break;
                case "match": { //Arbitrary inputs and outputs, for each input[n] a min[n] and max[n] can be defined. The module filters the inputs in parallel and returns only those sets that match the filters

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.matchAM(experiment, inputs, outputs));
                } break;
                case "rangefilter": { //Arbitrary inputs and outputs, for each input[n] a min[n] and max[n] can be defined. The module filters the inputs in parallel and returns only those sets that match the filters

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }},
                            new ioBlockParser.ioMapping() {{name = "min"; asRequired = true; minCount = 0; maxCount = 0; valueAllowed = true; repeatableOffset = 1; }},
                            new ioBlockParser.ioMapping() {{name = "max"; asRequired = true; minCount = 0; maxCount = 0; valueAllowed = true; repeatableOffset = 2; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.rangefilterAM(experiment, inputs, outputs));
                } break;
                case "subrange": { //from, to or length may be defined, arbitrary number of additional inputs and outputs

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "from"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "to"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "length"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.subrangeAM(experiment, inputs, outputs));
                } break;
                case "sort": {

                    boolean descending= getBooleanAttribute("descending", false);

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.sortAM(experiment, inputs, outputs, descending));
                } break;
                case "ramp": { //Create a linear ramp (great for creating time-bases)

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "start"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "stop"; asRequired = true; minCount = 1; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "length"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    Analysis.rampGeneratorAM rampAM = new Analysis.rampGeneratorAM(experiment, inputs, outputs);
                    experiment.analysis.add(rampAM);
                } break;
                case "const": { //Initialize a buffer with constant values

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "value"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "length"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }}
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    Analysis.constGeneratorAM constAM = new Analysis.constGeneratorAM(experiment, inputs, outputs);
                    experiment.analysis.add(constAM);
                } break;
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
            experiment.analysis.lastElement().setCycles(cycles);
        }

    }

    //Blockparser for the output block
    private static class outputBlockParser extends xmlBlockParser {

        outputBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "audio": { //Audio output, aka speaker
                    boolean loop = getBooleanAttribute("loop", false); //Loop the output?
                    int rate = getIntAttribute("rate", 48000); //Sample frequency
                    boolean normalize = getBooleanAttribute("normalize", false); //Normalize amplitude of all inputs
                    AudioOutput audioOutput = new AudioOutput(loop, rate, normalize);
                    (new AudioOutputPluginBlockParser(xpp, experiment, parent, audioOutput)).process();
                    experiment.audioOutput = audioOutput;
                    break;

                }
                case "bluetooth": { //A bluetooth output
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent)) {
                        throw new phyphoxFileException(parent.getResources().getString(R.string.bt_android_version));
                    } else {
                        String idString = getTranslatedAttribute("id");
                        String nameFilter = getStringAttribute("name");
                        String addressFilter = getStringAttribute("address");
                        String uuidFilterStr = getStringAttribute("uuid");
                        UUID uuidFilter = null;
                        if (uuidFilterStr != null && !uuidFilterStr.isEmpty()) {
                            try {
                                uuidFilter = UUID.fromString(uuidFilterStr);
                            } catch (Exception e) {
                                throw new phyphoxFileException("Invalid UUID: " + uuidFilterStr, xpp.getLineNumber());
                            }
                        }
                        Boolean autoConnect = getBooleanAttribute("autoConnect", false);
                        int mtu = getIntAttribute("mtu", 0);

                        Vector<DataInput> inputs = new Vector<>();
                        Vector<Bluetooth.CharacteristicData> characteristics = new Vector<>();
                        (new bluetoothIoBlockParser(xpp, experiment, parent, null, inputs, characteristics)).process();
                        BluetoothOutput b = new BluetoothOutput(idString, nameFilter, addressFilter, uuidFilter, autoConnect, parent, parent, inputs, characteristics);
                        if (mtu > 0)
                            b.requestMTU = mtu;
                        experiment.bluetoothOutputs.add(b);
                    }
                    break;
                }
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the export block
    private static class exportBlockParser extends xmlBlockParser {

        exportBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, IOException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "set": //An export set. These just group some dataBuffers to be exported as a set
                    DataExport.ExportSet set = experiment.exporter.new ExportSet(xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "name")); //Create the set with the given name
                    (new setBlockParser(xpp, experiment, parent, set)).process(); //Parse the information within
                    experiment.exporter.addSet(set); //Add the set
                break;
                default:
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

        @Override
        protected void processEndTag(String tag) {

        }
    }

    //Blockparser for the individual set blocks
    private static class setBlockParser extends xmlBlockParser {
        private DataExport.ExportSet set;

        //This constructor takes an additional argument: The export set to be filled
        setBlockParser(XmlPullParser xpp, PhyphoxExperiment experiment, Experiment parent, DataExport.ExportSet set) {
            super(xpp, experiment, parent);
            this.set = set;
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "data": //Add this data buffer to the set
                    String name = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "name");
                    String src = getText();
                    if (experiment.getBuffer(src) != null)
                        set.addSource(name, src);
                    else
                        throw new phyphoxFileException("Export buffer " + src + " has not been defined as a buffer.", xpp.getLineNumber());
                    break;
                default:
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //This AsyncTask will load a phyphoxExperiment from an intent and return it by passing it to
    //onExperimentLoaded of the activity given in the constructor.
    protected static class loadXMLAsyncTask extends AsyncTask<String, Void, PhyphoxExperiment> {
        private Intent intent;
        private WeakReference<Experiment> parent;

        loadXMLAsyncTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = new WeakReference<Experiment>(parent);
        }

        //Load the file from the intent
        protected PhyphoxExperiment doInBackground(String... params) {
            //New experiment
            PhyphoxExperiment experiment = new PhyphoxExperiment();

            //Open the input stream (see above)
            PhyphoxStream input = openXMLInputStream(intent, parent.get());
            if (input.inputStream == null) { //If this failed, abort and relay the error message
                experiment.message = input.errorMessage;
                return experiment;
            }

            experiment.isLocal = input.isLocal; //The experiment needs to know if it is local
            experiment.source = input.source;
            experiment.crc32 = input.crc32;

            try {
                //Setup the pull parser
                BufferedReader reader = new BufferedReader(new InputStreamReader(input.inputStream));

                XmlPullParser xpp = Xml.newPullParser();
                xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                xpp.setInput(reader);

                //We can just race through all start tags until we reach the phyphox tag. Then let out phyphoxBlockParser take over.
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("phyphox")) {
                        //Phyphox tag. This is what we need to read, but let's check the file version first.
                        String fileVersion = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "version");
                        if (fileVersion != null) {
                            //A file version has been given. (If not, some user probably created the file manually. Let's allow this although it should not be encouraged.)
                            //Parse the file version and the version of this class
                            int split = fileVersion.indexOf('.'); //File version
                            int phyphoxSplit = phyphoxFileVersion.indexOf('.'); //Class version
                            try {
                                //Version strings are supposed to be of the form "x.y" with x being the major version number and y being minor.

                                //File versions
                                int major = Integer.valueOf(fileVersion.substring(0, split));
                                int minor = Integer.valueOf(fileVersion.substring(split + 1));

                                //Class versions
                                int phyphoxMajor = Integer.valueOf(phyphoxFileVersion.substring(0, phyphoxSplit));
                                int phyphoxMinor = Integer.valueOf(phyphoxFileVersion.substring(phyphoxSplit + 1));

                                //This class needs to be newer than the file. Otherwise ask the user to update.
                                if (major > phyphoxMajor || (major == phyphoxMajor && minor > phyphoxMinor)) {
                                    experiment.message = "This experiment has been created for a more recent version of phyphox. Please update phyphox to load this experiment.";
                                    return experiment;
                                }
                            } catch (NumberFormatException e) {
                                experiment.message = "Unable to interpret the file version of this experiment.";
                                return experiment;
                            }

                            String globalLocale = xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "locale");
                            languageRating = Helper.getLanguageRating(parent.get().getResources(), globalLocale);
                        }
                        (new phyphoxBlockParser(xpp, experiment, parent.get())).process();
                    }
                    eventType = xpp.next();
                }
            } catch (XmlPullParserException e) { //Catch pullparser errors
                experiment.message = "XML Error in line "+ e.getLineNumber() +": " + e.getMessage();
                return experiment;
            } catch (phyphoxFileException e) { //Catch our own errors
                experiment.message = e.getMessage();
                return experiment;
            } catch (IOException e) { //Catch IO errors
                experiment.message = "Unhandled IO error while loading this experiment: " + e.getMessage();
                return experiment;
            } catch (RuntimeException e) { //Those are a thing, too... For example, for some reason an undefined xml prefix throws a RuntimeException.
                experiment.message = "Unhandled RuntimeException while loading this experiment: " + e.getMessage();
                return experiment;

            }

            //Sanity check: If the experiment did not define any views, we cannot use it
            if (experiment.experimentViews.size() == 0) {
                experiment.message = "Bad experiment definition: No valid view found.";
                return experiment;
            }

            //We are done without any problems that we know of.
            experiment.loaded = true;
            return experiment;

        }

        @Override
        //Call the parent callback when we are done.
        protected void onPostExecute(PhyphoxExperiment experiment) {
            parent.get().onExperimentLoaded(experiment);
        }
    }

    //This asyncTask just copies the resource provided by an intent to the private data storage
    //It calls onCopyXMLCompleted of the activity given in the constructor when it's done.
    protected static class CopyXMLTask extends AsyncTask<String, Void, String> {
        private Intent intent; //The intent to read from
        private WeakReference<Experiment> parent; //The calling Activity

        //The constructor takes the intent to copy from and the parent activity to call back when finished.
        CopyXMLTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = new WeakReference<Experiment>(parent);
        }

        //Copying is done on a second thread...
        protected String doInBackground(String... params) {
            InputStream input;
            if (parent.get().experiment.source != null) {
                //We have stored the original source file...
                input = new ByteArrayInputStream(parent.get().experiment.source);
            } else {
                //If not, open the remote source, but usually this should not happen...
                PhyphoxFile.PhyphoxStream ps = PhyphoxFile.openXMLInputStream(intent, parent.get());
                input = ps.inputStream;
            }
            if (input == null)
                return "Error loading the original XML file again. This should not have happend."; //Abort and relay the rror message, if this failed

            //Copy the input stream to a random file name
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"; //Random file name
                FileOutputStream output = parent.get().openFileOutput(file, Activity.MODE_PRIVATE);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                input.close();
            } catch (Exception e) {
                return "Error loading the original XML file again: " + e.getMessage();
            }
            return "";
        }

        @Override
        //Call the parent callback when we are done.
        protected void onPostExecute(String result) {
            parent.get().onCopyXMLCompleted(result);
        }
    }
}
