package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

//phyphoxFile implements the loading of an experiment from a *.phyphox file as well as the copying
//of a remote phyphox-file to the local collection. Both are implemented as an AsyncTask
public abstract class phyphoxFile {

    final static String phyphoxFileVersion = "1.3";

    //translation maps any term for which a suitable translation is found to the current locale or, as fallback, to English
    private static Map<String, String> translation = new HashMap<>();
    private static boolean perfectLocaleFound = false;

    //Simple helper to return either the translated term or the original one, if no translation could be found
    private static String translate(String input) {
        if (translation.containsKey(input.trim()))
            return translation.get(input.trim());
        else
            return input;
    }

    //Returns true if the string is a valid identifier for a dataBuffer (begins with a-zA-Z and only contains a-zA-Z0-9_)
    public static boolean isValidIdentifier(String s) {
        if (s.isEmpty()) {
            return false;
        }
//        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
//            return false;
//        }
//        for (int i = 1; i < s.length(); i++) {
//            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
//                return false;
//            }
//        }
        return true;
    }

    //PhyphoxStream bundles the result of an opened stream
    public static class PhyphoxStream {
        boolean isLocal;                //is the stream a local resource? (asset or private file)
        InputStream inputStream = null; //the input stream or null on error
        byte source[] = null;           //A copy of the input for non-local sources
        String errorMessage = "";       //Error message that can be displayed to the user
    }

