package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
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

    final static String phyphoxFileVersion = "1.0";

    //translation maps any English term for which a suitable translation is found to the current locale
    private static Map<String, String> translation = new HashMap<>();

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
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    //PhyphoxStream bundles the result of an opened stream
    public static class PhyphoxStream {
        boolean isLocal;                //is the stream a local resource? (asset or private file)
        InputStream inputStream = null; //the input stream or null on error
        String errorMessage = "";       //Error message that can be displayed to the user
    }

    //Helper function to open an inputStream from various intents
    public static PhyphoxStream openXMLInputStream(Intent intent, Activity parent) {
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
                        return phyphoxStream;
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from assets: "+e.getMessage();
                        return phyphoxStream;
                    }
                } else { //The local file is in the private directory
                    try {
                        phyphoxStream.inputStream = parent.openFileInput(intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        phyphoxStream.errorMessage = "Error loading this experiment from local storage: " +e.getMessage();
                        return phyphoxStream;
                    }
                }

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
                    return phyphoxStream;
                } catch (Exception e) {
                    phyphoxStream.errorMessage = "Error loading experiment from file: " + e.getMessage();
                    return phyphoxStream;
                }

            } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {//The intent refers to a content (like the attachment from a mailing app)
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = parent.getContentResolver();
                try {
                    phyphoxStream.inputStream = resolver.openInputStream(uri);
                } catch (Exception e) {
                    phyphoxStream.errorMessage = "Error loading experiment from content: " + e.getMessage();
                    return phyphoxStream;
                }
            } else if (scheme.equals("http") || scheme.equals("https")) { //The intent refers to an online resource
                phyphoxStream.isLocal = false;
                try {
                    Uri uri = intent.getData();
                    URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPath());
                    phyphoxStream.inputStream = url.openStream();
                    return phyphoxStream;
                } catch (Exception e) {
                    phyphoxStream.errorMessage = "Error loading experiment from http: " + e.getMessage();
                    return phyphoxStream;
                }
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

        //These functions should be overriden with block-specific code
        protected void processStartTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {

        }
        protected void processEndTag(String tag) throws IOException, XmlPullParserException, phyphoxFileException {

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
                    experiment.icon = getText();
                    break;
                case "description": //The experiment's description (might be replaced by a later translation block)
                    experiment.description = getText();
                    break;
                case "category": //The experiment's category (might be replaced by a later translation block)
                    experiment.category = getText();
                    break;
                case "translations": //A translations block may containing multiple translation-blocks
                    (new translationsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "views": //A Views block may contain multiple view-blocks
                    (new viewsBlockParser(xpp, experiment, parent)).process();
                    break;
                case "input": //Holds inputs like sensors or the microphone
                    (new inputBlockParser(xpp, experiment, parent)).process();
                    break;
                case "analysis": //Holds a number of math modules which will be executed in the order they occur
                    experiment.analysisPeriod = getDoubleAttribute("period", 0.); //Time between executions
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
                    if (getStringAttribute("locale").equals(Locale.getDefault().getLanguage())) //Check if the language matches...
                        (new translationBlockParser(xpp, experiment, parent)).process(); //Jepp, use it!
                    else
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
                    experiment.description = getText();
                    break;
                case "string": //Some other translation. In labels and names of view elements, the string defined here as the attribute "original" will be replaced by the text in this tag
                    translation.put(getStringAttribute("original"), getText()); //Store it in our translation mapping
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
                    newView.name = getTranslatedAttribute("name"); //Fill its name
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
        private float labelSize;

        //The viewBlockParser takes an additional argument, which is the expView instance it should fill
        viewBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent, expView newView) {
            super(xpp, experiment, parent);
            this.newView = newView;
            labelSize = parent.getResources().getDimension(R.dimen.font);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "value": //A value element displays a single value to the user
                    expView.valueElement ve = newView.new valueElement(getTranslatedAttribute("label"), null, getStringAttribute("input"), null, null, parent.getResources()); //Only a value input
                    ve.setLabelSize((float)getDoubleAttribute("labelsize", labelSize)); //Label size
                    ve.setPrecision(getIntAttribute("precision", 2)); //Floating point precision
                    ve.setScientificNotation(getBooleanAttribute("scientific", false)); //Scientific notation vs. fixed point
                    ve.setUnit(getStringAttribute("unit")); //We can have a unit after the value
                    ve.setFactor(getDoubleAttribute("factor", 1.)); //A conversion factor. Usually for the unit
                    newView.elements.add(ve);
                    break;
                case "info": //An info element just shows some text
                    expView.infoElement infoe = newView.new infoElement(getTranslatedAttribute("label"), null, null, null, null, parent.getResources()); //No inputs, just the label and resources
                    infoe.setLabelSize((float)getDoubleAttribute("labelsize", labelSize)); //Label size
                    newView.elements.add(infoe);
                    break;
                case "graph": //A graph element displays a graph of an y array or two arrays x and y
                    expView.graphElement ge = newView.new graphElement(getTranslatedAttribute("label"), null, null, getStringAttribute("inputX"), getStringAttribute("inputY"), parent.getResources()); //Two array inputs
                    ge.setLabelSize((float)getDoubleAttribute("labelsize", labelSize)); //Label size
                    ge.setAspectRatio(getDoubleAttribute("aspectRatio", 3.)); //Aspect ratio of the whole element area icluding axes
                    String lineStyle = getStringAttribute("style"); //Line style defaults to "line", but may be "dots"
                    ge.setLine(!(lineStyle != null && lineStyle.equals("dots"))); //Everything but dots will be lines
                    ge.setPartialUpdate(getBooleanAttribute("partialUpdate", false)); //Will data only be appended? Will save bandwidth if we do not need to update the whole graph each time, especially on the web-interface
                    ge.setForceFullDataset(getBooleanAttribute("forceFullDataset", false)); //Display every single point instead of averaging those that would share the same x-pixel (may be quite a performance hit)
                    ge.setHistoryLength(getIntAttribute("history", 1)); //If larger than 1 the previous n graphs remain visible in a different color
                    ge.setLabel(getTranslatedAttribute("labelX"), getTranslatedAttribute("labelY"));  //x- and y- label
                    ge.setLogScale(getBooleanAttribute("logX", false), getBooleanAttribute("logY", false)); //logarithmic scales for x/y axes
                    newView.elements.add(ge);
                    break;
                case "input": //The input element can take input from the user
                    expView.inputElement ie = newView.new inputElement(getTranslatedAttribute("label"), getStringAttribute("output"), null, null, null, parent.getResources()); //Ouput only
                    ie.setLabelSize((float) getDoubleAttribute("labelsize", labelSize)); //Label size
                    ie.setUnit(getStringAttribute("unit")); //A unit displayed next to the input box
                    ie.setFactor(getDoubleAttribute("factor", 1.)); //A scaling factor. Mostly for matching units
                    ie.setSigned(getBooleanAttribute("signed", true)); //May the entered number be negative?
                    ie.setDecimal(getBooleanAttribute("decimal", true)); //May the user enter a decimal point (non-integer values)?
                    ie.setDefaultValue(getDoubleAttribute("default", 0.)); //Default value before the user entered anything
                    newView.elements.add(ie);
                    experiment.createBuffer(getStringAttribute("output"), 1); //The output element needs a buffer to write to
                    break;
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
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "sensor": //A sensor input (in the sense of android sensor)
                    int bufferSize = getIntAttribute("buffer", 100); //Buffer size
                    double rate = getDoubleAttribute("rate", 0.); //Aquisition rate (we always request fastest rate, but average or just pick every n-th readout)
                    boolean average = getBooleanAttribute("average", false); //Average if we have a lower rate than the sensor can deliver?

                    //Create the buffers (create buffer will only create on non-null values, so the user decides which to use)
                    dataBuffer dataX = experiment.createBuffer(xpp.getAttributeValue(null, "outputX"), bufferSize);
                    dataBuffer dataY = experiment.createBuffer(xpp.getAttributeValue(null, "outputY"), bufferSize);
                    dataBuffer dataZ = experiment.createBuffer(xpp.getAttributeValue(null, "outputZ"), bufferSize);
                    dataBuffer dataT = experiment.createBuffer(xpp.getAttributeValue(null, "outputT"), bufferSize);

                    //Add a sensor. If the string is unknown, sensorInput throws a phyphoxFileException
                    try {
                        experiment.inputSensors.add(new sensorInput(parent.sensorManager, getStringAttribute("type"), rate, average, dataX, dataY, dataZ, dataT));
                    } catch (sensorInput.SensorException e) {
                        throw new phyphoxFileException(e.getMessage(), xpp.getLineNumber());
                    }

                    //Check if the sensor is available on this device
                    if (!experiment.inputSensors.lastElement().isAvailable()) {
                        throw new phyphoxFileException(parent.getResources().getString(R.string.sensorNotAvailableWarningText1) + " " + parent.getResources().getString(experiment.inputSensors.lastElement().getDescriptionRes()) + " " + parent.getResources().getString(R.string.sensorNotAvailableWarningText2));
                    }
                    break;
                case "audio": //Audio input, aka microphone
                    //Check for recording permission
                    if (ContextCompat.checkSelfPermission(parent, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        //No permission? Request it (Android 6+, only)
                        ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                        throw new phyphoxFileException("Need permission to record audio."); //We will throw an error here, but when the user grants the permission, the activity will be restarted from the permission callback
                    }
                    experiment.micRate = getIntAttribute("rate", 48000); //Recording rate
                    experiment.micBufferSize = getIntAttribute("buffer", experiment.micRate); //Output-buffer size

                    //Devices have a minimum buffer size. We might need to increase our buffer...
                    int minBufferSize = AudioRecord.getMinBufferSize(experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    if (minBufferSize < 0) {
                        throw new phyphoxFileException("Could not initialize recording. (" + minBufferSize + ")", xpp.getLineNumber());
                    }
                    if (minBufferSize > experiment.micBufferSize) {
                        experiment.micBufferSize = minBufferSize;
                        Log.w("loadExperiment", "Audio buffer size had to be adjusted to " + minBufferSize);
                    }

                    //Create the buffer to write to
                    experiment.micOutput = getStringAttribute("output");
                    experiment.createBuffer(experiment.micOutput, experiment.micBufferSize);

                    //Now create the audioRecord instance
                    experiment.audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, experiment.micBufferSize * 2);
                    break;
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
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException {

            //As all analysis steps need outputs and take inputs, we will prepare the inputs here and
            //calculate typical buffer sizes
            int i = 1;
            int maxBufferSize = 1; //The size of the largest input buffer (for example, the addition of two arrays will have the size of the larger buffer and fill up the smaller one with the last element)
            int totalBufferSize = 0; //Total size of all buffers (for example, the append-operation will create an output of the summed up size of all buffers.)
            Vector<String> inputs = new Vector<>(); //This will hold the input buffers
            String inputName; //Temp to go through all inputs
            while ((inputName = getStringAttribute("input" + i)) != null) { //input1, input2, input3 etc....
                int inputSize;
                if (phyphoxFile.isValidIdentifier(inputName)) { //Is this a buffer input?
                    if (experiment.getBuffer(inputName) == null) //If a buffer reference, but not available, we have a problem.
                        throw new phyphoxFileException("Invalid input buffer. Any data buffer has to be defined as an output before being used as an input.", xpp.getLineNumber());
                    inputSize = experiment.getBuffer(inputName).size;
                } else { //Just a value, which is like a buffer of length 1
                    inputSize = 1;
                    try { //Check if we convert the numeric string. If not, we have a problem here...
                        Double.valueOf(getStringAttribute("input" + i));
                    } catch (NumberFormatException e) {
                        throw new phyphoxFileException("Invalid input format.", xpp.getLineNumber());
                    }
                }
                totalBufferSize += inputSize; //Add this buffer to the total buffer size
                if (inputSize > maxBufferSize) //If this buffer is larger than the previous ones, update maxBufferSize
                    maxBufferSize = inputSize;
                inputs.add(inputName);
                i++;
            }
            int singleBufferSize = getIntAttribute("buffer", 1); //Some buffers have a single value output, but the user may decide to increase it to create a history

            Vector<dataBuffer> outputs = new Vector<>(); //Will hold the output buffers

            switch (tag.toLowerCase()) {
                case "add": //input1+input2+input3...
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.addAM(experiment, inputs, outputs));
                    break;
                case "subtract": //input1-input2-input3...
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.subtractAM(experiment, inputs, outputs));
                    break;
                case "multiply": //input1*input2*input3...
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.multiplyAM(experiment, inputs, outputs));
                    break;
                case "divide": //input1/input2/input3...
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.divideAM(experiment, inputs, outputs));
                    break;
                case "power"://(input1^input2)^input3...
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.powerAM(experiment, inputs, outputs));
                    break;
                case "gcd": //Greatest common divisor
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.gcdAM(experiment, inputs, outputs));
                    break;
                case "lcm": //Least common multiple
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.lcmAM(experiment, inputs, outputs));
                    break;
                case "abs": //Absolute value
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.absAM(experiment, inputs, outputs));
                    break;
                case "sin": //Sine
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.sinAM(experiment, inputs, outputs));
                    break;
                case "cos": //Cosine
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.cosAM(experiment, inputs, outputs));
                    break;
                case "tan": //Tangens
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    experiment.analysis.add(new Analysis.tanAM(experiment, inputs, outputs));
                    break;
                case "max": //Maximum (takes y as first input and may take x as an optional second, same for outputs)
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), singleBufferSize));
                    outputs.add(experiment.createBuffer(getStringAttribute("output2"), singleBufferSize));
                    experiment.analysis.add(new Analysis.maxAM(experiment, inputs, outputs));
                    break;
                case "threshold": //Find the index at which the input crosses a threshold
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), singleBufferSize));
                    boolean falling = getBooleanAttribute("falling", false); //Positive or negative flank
                    experiment.analysis.add(new Analysis.thresholdAM(experiment, inputs, outputs, getStringAttribute("threshold"), falling));
                    break;
                case "append": //Append the inputs to each other
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), totalBufferSize));
                    experiment.analysis.add(new Analysis.appendAM(experiment, inputs, outputs));
                    break;
                case "fft": //Fourier transform
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    outputs.add(experiment.createBuffer(getStringAttribute("output2"), maxBufferSize));
                    experiment.analysis.add(new Analysis.fftAM(experiment, inputs, outputs));
                    break;
                case "autocorrelation": //Autocorrelation. First in/out is y, second in/out may be x
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    outputs.add(experiment.createBuffer(getStringAttribute("output2"), maxBufferSize));
                    Analysis.autocorrelationAM acAM = new Analysis.autocorrelationAM(experiment, inputs, outputs);
                    acAM.setMinMax(getStringAttribute("mint"), getStringAttribute("maxt"));
                    experiment.analysis.add(acAM);
                    break;
                case "differentiate": //Differentiate by subtracting neighboring values
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize-1));
                    experiment.analysis.add(new Analysis.differentiateAM(experiment, inputs, outputs));
                    break;
                case "crosscorrelation": //Crosscorrelation requires two inputs and a single output. y only.
                    if (getStringAttribute("input1") == null || getStringAttribute("input2") == null) {
                        throw new phyphoxFileException("Crosscorrelation needs two inputs.", xpp.getLineNumber());
                    }
                    if (experiment.getBuffer(getStringAttribute("input1")) == null || experiment.getBuffer(getStringAttribute("input2")) == null) {
                        throw new phyphoxFileException("Crosscorrelation inputs not available.", xpp.getLineNumber());
                    }
                    //The crosscorrelation only "moves" the smaller array along the larger one. If a correlation beyond these borders is needed, you have to add zeros yourself. Hence, the resulting size is the difference of the input sizes
                    int outSize = Math.abs(experiment.getBuffer(getStringAttribute("input1")).size - experiment.getBuffer(getStringAttribute("input2")).size);
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), outSize));
                    experiment.analysis.add(new Analysis.crosscorrelationAM(experiment, inputs, outputs));
                    break;
                case "gausssmooth": //Smooth the data with a Gauss profile
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), maxBufferSize));
                    Analysis.gaussSmoothAM gsAM = new Analysis.gaussSmoothAM(experiment, inputs, outputs);
                    if (getDoubleAttribute("sigma", 0) > 0)
                        gsAM.setSigma(getDoubleAttribute("sigma", 0));
                    experiment.analysis.add(gsAM);
                    break;
                case "rangefilter": //Arbitrary inputs and outputs, for each input[n] a min[n] and max[n] can be defined. The module filters the inputs in parallel and returns only those sets that match the filters
                    int j = 1;
                    Vector<String> min = new Vector<>(); //List of mins
                    Vector<String> max = new Vector<>(); //List of maxs
                    while (getStringAttribute("output" + j) != null) {
                        outputs.add(experiment.createBuffer(getStringAttribute("output"+j), maxBufferSize));
                        min.add(getStringAttribute("min" + j));
                        max.add(getStringAttribute("max" + j));
                        j++;
                    }
                    experiment.analysis.add(new Analysis.rangefilterAM(experiment, inputs, outputs, min, max));
                    break;
                case "ramp": //Create a linear ramp (great for creating time-bases)
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), singleBufferSize));
                    Analysis.rampGeneratorAM rampAM = new Analysis.rampGeneratorAM(experiment, inputs, outputs);
                    rampAM.setParameters(getStringAttribute("start"), getStringAttribute("stop"), getStringAttribute("length"));
                    experiment.analysis.add(rampAM);
                    break;
                case "const": //Initialize a buffer with constant values
                    outputs.add(experiment.createBuffer(getStringAttribute("output1"), singleBufferSize));
                    Analysis.constGeneratorAM constAM = new Analysis.constGeneratorAM(experiment, inputs, outputs);
                    constAM.setParameters(getStringAttribute("value"), getStringAttribute("length"));
                    experiment.analysis.add(constAM);
                    break;
                default: //Unknown tag...
                    throw new phyphoxFileException("Unknown tag "+tag, xpp.getLineNumber());
            }
            //Each buffer can be set to static and we only get here if the tag was a known module, that could be created, so we might just do this here...
            experiment.analysis.lastElement().setStatic(getBooleanAttribute("static", false));
        }

    }

    //Blockparser for the output block
    private static class outputBlockParser extends xmlBlockParser {

        outputBlockParser(XmlPullParser xpp, phyphoxExperiment experiment, Experiment parent) {
            super(xpp, experiment, parent);
        }

        @Override
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "audio": //Audio output, aka speaker
                    experiment.audioLoop = getBooleanAttribute("loop", false); //Loop the output?
                    experiment.audioSource = getStringAttribute("input"); //The dataBuffer that contains the audio data
                    if (experiment.getBuffer(experiment.audioSource) == null) //If the buffer is not available, this cannot work
                        throw new phyphoxFileException("Invalid input buffer. Any data buffer has to be defined as an output before being used as an input.", xpp.getLineNumber());
                    experiment.audioRate = getIntAttribute("rate", 48000); //Sample frequency
                    experiment.audioBufferSize = getIntAttribute("buffer", experiment.audioRate);

                    //Create the audioTrack instance
                    experiment.audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, experiment.audioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * experiment.audioBufferSize, AudioTrack.MODE_STATIC);
                    if (experiment.audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                        throw new phyphoxFileException("Could not initialize audio. (" + experiment.audioTrack.getState() + ")", xpp.getLineNumber());
                    }
                    break;
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
        protected void processStartTag(String tag) throws XmlPullParserException, phyphoxFileException {
            switch (tag.toLowerCase()) {
                case "data": //Add this data buffer to the set
                    set.addSource(xpp.getAttributeValue(null, "name"), xpp.getAttributeValue(null, "source"));
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
            //Open the input stream (see above)
            phyphoxFile.PhyphoxStream input = phyphoxFile.openXMLInputStream(intent, parent);
            if (!input.errorMessage.equals(""))
                return input.errorMessage; //Abort and relay the rror message, if this failed

            //Copy the input stream to a random file name
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"; //Random file name
                FileOutputStream output = parent.openFileOutput(file, Activity.MODE_PRIVATE);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.inputStream.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                input.inputStream.close();
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