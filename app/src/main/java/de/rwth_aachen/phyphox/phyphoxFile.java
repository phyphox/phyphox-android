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
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.UUID;
import java.util.Vector;

//TODO clean-up and comment phyphoxFile class

public class phyphoxFile {

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

    public static class PhyphoxStream {
        boolean isLocal;
        InputStream inputStream = null;
        String message = "";
    }

    public static PhyphoxStream openXMLInputStream(Intent intent, Activity parent) {
        PhyphoxStream phyphoxStream = new PhyphoxStream();

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_VIEW)) {
            String scheme = intent.getScheme();
            if (intent.getStringExtra(ExperimentList.EXPERIMENT_XML) != null) {
                phyphoxStream.isLocal = true;
                if (intent.getBooleanExtra(ExperimentList.EXPERIMENT_ISASSET, true)) {
                    AssetManager assetManager = parent.getAssets();
                    try {
                        phyphoxStream.inputStream = assetManager.open("experiments/" + intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                        return phyphoxStream;
                    } catch (Exception e) {
                        Log.e("openXMLInputStream", "Error loading this experiment from assests: " + e);
                        phyphoxStream.message = "Error loading this experiment from assets.";
                        return phyphoxStream;
                    }
                } else {
                    try {
                        phyphoxStream.inputStream = parent.openFileInput(intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        Log.e("openXMLInputStream", "Error loading this experiment from local: " + e);
                        phyphoxStream.message = "Error loading this experiment from local storage.";
                        return phyphoxStream;
                    }
                }
            } else if (scheme.equals(ContentResolver.SCHEME_FILE )) {
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = parent.getContentResolver();
                if (ContextCompat.checkSelfPermission(parent, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    phyphoxStream.message = "Permission needed to read external storage.";
                    return phyphoxStream;
                }
                try {
                    phyphoxStream.inputStream = resolver.openInputStream(uri);
                    return phyphoxStream;
                } catch (Exception e) {
                    Log.e("openXMLInputStream", "Error loading experiment from file: " + e);
                    phyphoxStream.message = "Error loading experiment from file.";
                    return phyphoxStream;
                }
            } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                phyphoxStream.isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = parent.getContentResolver();
                try {
                    phyphoxStream.inputStream = resolver.openInputStream(uri);
                } catch (Exception e) {
                    Log.e("openXMLInputStream", "Error loading experiment from content: " + e);
                    phyphoxStream.message = "Error loading experiment from content.";
                    return phyphoxStream;
                }
            } else if (scheme.equals("http") || scheme.equals("https")) {
                phyphoxStream.isLocal = false;
                try {
                    Uri uri = intent.getData();
                    URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPath());
                    phyphoxStream.inputStream = url.openStream();
                    return phyphoxStream;
                } catch (Exception e) {
                    Log.e("openXMLInputStream", "Error loading experiment from http: " + e);
                    phyphoxStream.message = "Error loading experiment from http.";
                    return phyphoxStream;
                }
            }
            phyphoxStream.message = "Unknown scheme.";
            return phyphoxStream;
        } else {
            phyphoxStream.message = "No run-intent.";
            return phyphoxStream;
        }
    }

    protected static class loadXMLAsyncTask extends AsyncTask<String, Void, phyphoxExperiment> {
        private Intent intent;
        private Experiment parent;

        loadXMLAsyncTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = parent;
        }

        protected phyphoxExperiment doInBackground(String... params) {
            phyphoxExperiment experiment = new phyphoxExperiment();
            PhyphoxStream input = openXMLInputStream(intent, parent);

            if (input.inputStream == null) {
                experiment.message = input.message;
                return experiment;
            }

            experiment.isLocal = input.isLocal;

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input.inputStream));

                XmlPullParser xpp = Xml.newPullParser();
                xpp.setInput(reader);

                int eventType = xpp.getEventType();

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            switch (xpp.getName()) {
                                case "title":
                                    experiment.title = xpp.nextText();
                                    break;
                                case "icon":
                                    experiment.icon = xpp.nextText();
                                    break;
                                case "description":
                                    experiment.description = xpp.nextText();
                                    break;
                                case "category":
                                    experiment.category = xpp.nextText();
                                    break;
                                case "views":
                                    while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("views"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "view":
                                                        expView newView = new expView();
                                                        newView.name = xpp.getAttributeValue(null, "name");

                                                        while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("view"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                                            switch (eventType) {
                                                                case XmlPullParser.START_TAG:
                                                                    switch (xpp.getName()) {
                                                                        case "value":
                                                                            expView.valueElement ve = newView.new valueElement(xpp.getAttributeValue(null, "label"), null, xpp.getAttributeValue(null, "input"), null, null, parent.getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ve.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ve.setLabelSize(parent.getResources().getDimension(R.dimen.font));
                                                                            if (xpp.getAttributeValue(null, "precision") != null)
                                                                                ve.setPrecision(Integer.valueOf(xpp.getAttributeValue(null, "precision")));
                                                                            if (xpp.getAttributeValue(null, "scientific") != null)
                                                                                ve.setScientificNotation(xpp.getAttributeValue(null, "scientific").equals("true"));
                                                                            if (xpp.getAttributeValue(null, "unit") != null)
                                                                                ve.setUnit(xpp.getAttributeValue(null, "unit"));
                                                                            if (xpp.getAttributeValue(null, "factor") != null)
                                                                                ve.setFactor(Double.valueOf(xpp.getAttributeValue(null, "factor")));
                                                                            newView.elements.add(ve);
                                                                            break;
                                                                        case "graph":
                                                                            expView.graphElement ge = newView.new graphElement(xpp.getAttributeValue(null, "label"), null, null, xpp.getAttributeValue(null, "inputX"), xpp.getAttributeValue(null, "inputY"), parent.getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ge.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ge.setLabelSize(parent.getResources().getDimension(R.dimen.font));
                                                                            if (xpp.getAttributeValue(null, "height") != null)
                                                                                ge.setHeight(Integer.valueOf(xpp.getAttributeValue(null, "height")));
                                                                            ge.setLine(!(xpp.getAttributeValue(null, "style") != null && xpp.getAttributeValue(null, "style").equals("dots")));
                                                                            ge.setPartialUpdate((xpp.getAttributeValue(null, "partialUpdate") != null && xpp.getAttributeValue(null, "partialUpdate").equals("true")));
                                                                            ge.setForceFullDataset((xpp.getAttributeValue(null, "forceFullDataset") != null && xpp.getAttributeValue(null, "forceFullDataset").equals("true")));
                                                                            if (xpp.getAttributeValue(null, "history") != null)
                                                                                ge.setHistoryLength(Integer.valueOf(xpp.getAttributeValue(null, "history")));
                                                                            ge.setLabel(xpp.getAttributeValue(null, "labelX"), xpp.getAttributeValue(null, "labelY"));
                                                                            boolean logX = Boolean.valueOf(xpp.getAttributeValue(null, "logX"));
                                                                            boolean logY = Boolean.valueOf(xpp.getAttributeValue(null, "logY"));
                                                                            ge.setLogScale(logX, logY);
                                                                            newView.elements.add(ge);
                                                                            break;
                                                                        case "input":
                                                                            expView.inputElement ie = newView.new inputElement(xpp.getAttributeValue(null, "label"), xpp.getAttributeValue(null, "output"), null, null, null, parent.getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ie.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ie.setLabelSize(parent.getResources().getDimension(R.dimen.font));
                                                                            if (xpp.getAttributeValue(null, "unit") != null)
                                                                                ie.setUnit(xpp.getAttributeValue(null, "unit"));
                                                                            if (xpp.getAttributeValue(null, "factor") != null) {
                                                                                double factor = Double.valueOf(xpp.getAttributeValue(null, "factor"));
                                                                                ie.setFactor(factor);
                                                                            }
                                                                            ie.setSigned((xpp.getAttributeValue(null, "signed") == null || xpp.getAttributeValue(null, "signed").equals("true")));
                                                                            ie.setDecimal((xpp.getAttributeValue(null, "decimal") == null || xpp.getAttributeValue(null, "decimal").equals("true")));
                                                                            if (xpp.getAttributeValue(null, "default") != null)
                                                                                ie.setDefaultValue(Float.valueOf(xpp.getAttributeValue(null, "default")));
                                                                            newView.elements.add(ie);
                                                                            dataBuffer output = new dataBuffer(xpp.getAttributeValue(null, "output"), 1);
                                                                            experiment.dataBuffers.add(output);
                                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output"), experiment.dataBuffers.size() - 1);
                                                                            break;
                                                                    }
                                                            }
                                                            eventType = xpp.next();
                                                        }

                                                        if (newView.name != null && newView.elements.size() > 0)
                                                            experiment.experimentViews.add(newView);
                                                        else {
                                                            experiment.message = "Bad experiment definition: Invalid view.";
                                                            return experiment;
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "input":
                                    while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("input"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "sensor":
                                                        int bufferSize = 100;
                                                        double rate = 0.;
                                                        boolean average = false;
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            bufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        if (xpp.getAttributeValue(null, "rate") != null) {
                                                            rate = Double.valueOf(xpp.getAttributeValue(null, "rate"));
                                                        }
                                                        if (xpp.getAttributeValue(null, "average") != null) {
                                                            average = Boolean.valueOf(xpp.getAttributeValue(null, "average"));
                                                        }
                                                        if (xpp.getAttributeValue(null, "type") != null) {
                                                            dataBuffer dataX = null;
                                                            dataBuffer dataY = null;
                                                            dataBuffer dataZ = null;
                                                            dataBuffer dataT = null;
                                                            if (xpp.getAttributeValue(null, "outputX") != null) {
                                                                dataX = new dataBuffer(xpp.getAttributeValue(null, "outputX"), bufferSize);
                                                                experiment.dataBuffers.add(dataX);
                                                                experiment.dataMap.put(xpp.getAttributeValue(null, "outputX"), experiment.dataBuffers.size() - 1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputY") != null) {
                                                                dataY = new dataBuffer(xpp.getAttributeValue(null, "outputY"), bufferSize);
                                                                experiment.dataBuffers.add(dataY);
                                                                experiment.dataMap.put(xpp.getAttributeValue(null, "outputY"), experiment.dataBuffers.size() - 1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputZ") != null) {
                                                                dataZ = new dataBuffer(xpp.getAttributeValue(null, "outputZ"), bufferSize);
                                                                experiment.dataBuffers.add(dataZ);
                                                                experiment.dataMap.put(xpp.getAttributeValue(null, "outputZ"), experiment.dataBuffers.size() - 1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputT") != null) {
                                                                dataT = new dataBuffer(xpp.getAttributeValue(null, "outputT"), bufferSize);
                                                                experiment.dataBuffers.add(dataT);
                                                                experiment.dataMap.put(xpp.getAttributeValue(null, "outputT"), experiment.dataBuffers.size() - 1);
                                                            }
                                                            experiment.inputSensors.add(new sensorInput(parent.sensorManager, xpp.getAttributeValue(null, "type"), rate, average,
                                                                    dataX, dataY, dataZ, dataT));
                                                            if (!experiment.inputSensors.lastElement().isAvailable()) {
                                                                experiment.message = parent.getResources().getString(R.string.sensorNotAvailableWarningText1) + " " + parent.getResources().getString(experiment.inputSensors.lastElement().getDescriptionRes()) + " " + parent.getResources().getString(R.string.sensorNotAvailableWarningText2);
                                                                return experiment;
                                                            }
                                                        } else {
                                                            experiment.message = "Bad experiment definition: Undefined sensor.";
                                                            return experiment;
                                                        }
                                                        break;
                                                    case "audio":
                                                        if (ContextCompat.checkSelfPermission(parent, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                            ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                                                            experiment.message = "Need permission to record audio.";
                                                            return experiment;
                                                        }
                                                        experiment.micRate = 48000;
                                                        if (xpp.getAttributeValue(null, "rate") != null)
                                                            experiment.micRate = Integer.valueOf(xpp.getAttributeValue(null, "rate"));
                                                        experiment.micBufferSize = experiment.micRate;
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            experiment.micBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        int minBufferSize = AudioRecord.getMinBufferSize(experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                                                        if (minBufferSize < 0) {
                                                            experiment.message = "Could not initialize recording. (" + minBufferSize + ")";
                                                            return experiment;
                                                        }

                                                        if (minBufferSize > experiment.micBufferSize) {
                                                            experiment.micBufferSize = minBufferSize;
                                                            Log.w("loadExperiment", "Audio buffer size had to be adjusted to " + minBufferSize);
                                                        }

                                                        if (xpp.getAttributeValue(null, "output") != null) {
                                                            experiment.micOutput = xpp.getAttributeValue(null, "output");
                                                            dataBuffer output = new dataBuffer(experiment.micOutput, experiment.micBufferSize);
                                                            experiment.dataBuffers.add(output);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output"), experiment.dataBuffers.size() - 1);
                                                        }

                                                        experiment.audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, experiment.micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, experiment.micBufferSize * 2);
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "analysis":
                                    while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("analysis"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                if (xpp.getAttributeValue(null, "period") != null)
                                                    experiment.analysisPeriod = Double.valueOf(xpp.getAttributeValue(null, "period"));
                                                int i = 1;
                                                int maxBufferSize = 1;
                                                int totalBufferSize = 0;
                                                Vector<String> inputs = new Vector<>();
                                                while (xpp.getAttributeValue(null, "input" + i) != null) {
                                                    if (phyphoxFile.isValidIdentifier(xpp.getAttributeValue(null, "input" + i))) {
                                                        int inputSize = experiment.dataBuffers.get(experiment.dataMap.get(xpp.getAttributeValue(null, "input" + i))).size;
                                                        totalBufferSize += inputSize;
                                                        if (inputSize > maxBufferSize)
                                                            maxBufferSize = inputSize;
                                                    }
                                                    inputs.add(xpp.getAttributeValue(null, "input" + i));
                                                    i++;
                                                }
                                                int singleBufferSize = 1;
                                                if (xpp.getAttributeValue(null, "buffer") != null)
                                                    singleBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));

                                                Vector<dataBuffer> outputs = new Vector<>();

                                                String tag = xpp.getName();
                                                boolean handled = true;
                                                switch (tag) {
                                                    case "add":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.addAM(experiment, inputs, outputs));
                                                        break;
                                                    case "subtract":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.subtractAM(experiment, inputs, outputs));
                                                        break;
                                                    case "multiply":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.multiplyAM(experiment, inputs, outputs));
                                                        break;
                                                    case "divide":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.divideAM(experiment, inputs, outputs));
                                                        break;
                                                    case "power":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.powerAM(experiment, inputs, outputs));
                                                        break;
                                                    case "sin":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.sinAM(experiment, inputs, outputs));
                                                        break;
                                                    case "cos":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.cosAM(experiment, inputs, outputs));
                                                        break;
                                                    case "tan":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        experiment.analysis.add(new Analysis.tanAM(experiment, inputs, outputs));
                                                        break;
                                                    case "max":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), singleBufferSize);
                                                            experiment.dataBuffers.add(output2);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output2"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output2);
                                                        }
                                                        experiment.analysis.add(new Analysis.maxAM(experiment, inputs, outputs));
                                                        break;
                                                    case "threshold":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        double threshold = 0.;
                                                        if (xpp.getAttributeValue(null, "threshold") != null)
                                                            threshold = Double.valueOf(xpp.getAttributeValue(null, "threshold"));
                                                        boolean falling = Boolean.valueOf(xpp.getAttributeValue(null, "falling"));
                                                        experiment.analysis.add(new Analysis.thresholdAM(experiment, inputs, outputs, String.valueOf(threshold), falling));
                                                        break;
                                                    case "append":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), totalBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.appendAM appendAM = new Analysis.appendAM(experiment, inputs, outputs);
                                                        experiment.analysis.add(appendAM);
                                                        break;
                                                    case "fft":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), maxBufferSize);
                                                            experiment.dataBuffers.add(output2);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output2"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output2);
                                                        }
                                                        Analysis.fftAM fftAM = new Analysis.fftAM(experiment, inputs, outputs);
                                                        experiment.analysis.add(fftAM);
                                                        break;
                                                    case "autocorrelation":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), maxBufferSize);
                                                            experiment.dataBuffers.add(output2);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output2"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output2);
                                                        }
                                                        Analysis.autocorrelationAM acAM = new Analysis.autocorrelationAM(experiment, inputs, outputs);
                                                        double mint = Double.NEGATIVE_INFINITY;
                                                        if (xpp.getAttributeValue(null, "mint") != null)
                                                            mint = Double.valueOf(xpp.getAttributeValue(null, "mint"));
                                                        double maxt = Double.POSITIVE_INFINITY;
                                                        if (xpp.getAttributeValue(null, "maxt") != null)
                                                            maxt = Double.valueOf(xpp.getAttributeValue(null, "maxt"));
                                                        acAM.setMinMax(String.valueOf(mint), String.valueOf(maxt));
                                                        experiment.analysis.add(acAM);
                                                        break;
                                                    case "crosscorrelation":
                                                        if (xpp.getAttributeValue(null, "input1") == null || xpp.getAttributeValue(null, "input2") == null) {
                                                            experiment.message = "Bad experiment definition: Crosscorrelation needs two inputs.";
                                                            return experiment;
                                                        }
                                                        int outSize = Math.abs(experiment.dataBuffers.get(experiment.dataMap.get(xpp.getAttributeValue(null, "input1"))).size - experiment.dataBuffers.get(experiment.dataMap.get(xpp.getAttributeValue(null, "input2"))).size);
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), outSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.crosscorrelationAM ccAM = new Analysis.crosscorrelationAM(experiment, inputs, outputs);
                                                        experiment.analysis.add(ccAM);
                                                        break;
                                                    case "gausssmooth":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.gaussSmoothAM gsAM = new Analysis.gaussSmoothAM(experiment, inputs, outputs);
                                                        if (xpp.getAttributeValue(null, "sigma") != null)
                                                            gsAM.setSigma(Double.valueOf(xpp.getAttributeValue(null, "sigma")));
                                                        experiment.analysis.add(gsAM);
                                                        break;
                                                    case "rangefilter":
                                                        int j = 1;
                                                        Vector<String> min = new Vector<>();
                                                        Vector<String> max = new Vector<>();
                                                        while (xpp.getAttributeValue(null, "output" + j) != null) {
                                                            dataBuffer output = new dataBuffer(xpp.getAttributeValue(null, "output" + j), maxBufferSize);
                                                            experiment.dataBuffers.add(output);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output" + j), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output);
                                                            min.add(xpp.getAttributeValue(null, "min" + j));
                                                            max.add(xpp.getAttributeValue(null, "max" + j));
                                                            j++;
                                                        }
                                                        experiment.analysis.add(new Analysis.rangefilterAM(experiment, inputs, outputs, min, max));
                                                        break;
                                                    case "ramp":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.rampGeneratorAM rampAM = new Analysis.rampGeneratorAM(experiment, inputs, outputs);
                                                        double start = 0;
                                                        double stop = 100;
                                                        int length = -1;
                                                        if (xpp.getAttributeValue(null, "start") != null)
                                                            start = Double.valueOf(xpp.getAttributeValue(null, "start"));
                                                        if (xpp.getAttributeValue(null, "stop") != null)
                                                            stop = Double.valueOf(xpp.getAttributeValue(null, "stop"));
                                                        if (xpp.getAttributeValue(null, "length") != null)
                                                            length = Integer.valueOf(xpp.getAttributeValue(null, "length"));
                                                        rampAM.setParameters(String.valueOf(start), String.valueOf(stop), String.valueOf(length));
                                                        experiment.analysis.add(rampAM);
                                                        break;
                                                    case "const":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            experiment.dataBuffers.add(output1);
                                                            experiment.dataMap.put(xpp.getAttributeValue(null, "output1"), experiment.dataBuffers.size() - 1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.constGeneratorAM constAM = new Analysis.constGeneratorAM(experiment, inputs, outputs);
                                                        double value = 0;
                                                        int clength = -1;
                                                        if (xpp.getAttributeValue(null, "value") != null)
                                                            value = Double.valueOf(xpp.getAttributeValue(null, "value"));
                                                        if (xpp.getAttributeValue(null, "length") != null)
                                                            clength = Integer.valueOf(xpp.getAttributeValue(null, "length"));
                                                        constAM.setParameters(String.valueOf(value), String.valueOf(clength));
                                                        experiment.analysis.add(constAM);
                                                        break;
                                                    default:
                                                        handled = false;

                                                }
                                                if (handled) {
                                                    boolean isStatic = Boolean.valueOf(xpp.getAttributeValue(null, "static"));
                                                    experiment.analysis.lastElement().setStatic(isStatic);
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "output":
                                    while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("export"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "audio":
                                                        experiment.audioSource = xpp.getAttributeValue(null, "input");
                                                        if (xpp.getAttributeValue(null, "rate") != null)
                                                            experiment.audioRate = Integer.valueOf(xpp.getAttributeValue(null, "rate"));
                                                        experiment.audioBufferSize = experiment.audioRate;
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            experiment.audioBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        experiment.audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, experiment.audioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * experiment.audioBufferSize, AudioTrack.MODE_STATIC);
                                                        if (experiment.audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                                                            experiment.message = "Could not initialize audio. (" + experiment.audioTrack.getState() + ")";
                                                            return experiment;
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "export":
                                    while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("export"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "set":
                                                        dataExport.exportSet set = experiment.exporter.new exportSet(xpp.getAttributeValue(null, "name"));
                                                        while ((!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("set"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                                            switch (eventType) {
                                                                case XmlPullParser.START_TAG:
                                                                    switch (xpp.getName()) {
                                                                        case "data":
                                                                            set.addSource(xpp.getAttributeValue(null, "name"), xpp.getAttributeValue(null, "source"));
                                                                            break;
                                                                    }
                                                                    break;
                                                            }
                                                            eventType = xpp.next();
                                                        }
                                                        experiment.exporter.addSet(set);
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                            }
                            break;
                    }
                    eventType = xpp.next();
                }

            } catch (Exception e) {
                experiment.message = "Unhandled error while loading this experiment.";
                Log.e("loadExperiment", "Unhandled error loading this experiment.", e);
                return experiment;
            }

            if (experiment.experimentViews.size() == 0) {
                experiment.message = "Bad experiment definition: No valid view found.";
                return experiment;
            }

            experiment.loaded = true;
            return experiment;

        }

        @Override
        protected void onPostExecute(phyphoxExperiment experiment) {
            parent.onExperimentLoaded(experiment);
        }
    }

    protected static class CopyXMLTask extends AsyncTask<String, Void, String> {
        private Intent intent;
        private Experiment parent;

        CopyXMLTask(Intent intent, Experiment parent) {
            this.intent = intent;
            this.parent = parent;
        }

        protected String doInBackground(String... params) {
            phyphoxFile.PhyphoxStream input = phyphoxFile.openXMLInputStream(intent, parent);
            if (!input.message.equals(""))
                return input.message;
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";
                FileOutputStream output = parent.openFileOutput(file, Activity.MODE_PRIVATE);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.inputStream.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                input.inputStream.close();
            } catch (Exception e) {
                Log.e("loadExperiment", "Error loading this experiment to local memory.", e);
                return "Error loading the original XML file again.";
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            parent.onCopyXMLCompleted(result);
        }
    }
}