    //Helper function to read an input stream into memory and return an input stream to the data in memory as well as the data
    public static void remoteInputToMemory(PhyphoxStream stream) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        int n;
        byte[] buffer = new byte[1024];
        while ((n = stream.inputStream.read(buffer, 0, 1024)) != -1) {
            os.write(buffer, 0, n);
        }
        os.flush();
        stream.source = os.toByteArray();
        stream.inputStream = new ByteArrayInputStream(stream.source);

    }

    //Helper function to open an inputStream from various intents
    public static PhyphoxStream openXMLInputStream(Intent intent, Activity parent) {
        perfectLocaleFound = false;
        translation = new HashMap<>();

        PhyphoxStream phyphoxStream = new PhyphoxStream();

        //We only respond to view-action-intents
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_VIEW)) {

            //We need to perform slightly different actions for all the different schemes
            String scheme = intent.getScheme();

            if (intent.getStringExtra(ExperimentList.EXPERIMENT_XML) != null) { //If the file location is found in the extra EXPERIMENT_XML, it is a local file
                phyphoxStream.isLocal = true;
                if (intent.getBooleanExtra(ExperimentList.EXPERIMENT_ISASSET, true)) { //The local file is an asser
                    AssetManager assetManager = parent.getAssets();
                    try {
                        phyphoxStream.inputStream = assetManager.open("experiments/" + intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from assets: "+e.getMessage();
                    }
                } else { //The local file is in the private directory
                    try {
                        phyphoxStream.inputStream = parent.openFileInput(intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from local storage: " +e.getMessage();
                    }
                }
                return phyphoxStream;

            } else if (scheme.equals(ContentResolver.SCHEME_FILE )) {//The intent refers to a file
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = parent.getContentResolver();
                //We will need read permissions for pretty much any file...
                if (ContextCompat.checkSelfPermission(parent, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
                    URL url = new URL("https", uri.getHost(), uri.getPath() + "?" + uri.getQuery());
                    phyphoxStream.inputStream = url.openStream();
                    remoteInputToMemory(phyphoxStream);
                } catch (Exception e) {
                    //ok, https did not work. Maybe we success with http?
                    try {
                        URL url = new URL("http", uri.getHost(), uri.getPath() + "?" + uri.getQuery());
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
        protected phyphoxExperiment experiment; //The experiment to be loaded

        //The constructor takes the tag, and the experiment to fill
        xmlBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            this.xpp = xpp;
            this.experiment = experiment;
            this.parent = parent;
        }

        //Helper to receive the text block of a tag
        protected String getText() throws XmlPullParserException, IOException {
            String text = xpp.nextText();
            if (text != null)
                return text.trim();
            else
                return null;
        }

        //Helper to receive a string typed attribute
        protected String getStringAttribute(String identifier) {
            return xpp.getAttributeValue(null, identifier);
        }

        //Helper to receive a string typed attribute and translate it
        protected String getTranslatedAttribute(String identifier) {
            return translate(xpp.getAttributeValue(null, identifier));
        }

        //Helper to receive an integer typed attribute, if invalid or not present, return default
        protected int getIntAttribute(String identifier, int defaultValue) {
            try {
                return Integer.valueOf(xpp.getAttributeValue(null, identifier));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        //Helper to receive a double typed attribute, if invalid or not present, return default
        protected double getDoubleAttribute(String identifier, double defaultValue) {
            try {
                return Double.valueOf(xpp.getAttributeValue(null, identifier));
            } catch (Exception e) {
                return defaultValue;
            }
        }

        //Helper to receive a boolean attribute, if invalid or not present, return default
        protected boolean getBooleanAttribute(String identifier, boolean defaultValue) {
            final String att = xpp.getAttributeValue(null, identifier);
            if (att == null)
                return defaultValue;
            return Boolean.valueOf(att);
        }

        //Helper to receive a color attribute, if invalid or not present, return default
        protected int getColorAttribute(String identifier, int defaultValue) {
            final String att = xpp.getAttributeValue(null, identifier);
            if (att == null)
                return defaultValue;
            if (att.length() != 6)
                return defaultValue;
            return Integer.parseInt(att, 16);
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
                eventType = xpp.next();
            }

            done();
        }
    }

    //Blockparser for any input and output assignments
    private static class ioBlockParser extends xmlBlockParser {

        public static class ioMapping {
            String name;
            boolean asRequired = true;
            int repeatableOffset = -1;
            boolean valueAllowed = true;
            int minCount = 0;
            int maxCount = 0;
            int count = 0;
        }

        Vector<dataInput> inputList;
        Vector<dataOutput> outputList;
        ioMapping[] inputMapping;
        ioMapping[] outputMapping;
        String mappingAttribute;

        ioBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent, Vector<dataInput> inputList, Vector<dataOutput> outputList, ioMapping[] inputMapping, ioMapping[] outputMapping, String mappingAttribute) {
            super(xpp, experiment, parent);
            this.inputList = inputList;
            this.outputList = outputList;
            this.inputMapping = inputMapping;
            this.outputMapping = outputMapping;
            this.mappingAttribute = mappingAttribute;
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

                        //Check if there is a matchin inputMapping
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
                        if (inputList.get(targetIndex) != null) {
                            if (inputMapping[targetIndex].repeatableOffset >= 0) {
                                //It is repeatable. Let's calculate a new index according to the repeatable offset
                                int repeatPeriod = inputMapping[inputMapping.length-1].repeatableOffset+1;
                                targetIndex = inputMapping.length + inputMapping[mappingIndex].repeatableOffset;
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
                            inputList.set(targetIndex, new dataInput(value));
                        } else {
                            throw new phyphoxFileException("Value-type not allowed for input \""+inputMapping[mappingIndex].name+"\".", xpp.getLineNumber());
                        }
                    } else if (type.equals("buffer")) {

                        //Check the type
                        boolean clearAfterRead = getBooleanAttribute("clear", true);

                        //This is a buffer. Let's see if it exists
                        String bufferName = getText();
                        dataBuffer buffer = experiment.getBuffer(bufferName);
                        if (buffer == null)
                            throw new phyphoxFileException("Buffer \""+bufferName+"\" not defined.", xpp.getLineNumber());
                        else {
                            inputList.set(targetIndex, new dataInput(buffer, clearAfterRead));
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
                    dataBuffer buffer = experiment.getBuffer(bufferName);
                    if (buffer == null)
                        throw new phyphoxFileException("Buffer \""+bufferName+"\" not defined.", xpp.getLineNumber());
                    else {
                        outputList.set(targetIndex, new dataOutput(buffer, clearBeforeWrite));
                    }
                    break;
                default: //Unknown tag,,,
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
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

        phyphoxBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "title": //The experiment's title (might be replaced by a later translation block)
                    experiment.title = getText();
                    break;
                case "icon": //The experiment's icon
                    // We currently do not show the icon while the experiment is open, so we do not need to read it.
                    //experiment.icon = getText();
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
                    experiment.category = getText();
                    break;
                case "translations": //A translations block may containing multiple translation-blocks
                    (new translationsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "data-containers": //The data-containers block defines all buffers used in this experiment
                    (new dataContainersBlockParser(xpp, experiment, parent)).process();
                    break;
                case "views": //A Views block may contain multiple view-blocks
                    (new viewsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "input": //Holds inputs like sensors or the microphone
                    (new inputBlockParser(xpp, experiment, parent)).process();
                    break;
                case "analysis": //Holds a number of math modules which will be executed in the order they occur
                    experiment.analysisSleep = getDoubleAttribute("sleep", 0.); //Time between executions
                    experiment.analysisOnUserInput = getBooleanAttribute("onUserInput", false); //Only execute when the user changed something?
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

        translationsBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "translation": //A translation block holds all translation information for a single language
                    String thisLocale = getStringAttribute("locale");
                    if (thisLocale.equals(Locale.getDefault().getLanguage()) || (!perfectLocaleFound && thisLocale.equals("en"))) { //Check if the language matches...
                        if (thisLocale.equals(Locale.getDefault().getLanguage()))
                            perfectLocaleFound = true;
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

        translationBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
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

        dataContainersBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
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
                    double init = getDoubleAttribute("init",Double.NaN);
                    boolean isStatic = getBooleanAttribute("static", false);

                    String name = getText();
                    if (!isValidIdentifier(name))
                        throw new phyphoxFileException("\"" + name + "\" is not a valid name for a data-container.", xpp.getLineNumber());

                    dataBuffer newBuffer = experiment.createBuffer(name, size);
                    newBuffer.setStatic(isStatic);
                    newBuffer.setInit(init);
                    break;
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the views block
    private static class viewsBlockParser extends xmlBlockParser {

        viewsBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag)  throws IOException, XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "view": //A view defines an arangement of elements displayed to the user
                    expView newView = new expView(); //Create a new view
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
        private expView newView;

        graphView.scaleMode parseScaleMode(String attribute) {
            String scaleStr = getStringAttribute(attribute);
            graphView.scaleMode scale = graphView.scaleMode.auto;
            if (scaleStr != null) {
                switch (scaleStr) {
                    case "auto": scale = graphView.scaleMode.auto;
                        break;
                    case "extend": scale = graphView.scaleMode.extend;
                        break;
                    case "fixed": scale = graphView.scaleMode.fixed;
                        break;
                }
            }
            return scale;
        }

        //The viewBlockParser takes an additional argument, which is the expView instance it should fill
        viewBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent, expView newView) {
            super(xpp, experiment, parent);
            this.newView = newView;
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            String label = getTranslatedAttribute("label");
            double factor = getDoubleAttribute("factor", 1.);
            String unit = getStringAttribute("unit");
            Vector<dataInput> inputs = new Vector<>();
            Vector<dataOutput> outputs = new Vector<>();
            switch (tag.toLowerCase()) {
                case "value": { //A value element displays a single value to the user
                    int precision = getIntAttribute("precision", 2);
                    boolean scientific = getBooleanAttribute("scientific", false);
                    double size = getDoubleAttribute("size", 1.0);

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, null)).process(); //Load inputs and outputs

                    expView.valueElement ve = newView.new valueElement(label, null, inputs.get(0).buffer.name, null, null, parent.getResources()); //Only a value input
                    ve.setPrecision(precision); //Floating point precision
                    ve.setScientificNotation(scientific); //Scientific notation vs. fixed point
                    ve.setUnit(unit); //We can have a unit after the value
                    ve.setFactor(factor); //A conversion factor. Usually for the unit
                    ve.setSize(size); //A conversion factor. Usually for the unit
                    newView.elements.add(ve);
                    break;
                }
                case "info": //An info element just shows some text
                    expView.infoElement infoe = newView.new infoElement(label, null, null, null, null, parent.getResources()); //No inputs, just the label and resources
                    newView.elements.add(infoe);
                    break;
                case "graph": { //A graph element displays a graph of an y array or two arrays x and y
                    double aspectRatio = getDoubleAttribute("aspectRatio", 2.5);
                    String lineStyle = getStringAttribute("style"); //Line style defaults to "line", but may be "dots"
                    boolean partialUpdate = getBooleanAttribute("partialUpdate", false);
                    int history = getIntAttribute("history", 1);
                    String labelX = getTranslatedAttribute("labelX");
                    String labelY = getTranslatedAttribute("labelY");
                    boolean logX = getBooleanAttribute("logX", false);
                    boolean logY = getBooleanAttribute("logY", false);
                    double lineWidth = getDoubleAttribute("lineWidth", 1.0);
                    int xPrecision = getIntAttribute("xPrecision", 3);
                    int yPrecision = getIntAttribute("yPrecision", 3);
                    int color = getColorAttribute("color", parent.getResources().getColor(R.color.highlight));

                    graphView.scaleMode scaleMinX = parseScaleMode("scaleMinX");
                    graphView.scaleMode scaleMaxX = parseScaleMode("scaleMaxX");
                    graphView.scaleMode scaleMinY = parseScaleMode("scaleMinY");
                    graphView.scaleMode scaleMaxY = parseScaleMode("scaleMaxY");

                    double minX = getDoubleAttribute("minX", 0.);
                    double maxX = getDoubleAttribute("maxX", 0.);
                    double minY = getDoubleAttribute("minY", 0.);
                    double maxY = getDoubleAttribute("maxY", 0.);


                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = false; minCount = 1; maxCount = 1; valueAllowed = false; }},
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false; }}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, "axis")).process(); //Load inputs and outputs
                    String bufferX = null;
                    if (inputs.size() > 1)
                        bufferX = inputs.get(1).buffer.name;
                    String bufferY = inputs.get(0).buffer.name;

                    expView.graphElement ge = newView.new graphElement(label, null, null, bufferX, bufferY, parent.getResources()); //Two array inputs
                    ge.setAspectRatio(aspectRatio); //Aspect ratio of the whole element area icluding axes
                    ge.setLine(!(lineStyle != null && lineStyle.equals("dots"))); //Everything but dots will be lines
                    ge.setLineWidth(lineWidth);
                    ge.setColor(color);
                    ge.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
                    ge.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
                    ge.setPartialUpdate(partialUpdate); //Will data only be appended? Will save bandwidth if we do not need to update the whole graph each time, especially on the web-interface
                    ge.setHistoryLength(history); //If larger than 1 the previous n graphs remain visible in a different color
                    ge.setLabel(labelX, labelY);  //x- and y- label
                    ge.setLogScale(logX, logY); //logarithmic scales for x/y axes
                    ge.setPrecision(xPrecision, yPrecision); //logarithmic scales for x/y axes
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

                    expView.editElement ie = newView.new editElement(label, outputs.get(0).buffer.name, null, null, null, parent.getResources()); //Ouput only
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
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0;}},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; repeatableOffset = 0;}}
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, null)).process(); //Load inputs and outputs

                    expView.buttonElement be = newView.new buttonElement(label, null, null, null, null, parent.getResources()); //This one is user-event driven and does not regularly read or write values
                    be.setIO(inputs, outputs);
                    newView.elements.add(be);
                    break;
                }
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the input block
    private static class inputBlockParser extends xmlBlockParser {

        inputBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "sensor": { //A sensor input (in the sense of android sensor)
                    double rate = getDoubleAttribute("rate", 0.); //Aquisition rate (we always request fastest rate, but average or just pick every n-th readout)
                    boolean average = getBooleanAttribute("average", false); //Average if we have a lower rate than the sensor can deliver?

                    String type = getStringAttribute("type");

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "x"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "y"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "z"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}},
                            new ioBlockParser.ioMapping() {{name = "t"; asRequired = true; minCount = 0; maxCount = 1; valueAllowed = false;}}
                    };
                    Vector<dataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, "component")).process(); //Load inputs and outputs

                    //Add a sensor. If the string is unknown, sensorInput throws a phyphoxFileException
                    try {
                        experiment.inputSensors.add(new sensorInput(type, rate, average, outputs, experiment.dataLock));
                        experiment.inputSensors.lastElement().attachSensorManager(parent.sensorManager);
                    } catch (sensorInput.SensorException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber());
                    }

                    //Check if the sensor is available on this device
                    if (!experiment.inputSensors.lastElement().isAvailable()) {
                        throw new phyphoxFileException(parent.getResources().getString(R.string.sensorNotAvailableWarningText1) + " " + parent.getResources().getString(experiment.inputSensors.lastElement().getDescriptionRes()) + " " + parent.getResources().getString(R.string.sensorNotAvailableWarningText2));
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
                    };
                    Vector<dataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, null)).process(); //Load inputs and outputs

                    experiment.micOutput = outputs.get(0).buffer.name;
                    experiment.micBufferSize = outputs.get(0).size()*2; //Output-buffer size

                    //Devices have a minimum buffer size. We might need to increase our buffer...
                    int minBufferSize = AudioRecord.getMinBufferSize(experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (minBufferSize < 0) {
                        throw new phyphoxFileException("Could not initialize recording. (" + minBufferSize + ")", xpp.getLineNumber());
                    }
                    if (minBufferSize > experiment.micBufferSize) {
                        experiment.micBufferSize = minBufferSize;
                        Log.w("loadExperiment", "Audio buffer size had to be adjusted to " + minBufferSize);
                    }

                    break;
                }
                case "bluetooth": { //A serial bluetooth input
                    double rate = getDoubleAttribute("rate", 0.); //Aquisition rate
                    boolean average = getBooleanAttribute("average", false); //Average if we have a lower rate than the sensor can deliver?

                    String nameFilter = getStringAttribute("name");
                    String addressFilter = getStringAttribute("address");

                    String protocolStr = getStringAttribute("protocol");
                    Protocol protocol = null;
                    switch (protocolStr) {
                        case "simple": {
                            String separator = getStringAttribute("separator");
                            if (separator == null || separator.equals("")) {
                                separator = "\n";
                            }
                            protocol = new Protocol(new Protocol.simple(separator));
                            break;
                        }
                        case "csv": {
                            String separator = getStringAttribute("separator");
                            if (separator == null || separator.equals("")) {
                                separator = ",";
                            }
                            protocol = new Protocol(new Protocol.csv(separator));
                            break;
                        }
                        case "json": {
                            Vector<String> names = new Vector<>();
                            int index = 1;
                            String name = getStringAttribute("out"+index);
                            while (name != null) {
                                names.add(name);
                                index++;
                                name = getStringAttribute("out"+index);
                            }
                            protocol = new Protocol(new Protocol.json(names));
                            break;
                        }
                        default: {
                            throw new phyphoxFileException("Unknown bluetooth protocol: " + protocolStr, xpp.getLineNumber());
                        }
                    }

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0;}},
                    };
                    Vector<dataOutput> outputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, null, outputs, null, outputMapping, "component")).process(); //Load inputs and outputs

                    //Instantiate the input.
                    try {
                        experiment.bluetoothInputs.add(new bluetoothInput(nameFilter, addressFilter, rate, average, outputs, experiment.dataLock, protocol));
                        experiment.bluetoothInputs.lastElement().openConnection();
                    } catch (bluetoothInput.bluetoothException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber());
                    }

                    //Check if the sensor is available on this device
                    if (!experiment.bluetoothInputs.lastElement().isAvailable()) {
                        throw new phyphoxFileException("Bluetooth device not found.");
                    }
                    break;
                }
                default: //Unknown tag
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
        }

    }

    //Blockparser for the analysis block
    private static class analysisBlockParser extends xmlBlockParser {

        analysisBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {

            Vector<dataInput> inputs = new Vector<>(); //Will hold the inputs
            Vector<dataOutput> outputs = new Vector<>(); //Will hold the output buffers

            switch (tag.toLowerCase()) {
                case "timer": { //Start-time of analysis

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.timerAM(experiment, inputs, outputs));
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
                            new ioBlockParser.ioMapping() {{name = "true"; asRequired = false; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "false"; asRequired = false; minCount = 0; maxCount = 1; valueAllowed = true; repeatableOffset = -1; }},
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
                            new ioBlockParser.ioMapping() {{name = "average"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                            new ioBlockParser.ioMapping() {{name = "stddev"; asRequired = false; minCount = 0; maxCount = 1; repeatableOffset = -1; }},
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
                case "append": { //Append the inputs to each other

                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = true; repeatableOffset = 0; }},
                    };
                    ioBlockParser.ioMapping[] outputMapping = {
                            new ioBlockParser.ioMapping() {{name = "out"; asRequired = false; minCount = 1; maxCount = 1; repeatableOffset = -1; }},
                    };
                    (new ioBlockParser(xpp, experiment, parent, inputs, outputs, inputMapping, outputMapping, "as")).process(); //Load inputs and outputs

                    experiment.analysis.add(new Analysis.appendAM(experiment, inputs, outputs));
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
        }

    }

    //Blockparser for the output block
    private static class outputBlockParser extends xmlBlockParser {

        outputBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "audio": { //Audio output, aka speaker
                    experiment.audioLoop = getBooleanAttribute("loop", false); //Loop the output?
                    experiment.audioRate = getIntAttribute("rate", 48000); //Sample frequency

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{
                                name = "in";
                                asRequired = false;
                                minCount = 1;
                                maxCount = 1;
                                valueAllowed = false;
                            }}
                    };
                    Vector<dataInput> inputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, null)).process(); //Load inputs and outputs

                    experiment.audioSource = inputs.get(0).buffer.name;
                    experiment.audioBufferSize = inputs.get(0).buffer.size;

                    //To avoid to short audio loops, we will write the audio data multiple times
                    // until we reached at least one second. In the worst case (one sample less than
                    // one second) we will have data for just short of 2 seconds.
                    if (experiment.audioLoop && experiment.audioBufferSize < 2 * experiment.audioRate)
                        experiment.audioBufferSize = 2 * experiment.audioRate;

                    break;
                }
                case "bluetooth": { //A serial bluetooth output
                    String nameFilter = getStringAttribute("name");
                    String addressFilter = getStringAttribute("address");

                    String protocolStr = getStringAttribute("protocol");
                    Protocol protocol = null;
                    switch (protocolStr) {
                        case "simple": {
                            String separator = getStringAttribute("separator");
                            if (separator == null || separator.equals("")) {
                                separator = "\n";
                            }
                            protocol = new Protocol(new Protocol.simple(separator));
                            break;
                        }
                        case "csv": {
                            String separator = getStringAttribute("separator");
                            if (separator == null || separator.equals("")) {
                                separator = ",";
                            }
                            protocol = new Protocol(new Protocol.csv(separator));
                            break;
                        }
                        case "json": {
                            Vector<String> names = new Vector<>();
                            int index = 1;
                            String name = getStringAttribute("in"+index);
                            while (name != null) {
                                names.add(name);
                                index++;
                                name = getStringAttribute("in"+index);
                            }
                            protocol = new Protocol(new Protocol.json(names));
                            break;
                        }
                        default: {
                            throw new phyphoxFileException("Unknown bluetooth protocol: " + protocolStr, xpp.getLineNumber());
                        }
                    }

                    //Allowed input/output configuration
                    ioBlockParser.ioMapping[] inputMapping = {
                            new ioBlockParser.ioMapping() {{name = "in"; asRequired = false; minCount = 1; maxCount = 0; valueAllowed = false; repeatableOffset = 0;}}
                    };
                    Vector<dataInput> inputs = new Vector<>();
                    (new ioBlockParser(xpp, experiment, parent, inputs, null, inputMapping, null, null)).process(); //Load inputs and outputs

                    //Instantiate the input.
                    try {
                        experiment.bluetoothOutputs.add(new bluetoothOutput(nameFilter, addressFilter, inputs, protocol));
                        experiment.bluetoothOutputs.lastElement().openConnection();
                    } catch (bluetoothOutput.bluetoothException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber());
                    }

                    //Check if the sensor is available on this device
                    if (!experiment.bluetoothOutputs.lastElement().isAvailable()) {
                        throw new phyphoxFileException("Bluetooth device not found.");
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

        exportBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, IOException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "set": //An export set. These just group some dataBuffers to be exported as a set
                    dataExport.exportSet set = experiment.exporter.new exportSet(xpp.getAttributeValue(null, "name")); //Create the set with the given name
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
        private dataExport.exportSet set;

        //This constructor takes an additional argument: The export set to be filled
        setBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent, dataExport.exportSet set) {
            super(xpp, experiment, parent);
            this.set = set;
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException, IOException {
            switch (tag.toLowerCase()) {
                case "data": //Add this data buffer to the set
                    String name = xpp.getAttributeValue(null, "name");
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
    protected static class loadXMLAsyncTask extends AsyncTask<String, Void, phyphoxExperiment> {
        private Intent intent;
        private Experiment parent;

        loadXMLAsyncTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = parent;
        }

        //Load the file from the intent
        protected phyphoxExperiment doInBackground(String... params) {
            //New experiment
            phyphoxExperiment experiment = new phyphoxExperiment();

            //Open the input stream (see above)
            PhyphoxStream input = openXMLInputStream(intent, parent);
            if (input.inputStream == null) { //If this failed, abort and relay the error message
                experiment.message = input.errorMessage;
                return experiment;
            }

            experiment.isLocal = input.isLocal; //The experiment needs to know if it is local
            experiment.source = input.source;

            try {
                //Setup the pull parser
                BufferedReader reader = new BufferedReader(new InputStreamReader(input.inputStream));

                XmlPullParser xpp = Xml.newPullParser();
                xpp.setInput(reader);

                //We can just race through all start tags until we reach the phyphox tag. Then let out phyphoxBlockParser take over.
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("phyphox")) {
                        //Phyphox tag. This is what we need to read, but let's check the file version first.
                        String fileVersion = xpp.getAttributeValue(null, "version");
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

                            String globalLocale = xpp.getAttributeValue(null, "locale");
                            if (globalLocale != null && globalLocale.equals(Locale.getDefault().getLanguage()))
                                perfectLocaleFound = true;
                        }
                        (new phyphoxBlockParser(xpp, experiment, parent)).process();
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
        protected void onPostExecute(phyphoxExperiment experiment) {
            parent.onExperimentLoaded(experiment);
        }
    }

    //This asyncTask just copies the resource provided by an intent to the private data storage
    //It calls onCopyXMLCompleted of the activity given in the constructor when it's done.
    protected static class CopyXMLTask extends AsyncTask<String, Void, String> {
        private Intent intent; //The intent to read from
        private Experiment parent; //The calling Activity

        //The constructor takes the intent to copy from and the parent activity to call back when finished.
        CopyXMLTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = parent;
        }

        //Copying is done on a second thread...
        protected String doInBackground(String... params) {
            InputStream input;
            if (parent.experiment.source != null) {
                //We have stored the original source file...
                input = new ByteArrayInputStream(parent.experiment.source);
            } else {
                //If not, open the remote source, but usually this should not happen...
                phyphoxFile.PhyphoxStream ps = phyphoxFile.openXMLInputStream(intent, parent);
                input = ps.inputStream;
            }
            if (input == null)
                return "Error loading the original XML file again. This should not have happend."; //Abort and relay the rror message, if this failed

            //Copy the input stream to a random file name
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"; //Random file name
                FileOutputStream output = parent.openFileOutput(file, Activity.MODE_PRIVATE);
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
            parent.onCopyXMLCompleted(result);
        }
    }
}