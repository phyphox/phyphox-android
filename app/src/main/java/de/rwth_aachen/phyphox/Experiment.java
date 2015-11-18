package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

//TODO Clean-up loading-code and make it more error-proof (Especially: Get error messages to main thread as they cannot be displayed in second thread)
//TODO Raw-Experiment (Select inputs, buffer length and aquisition rate)
//TODO Translation of Experiment-Texts
//TODO Sonar needs fine-tuning

public class Experiment extends AppCompatActivity {

    private static final String STATE_CURRENT_VIEW = "current_view";
    private static final String STATE_DATA_BUFFERS = "data_buffers";
    private static final String STATE_REMOTE_SERVER = "remote_server";
    private static final String STATE_TIMED_RUN = "timed_run";
    private static final String STATE_TIMED_RUN_START_DELAY = "timed_run_start_delay";
    private static final String STATE_TIMED_RUN_STOP_DELAY = "timed_run_stop_delay";

    boolean measuring = false;
    boolean remoteIntentMeasuring = false;
    boolean updateState = false;
    boolean loadCompleted = false;
    boolean shutdown = false;
    boolean timedRun = false;
    double timedRunStartDelay = 0.;
    double timedRunStopDelay = 0.;
    CountDownTimer cdTimer = null;
    long millisUntilFinished = 0;
    final Handler updateViewsHandler = new Handler();

    double analysisPeriod = 0.;
    long analysisStart = 0;

    String title = "";
    String category = "";
    String icon = "";
    String description = "There is no description available for this experiment.";
    boolean isLocal = true;
    private Vector<expView> experimentViews = new Vector<>();
    private int currentView;
    private Vector<sensorInput> inputSensors = new Vector<>();
    public final Vector<dataBuffer> dataBuffers = new Vector<>();
    public final Map<String, Integer> dataMap = new HashMap<>();
    private Vector<Analysis.analysisModule> analysis = new Vector<>();

    AudioTrack audioTrack = null;
    String audioSource;
    int audioRate = 48000;
    int audioBufferSize = 0;
    AudioRecord audioRecord = null;
    String micOutput;
    int micRate = 48000;
    int micBufferSize = 0;

    private Resources res;

    private boolean serverEnabled = false;

    public dataExport exporter;

    private SensorManager sensorManager;

    private remoteServer remote = null;
    public boolean remoteInput = false;
    public boolean shouldDefocus = false;

    Intent intent;
    ProgressDialog progress;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                this.recreate();
            else {
                finish();
                startActivity(intent);
            }
        }
    }

    InputStream getXMLInputStream(Intent intent, Activity parent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_VIEW)) {
            String scheme = intent.getScheme();
            if (intent.getStringExtra(ExperimentList.EXPERIMENT_XML) != null) {
                isLocal = true;
                if (intent.getBooleanExtra(ExperimentList.EXPERIMENT_ISASSET, true)) {
                    AssetManager assetManager = getAssets();
                    try {
                        return assetManager.open("experiments/" + intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        Log.e("onCreate", "Error loading this experiment from assests: " + e);
                        Toast.makeText(parent, "Error loading this experiment from assets.", Toast.LENGTH_LONG).show();
                        finish();
                        return null;
                    }
                } else {
                    try {
                        return openFileInput(intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
                    } catch (Exception e) {
                        Log.e("onCreate", "Error loading this experiment from assests: " + e);
                        Toast.makeText(parent, "Error loading this experiment from assets.", Toast.LENGTH_LONG).show();
                        finish();
                        return null;
                    }
                }
            } else if (scheme.equals(ContentResolver.SCHEME_FILE )) {
                isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = getContentResolver();
                if (ContextCompat.checkSelfPermission(parent, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    return null;
                }
                try {
                    return resolver.openInputStream(uri);
                } catch (Exception e) {
                    Log.e("onCreate", "Error loading experiment from file: " + e);
                    Toast.makeText(parent, "Error loading experiment from file.", Toast.LENGTH_LONG).show();
                    finish();
                    return null;
                }
            } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                isLocal = false;
                Uri uri = intent.getData();
                ContentResolver resolver = getContentResolver();
                try {
                    return resolver.openInputStream(uri);
                } catch (Exception e) {
                    Log.e("onCreate", "Error loading experiment from file: " + e);
                    Toast.makeText(parent, "Error loading experiment from file.", Toast.LENGTH_LONG).show();
                    finish();
                    return null;
                }
            } else if (scheme.equals("http") || scheme.equals("https")) {
                isLocal = false;
                try {
                    Uri uri = intent.getData();
                    URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPath());
                    return url.openStream();
                } catch (Exception e) {
                    Log.e("onCreate", "Error loading experiment from http: " + e);
                    Toast.makeText(parent, "Error loading experiment from http.", Toast.LENGTH_LONG).show();
                    finish();
                    return null;
                }
            }
            Toast.makeText(parent, "Unknown scheme.", Toast.LENGTH_LONG).show();
            return null;
        } else {
            Toast.makeText(parent, "No run-intent.", Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private class CopyXMLTask extends AsyncTask<String, Void, Boolean> {
        private Intent intent;
        private Activity parent;

        CopyXMLTask(Intent intent, Activity parent) {
            this.intent = intent;
            this.parent = parent;
        }

        protected Boolean doInBackground(String... params) {
            InputStream input = getXMLInputStream(intent, parent);
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";
                FileOutputStream output = parent.openFileOutput(file, MODE_PRIVATE);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                input.close();
            } catch (Exception e) {
                Toast.makeText(parent, "Error loading the original XML file again.", Toast.LENGTH_LONG).show();
                Log.e("loadExperiment", "Error loading this experiment to local memory.", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progress.dismiss();
            if (result) {
                Toast.makeText(parent, R.string.save_locally_done, Toast.LENGTH_LONG).show();
                Intent upIntent = NavUtils.getParentActivityIntent(parent);
                TaskStackBuilder.create(parent)
                           .addNextIntent(upIntent)
                           .startActivities();
                finish();
            }
        }
    }

    private class DownloadXMLTask extends AsyncTask<String, Void, Boolean> {
        private Intent intent;
        private boolean noWarning;
        private Activity parent;
        private Bundle savedInstanceState;

        DownloadXMLTask(Intent intent, boolean noWarning, Activity parent, Bundle savedInstanceState) {
            this.intent = intent;
            this.noWarning = noWarning;
            this.parent = parent;
            this.savedInstanceState = savedInstanceState;
        }

        protected Boolean doInBackground(String... params) {
            InputStream input = getXMLInputStream(intent, parent);

            if (input == null)
                return false;

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                XmlPullParser xpp = Xml.newPullParser();
                xpp.setInput(reader);

                int eventType = xpp.getEventType();

                while (eventType != XmlPullParser.END_DOCUMENT){
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            switch (xpp.getName()) {
                                case "title":
                                    title = xpp.nextText();
                                    break;
                                case "icon":
                                    icon = xpp.nextText();
                                    break;
                                case "description":
                                    description = xpp.nextText();
                                    break;
                                case "category":
                                    category = xpp.nextText();
                                    break;
                                case "views":
                                    while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("views"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "view":
                                                        expView newView = new expView();
                                                        newView.name = xpp.getAttributeValue(null, "name");

                                                        while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("view"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                                            switch (eventType) {
                                                                case XmlPullParser.START_TAG:
                                                                    switch (xpp.getName()) {
                                                                        case "value":
                                                                            expView.valueElement ve = newView.new valueElement(xpp.getAttributeValue(null, "label"), null, xpp.getAttributeValue(null, "input"), null, null, getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ve.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ve.setLabelSize(res.getDimension(R.dimen.font));
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
                                                                            expView.graphElement ge = newView.new graphElement(xpp.getAttributeValue(null, "label"), null, null, xpp.getAttributeValue(null, "inputX"), xpp.getAttributeValue(null, "inputY"), getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ge.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ge.setLabelSize(res.getDimension(R.dimen.font));
                                                                            if (xpp.getAttributeValue(null, "height") != null)
                                                                                ge.setHeight(Integer.valueOf(xpp.getAttributeValue(null, "height")));
                                                                            ge.setLine(!(xpp.getAttributeValue(null, "style") != null && xpp.getAttributeValue(null, "style").equals("dots")));
                                                                            ge.setPartialUpdate((xpp.getAttributeValue(null, "partialUpdate") != null && xpp.getAttributeValue(null, "partialUpdate").equals("true")));
                                                                            if (xpp.getAttributeValue(null, "history") != null)
                                                                                ge.setHistoryLength(Integer.valueOf(xpp.getAttributeValue(null, "history")));
                                                                            ge.setLabel(xpp.getAttributeValue(null, "labelX"), xpp.getAttributeValue(null, "labelY"));
                                                                            boolean logX = Boolean.valueOf(xpp.getAttributeValue(null, "logX"));
                                                                            boolean logY = Boolean.valueOf(xpp.getAttributeValue(null, "logY"));
                                                                            ge.setLogScale(logX, logY);
                                                                            newView.elements.add(ge);
                                                                            break;
                                                                        case "input":
                                                                            expView.inputElement ie = newView.new inputElement(xpp.getAttributeValue(null, "label"), xpp.getAttributeValue(null, "output"), null, null, null, getResources());
                                                                            if (xpp.getAttributeValue(null, "labelsize") != null)
                                                                                ie.setLabelSize(Float.valueOf(xpp.getAttributeValue(null, "labelsize")));
                                                                            else
                                                                                ie.setLabelSize(res.getDimension(R.dimen.font));
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
                                                                            dataBuffers.add(output);
                                                                            dataMap.put(xpp.getAttributeValue(null, "output"), dataBuffers.size() - 1);
                                                                            break;
                                                                    }
                                                            }
                                                            eventType = xpp.next();
                                                        }

                                                        if (newView.name != null && newView.elements.size() > 0)
                                                            experimentViews.add(newView);
                                                        else
                                                            Toast.makeText(parent, "Invalid view ignored.", Toast.LENGTH_LONG).show();
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "input":
                                    while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("input"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "sensor":
                                                        int bufferSize = 100;
                                                        String rate = "fastest";
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            bufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        if (xpp.getAttributeValue(null, "rate") != null) {
                                                            rate = xpp.getAttributeValue(null, "rate");
                                                        }
                                                        if (xpp.getAttributeValue(null, "type") != null) {
                                                            dataBuffer dataX = null;
                                                            dataBuffer dataY = null;
                                                            dataBuffer dataZ = null;
                                                            dataBuffer dataT = null;
                                                            if (xpp.getAttributeValue(null, "outputX") != null) {
                                                                dataX = new dataBuffer(xpp.getAttributeValue(null, "outputX"), bufferSize);
                                                                dataBuffers.add(dataX);
                                                                dataMap.put(xpp.getAttributeValue(null, "outputX"), dataBuffers.size() - 1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputY") != null) {
                                                                dataY = new dataBuffer(xpp.getAttributeValue(null, "outputY"), bufferSize);
                                                                dataBuffers.add(dataY);
                                                                dataMap.put(xpp.getAttributeValue(null, "outputY"), dataBuffers.size()-1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputZ") != null) {
                                                                dataZ = new dataBuffer(xpp.getAttributeValue(null, "outputZ"), bufferSize);
                                                                dataBuffers.add(dataZ);
                                                                dataMap.put(xpp.getAttributeValue(null, "outputZ"), dataBuffers.size()-1);
                                                            }
                                                            if (xpp.getAttributeValue(null, "outputT") != null) {
                                                                dataT = new dataBuffer(xpp.getAttributeValue(null, "outputT"), bufferSize);
                                                                dataBuffers.add(dataT);
                                                                dataMap.put(xpp.getAttributeValue(null, "outputT"), dataBuffers.size()-1);
                                                            }
                                                            inputSensors.add(new sensorInput(sensorManager, xpp.getAttributeValue(null, "type"), rate,
                                                                    dataX, dataY, dataZ, dataT));
                                                            if (!inputSensors.lastElement().isAvailable() && !noWarning) {
                                                                new AlertDialog.Builder(parent)
                                                                        .setTitle(res.getString(R.string.sensorNotAvailableWarningTitle))
                                                                        .setMessage(res.getString(R.string.sensorNotAvailableWarningText1) + " " + res.getString(inputSensors.lastElement().getDescriptionRes()) + " " + res.getString(R.string.sensorNotAvailableWarningText2))
                                                                        .setCancelable(false)
                                                                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                                                            @Override
                                                                            public void onClick(DialogInterface dialog, int which) {
                                                                            }
                                                                        }).create().show();
                                                            }
                                                        } else
                                                            Toast.makeText(parent, "Undefined sensor ignored.", Toast.LENGTH_LONG).show();
                                                        break;
                                                    case "audio":
                                                        if (ContextCompat.checkSelfPermission(parent, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                            ActivityCompat.requestPermissions(parent, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                                                            return false;
                                                        }
                                                        micRate = 48000;
                                                        if (xpp.getAttributeValue(null, "rate") != null)
                                                            micRate = Integer.valueOf(xpp.getAttributeValue(null, "rate"));
                                                        micBufferSize = micRate;
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            micBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        int minBufferSize = AudioRecord.getMinBufferSize(micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                                                        if (minBufferSize < 0)
                                                            Toast.makeText(parent, "Could not initialize recording. (" + minBufferSize + ")", Toast.LENGTH_LONG).show();

                                                        if (minBufferSize > micBufferSize) {
                                                            micBufferSize = minBufferSize;
                                                            Toast.makeText(parent, "Warning: Audio buffer size had to be adjusted to " + minBufferSize, Toast.LENGTH_LONG).show();
                                                        }

                                                        if (xpp.getAttributeValue(null, "output") != null) {
                                                            micOutput = xpp.getAttributeValue(null, "output");
                                                            dataBuffer output = new dataBuffer(micOutput, micBufferSize);
                                                            dataBuffers.add(output);
                                                            dataMap.put(xpp.getAttributeValue(null, "output"), dataBuffers.size() - 1);
                                                        }

                                                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, micBufferSize*2);
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "analysis":
                                    while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("analysis"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                if (xpp.getAttributeValue(null, "period") != null)
                                                    analysisPeriod = Double.valueOf(xpp.getAttributeValue(null, "period"));
                                                int i = 1;
                                                int maxBufferSize = 1;
                                                int totalBufferSize = 0;
                                                Vector <String> inputs = new Vector<>();
                                                Vector <Boolean> isValue = new Vector<>();
                                                while (xpp.getAttributeValue(null, "input"+i) != null) {
                                                    isValue.add(xpp.getAttributeValue(null, "type"+i) != null && xpp.getAttributeValue(null, "type"+i).equals("value"));
                                                    if (!isValue.lastElement()) {
                                                        int inputSize = dataBuffers.get(dataMap.get(xpp.getAttributeValue(null, "input" + i))).size;
                                                        totalBufferSize += inputSize;
                                                        if (inputSize > maxBufferSize)
                                                            maxBufferSize = inputSize;
                                                    }
                                                    inputs.add(xpp.getAttributeValue(null, "input"+i));
                                                    i++;
                                                }
                                                int singleBufferSize = 1;
                                                if (xpp.getAttributeValue(null, "buffer") != null)
                                                    singleBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));

                                                Vector <dataBuffer> outputs = new Vector<>();

                                                String tag = xpp.getName();
                                                boolean handled = true;
                                                switch (tag) {
                                                    case "add":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.addAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "subtract":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.subtractAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "multiply":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.multiplyAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "divide":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.divideAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "power":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.powerAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "sin":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.sinAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "cos":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.cosAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "tan":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        analysis.add(new Analysis.tanAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "max":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), singleBufferSize);
                                                            dataBuffers.add(output2);
                                                            dataMap.put(xpp.getAttributeValue(null, "output2"), dataBuffers.size()-1);
                                                            outputs.add(output2);
                                                        }
                                                        analysis.add(new Analysis.maxAM(dataBuffers, dataMap, inputs, isValue, outputs));
                                                        break;
                                                    case "threshold":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        double threshold = 0.;
                                                        if (xpp.getAttributeValue(null, "threshold") != null)
                                                            threshold = Double.valueOf(xpp.getAttributeValue(null, "threshold"));
                                                        boolean falling = Boolean.valueOf(xpp.getAttributeValue(null, "falling"));
                                                        analysis.add(new Analysis.thresholdAM(dataBuffers, dataMap, inputs, isValue, outputs, threshold, falling));
                                                        break;
                                                    case "append":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), totalBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.appendAM appendAM = new Analysis.appendAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        analysis.add(appendAM);
                                                        break;
                                                    case "fft":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), maxBufferSize);
                                                            dataBuffers.add(output2);
                                                            dataMap.put(xpp.getAttributeValue(null, "output2"), dataBuffers.size()-1);
                                                            outputs.add(output2);
                                                        }
                                                        Analysis.fftAM fftAM = new Analysis.fftAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        analysis.add(fftAM);
                                                        break;
                                                    case "autocorrelation":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        if (xpp.getAttributeValue(null, "output2") != null) {
                                                            dataBuffer output2 = new dataBuffer(xpp.getAttributeValue(null, "output2"), maxBufferSize);
                                                            dataBuffers.add(output2);
                                                            dataMap.put(xpp.getAttributeValue(null, "output2"), dataBuffers.size()-1);
                                                            outputs.add(output2);
                                                        }
                                                        Analysis.autocorrelationAM acAM = new Analysis.autocorrelationAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        double mint = Double.NEGATIVE_INFINITY;
                                                        if (xpp.getAttributeValue(null, "mint") != null)
                                                            mint = Double.valueOf(xpp.getAttributeValue(null, "mint"));
                                                        double maxt = Double.POSITIVE_INFINITY;
                                                        if (xpp.getAttributeValue(null, "maxt") != null)
                                                            maxt = Double.valueOf(xpp.getAttributeValue(null, "maxt"));
                                                        acAM.setMinMaxT(mint, maxt);
                                                        analysis.add(acAM);
                                                        break;
                                                    case "crosscorrelation":
                                                        if (xpp.getAttributeValue(null, "input1") == null || xpp.getAttributeValue(null, "input2") == null) {
                                                            Toast.makeText(parent, "Crosscorrelation needs two inputs.", Toast.LENGTH_LONG).show();
                                                            return false;
                                                        }
                                                        int outSize = Math.abs(dataBuffers.get(dataMap.get(xpp.getAttributeValue(null, "input1"))).size-dataBuffers.get(dataMap.get(xpp.getAttributeValue(null, "input2"))).size);
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), outSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.crosscorrelationAM ccAM = new Analysis.crosscorrelationAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        analysis.add(ccAM);
                                                        break;
                                                    case "gausssmooth":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), maxBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.gaussSmoothAM gsAM = new Analysis.gaussSmoothAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        if (xpp.getAttributeValue(null, "sigma") != null)
                                                            gsAM.setSigma(Double.valueOf(xpp.getAttributeValue(null, "sigma")));
                                                        analysis.add(gsAM);
                                                        break;
                                                    case "rangefilter":
                                                        int j = 1;
                                                        Vector<String> min = new Vector<>();
                                                        Vector<String> max = new Vector<>();
                                                        while (xpp.getAttributeValue(null, "output"+j) != null) {
                                                            dataBuffer output = new dataBuffer(xpp.getAttributeValue(null, "output"+j), maxBufferSize);
                                                            dataBuffers.add(output);
                                                            dataMap.put(xpp.getAttributeValue(null, "output"+j), dataBuffers.size()-1);
                                                            outputs.add(output);
                                                            min.add(xpp.getAttributeValue(null, "min"+j));
                                                            max.add(xpp.getAttributeValue(null, "max"+j));
                                                            j++;
                                                        }
                                                        analysis.add(new Analysis.rangefilterAM(dataBuffers, dataMap, inputs, isValue, outputs, min, max));
                                                        break;
                                                    case "ramp":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.rampGeneratorAM rampAM = new Analysis.rampGeneratorAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        double start = 0;
                                                        double stop = 100;
                                                        int length = -1;
                                                        if (xpp.getAttributeValue(null, "start") != null)
                                                            start = Double.valueOf(xpp.getAttributeValue(null, "start"));
                                                        if (xpp.getAttributeValue(null, "stop") != null)
                                                            stop = Double.valueOf(xpp.getAttributeValue(null, "stop"));
                                                        if (xpp.getAttributeValue(null, "length") != null)
                                                            length = Integer.valueOf(xpp.getAttributeValue(null, "length"));
                                                        rampAM.setParameters(start, stop, length);
                                                        analysis.add(rampAM);
                                                        break;
                                                    case "const":
                                                        if (xpp.getAttributeValue(null, "output1") != null) {
                                                            dataBuffer output1 = new dataBuffer(xpp.getAttributeValue(null, "output1"), singleBufferSize);
                                                            dataBuffers.add(output1);
                                                            dataMap.put(xpp.getAttributeValue(null, "output1"), dataBuffers.size()-1);
                                                            outputs.add(output1);
                                                        }
                                                        Analysis.constGeneratorAM constAM = new Analysis.constGeneratorAM(dataBuffers, dataMap, inputs, isValue, outputs);
                                                        double value = 0;
                                                        int clength = -1;
                                                        if (xpp.getAttributeValue(null, "value") != null)
                                                            value = Double.valueOf(xpp.getAttributeValue(null, "value"));
                                                        if (xpp.getAttributeValue(null, "length") != null)
                                                            clength = Integer.valueOf(xpp.getAttributeValue(null, "length"));
                                                        constAM.setParameters(value, clength);
                                                        analysis.add(constAM);
                                                        break;
                                                    default:
                                                        handled = false;

                                                }
                                                if (handled) {
                                                    boolean isStatic = Boolean.valueOf(xpp.getAttributeValue(null, "static"));
                                                    analysis.lastElement().setStatic(isStatic);
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "output":
                                    while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("export"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "audio":
                                                        audioSource = xpp.getAttributeValue(null, "input");
                                                        if (xpp.getAttributeValue(null, "rate") != null)
                                                            audioRate = Integer.valueOf(xpp.getAttributeValue(null, "rate"));
                                                        audioBufferSize = audioRate;
                                                        if (xpp.getAttributeValue(null, "buffer") != null)
                                                            audioBufferSize = Integer.valueOf(xpp.getAttributeValue(null, "buffer"));
                                                        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2*audioBufferSize, AudioTrack.MODE_STATIC);
                                                        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                                                            Toast.makeText(parent, "Could not initialize audio. (" + audioTrack.getState() + ")", Toast.LENGTH_LONG).show();
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        eventType = xpp.next();
                                    }
                                    break;
                                case "export":
                                    while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("export"))) && eventType != XmlPullParser.END_DOCUMENT) {
                                        switch (eventType) {
                                            case XmlPullParser.START_TAG:
                                                switch (xpp.getName()) {
                                                    case "set":
                                                        dataExport.exportSet set = exporter.new exportSet(xpp.getAttributeValue(null, "name"));
                                                        while ((!(eventType ==  XmlPullParser.END_TAG && xpp.getName().equals("set"))) && eventType != XmlPullParser.END_DOCUMENT) {
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
                                                        exporter.addSet(set);
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
                Toast.makeText(parent, "Error loading this experiment.", Toast.LENGTH_LONG).show();
                Log.e("loadExperiment", "Error loading this experiment.", e);
                return false;
            }

            if (experimentViews.size() == 0) {
                Toast.makeText(parent, "No valid view found.", Toast.LENGTH_LONG).show();
                return false;
            }


            return true;

        }

        @Override
        protected void onPostExecute(Boolean result) {
            progress.dismiss();
            if (result) {
                List<String> viewChoices = new ArrayList<>();

                for (expView v : experimentViews) {
                    viewChoices.add(v.name);
                }

                Spinner viewSelector = (Spinner) findViewById(R.id.viewSelector);

                ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(parent, android.R.layout.simple_spinner_item, viewChoices);
                spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                viewSelector.setAdapter(spinnerArrayAdapter);

                viewSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                        setupView(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parentView) {

                    }

                });

                ((TextView) findViewById(R.id.titleText)).setText(title);

                if (savedInstanceState != null) {
                    Vector<dataBuffer> oldData = (Vector<dataBuffer>)savedInstanceState.getSerializable(STATE_DATA_BUFFERS);
                    if (oldData != null) {
                        serverEnabled = savedInstanceState.getBoolean(STATE_REMOTE_SERVER, false);
                        timedRun = savedInstanceState.getBoolean(STATE_TIMED_RUN, false);
                        timedRunStartDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_START_DELAY);
                        timedRunStopDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_STOP_DELAY);
                        for (int i = 0; i < dataBuffers.size() && i < oldData.size(); i++) {
                            if (oldData.get(i) == null)
                                continue;
                            dataBuffers.get(i).clear();
                            Iterator it = oldData.get(i).getIterator();
                            while (it.hasNext())
                                dataBuffers.get(i).append((double) it.next());
                        }
                        currentView = savedInstanceState.getInt(STATE_CURRENT_VIEW);
                        viewSelector.setSelection(currentView);
                    }
                }
                setupView(viewSelector.getSelectedItemPosition());
                updateViewsHandler.postDelayed(updateViews, 40);

                loadCompleted = true;

                ContextThemeWrapper ctw = new ContextThemeWrapper( parent, R.style.AppTheme);
                AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
                LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
                View startInfoLayout = adbInflater.inflate(R.layout.donotshowagain, null);
                final CheckBox dontShowAgain = (CheckBox) startInfoLayout.findViewById(R.id.donotshowagain);
                adb.setView(startInfoLayout);
                adb.setTitle(R.string.info);
                adb.setMessage(R.string.startInfo);
                adb.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Boolean skipStartInfo = false;
                        if (dontShowAgain.isChecked())
                            skipStartInfo = true;
                        SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("skipStartInfo", skipStartInfo);
                        editor.apply();
                    }
                });

                SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                Boolean skipStartInfo = settings.getBoolean("skipStartInfo", false);
                if (!skipStartInfo)
                    adb.show();

            } else {
                //finish();
                //TODO create blocked app with error message
            }
        }
    }

    private void setupView(int newView) {
        LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
        ll.removeAllViews();
        for (expView.expViewElement element : experimentViews.elementAt(newView).elements) {
            element.createView(ll, this);
        }
        currentView = newView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        intent = getIntent();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment);

        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);

        res = getResources();
        exporter = new dataExport(this);

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
        new DownloadXMLTask(intent, savedInstanceState != null, this, savedInstanceState).execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_experiment, menu);
        return true;
    }

    private void startRemoteServer() {
        if (remote != null || !serverEnabled)
            return;
        remote = new remoteServer(experimentViews, dataBuffers, dataMap, getResources(), title, this);
        remote.start();
        Toast.makeText(this, "Remote access server started.", Toast.LENGTH_LONG).show();
    }

    private void stopRemoteServer() {
        if (remote == null)
            return;
        remote.stopServer();
        try {
            remote.join();
        } catch (Exception e) {
            Log.d("stopRemoteServer", "Exception on join.", e);
        }
        remote = null;
        Toast.makeText(this, "Remote access server stopped.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem timed_play = menu.findItem(R.id.action_timed_play);
        MenuItem timed_pause = menu.findItem(R.id.action_timed_pause);
        MenuItem play = menu.findItem(R.id.action_play);
        MenuItem pause = menu.findItem(R.id.action_pause);
        MenuItem timer = menu.findItem(R.id.timer);
        MenuItem timed_run = menu.findItem(R.id.action_timedRun);
        MenuItem remote = menu.findItem(R.id.action_remoteServer);
        MenuItem saveLocally = menu.findItem(R.id.action_saveLocally);

        timed_play.setVisible(!measuring && cdTimer != null);
        timed_pause.setVisible(measuring && cdTimer != null);
        play.setVisible(!measuring && cdTimer == null);
        pause.setVisible(measuring && cdTimer == null);
        timer.setVisible(timedRun);

        saveLocally.setVisible(!isLocal);

        timed_run.setChecked(timedRun);
        remote.setChecked(serverEnabled);

        if (timedRun) {
            if (cdTimer != null) {
                timer.setTitle(String.valueOf(millisUntilFinished / 1000 + 1) + "s");
            } else {
                if (measuring)
                    timer.setTitle(String.valueOf(Math.round(timedRunStopDelay))+"s");
                else
                    timer.setTitle(String.valueOf(Math.round(timedRunStartDelay))+"s");
            }
        }
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                TaskStackBuilder.create(this)
                        .addNextIntent(upIntent)
                        .startActivities();
                finish();
            } else {
                NavUtils.navigateUpTo(this, upIntent);
            }
            return true;
        }

        if (id == R.id.action_play) {
            if (timedRun) {
                startTimedMeasurement();
            } else
                startMeasurement();
            return true;
        }

        if (id == R.id.action_pause) {
            stopMeasurement();
            return true;
        }

        if (id == R.id.action_timed_play) {
            stopMeasurement();
            return true;
        }

        if (id == R.id.action_timed_pause) {
            stopMeasurement();
            return true;
        }

        if (id == R.id.action_timedRun) {
            final MenuItem itemRef = item;
            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
            View vLayout = inflater.inflate(R.layout.timed_run_layout, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final CheckBox cbTimedRunEnabled = (CheckBox) vLayout.findViewById(R.id.timedRunEnabled);
            cbTimedRunEnabled.setChecked(timedRun);
            final EditText etTimedRunStartDelay = (EditText) vLayout.findViewById(R.id.timedRunStartDelay);
            etTimedRunStartDelay.setText(String.valueOf(timedRunStartDelay));
            final EditText etTimedRunStopDelay = (EditText) vLayout.findViewById(R.id.timedRunStopDelay);
            etTimedRunStopDelay.setText(String.valueOf(timedRunStopDelay));
            builder.setView(vLayout)
                    .setTitle(R.string.timedRunDialogTitle)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            timedRun = cbTimedRunEnabled.isChecked();
                            itemRef.setChecked(timedRun);

                            String startDelayRaw = etTimedRunStartDelay.getText().toString();
                            try {
                                timedRunStartDelay = Double.valueOf(startDelayRaw);
                            } catch (Exception e) {
                                timedRunStartDelay = 0.;
                            }

                            String stopDelayRaw = etTimedRunStopDelay.getText().toString();
                            try {
                                timedRunStopDelay = Double.valueOf(stopDelayRaw);
                            } catch (Exception e) {
                                timedRunStopDelay = 0.;
                            }

                            if (timedRun && measuring)
                                stopMeasurement();
                            else
                                invalidateOptionsMenu();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        }

        if (id == R.id.action_export) {
            exporter.export(dataBuffers, dataMap);
            return true;
        }

        if (id == R.id.action_share) {
            View screenView = findViewById(R.id.rootLayout).getRootView();
            screenView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
            screenView.setDrawingCacheEnabled(false);

            File file = new File(this.getCacheDir(), "/phyphox.png");
            try {
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out);
                out.flush();
                out.close();

                final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".exportProvider", file);
                grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                final Intent intent = ShareCompat.IntentBuilder.from(this)
                        .setType("image/png")
                        .setSubject(getString(R.string.share_subject))
                        .setStream(uri)
                        .setChooserTitle(R.string.share_pick_share)
                        .createChooserIntent()
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(intent);
            } catch (Exception e) {
                Log.e("action_share", "Unhandled exception", e);
            }
        }

        if (id == R.id.action_remoteServer) {
            if (item.isChecked()) {
                item.setChecked(false);
                serverEnabled = false;
                stopRemoteServer();
            } else {
                final MenuItem itemRef = item;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(res.getString(R.string.remoteServerWarning) + "\n\n" + remoteServer.getAddresses())
                        .setTitle(R.string.remoteServerWarningTitle)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                itemRef.setChecked(true);
                                serverEnabled = true;
                                startRemoteServer();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            return true;
        }

        if (id == R.id.action_description) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(description)
                    .setTitle(R.string.show_description)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        }

        if (id == R.id.action_saveLocally) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new CopyXMLTask(intent, this).execute();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        stopRemoteServer();
        shutdown = true;
        stopMeasurement();
        super.onPause();
        overridePendingTransition(R.anim.hold, R.anim.exit_experiment);
    }

    @Override
    public void onResume() {
        super.onResume();
        shutdown = false;
        startRemoteServer();
    }

    Runnable updateViews = new Runnable() {
        @Override
        public void run() {
            try{
                if (shouldDefocus) {
                    defocus();
                    shouldDefocus = false;
                }
                if (updateState) {
                    if (remoteIntentMeasuring) {
                        if (timedRun)
                            startTimedMeasurement();
                        else
                            startMeasurement();
                    } else
                        stopMeasurement();
                    updateState = false;
                }

                synchronized (dataBuffers) {
                    if (!remoteInput) {
                        for (dataBuffer buffer : dataBuffers) {
                            for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
                                try {
                                    if (eve.getValueOutput() != null && eve.getValueOutput().equals(buffer.name)) {
                                        Double v = eve.getValue();
                                        if (!Double.isNaN(v))
                                            buffer.append(v);
                                    }
                                } catch (Exception e) {
                                    Log.e("updateViews", "Unhandled exception in view module (input) " + eve.toString() + " while sending data.", e);
                                }
                            }
                        }
                    } else
                        remoteInput = false;

                    if (measuring && System.currentTimeMillis() - analysisStart > analysisPeriod * 1000) {
                        if (audioRecord != null) {
                            audioRecord.stop();
                            int bytesRead = 1;
                            short[] buffer = new short[1024];
                            while (bytesRead > 0) {
                                bytesRead = audioRecord.read(buffer, 0, 1024);
                                dataBuffers.get(dataMap.get(micOutput)).append(buffer, bytesRead);
                            }
                        }
                        for (Analysis.analysisModule mod : analysis) {
                            try {
                                mod.updateIfNotStatic();
                            } catch (Exception e) {
                                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                            }
                        }
                        if (audioTrack != null) {
                            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                                audioTrack.stop();
                            if (!(audioTrack.getState() == AudioTrack.STATE_INITIALIZED && dataBuffers.get(dataMap.get(audioSource)).isStatic)) {
                                short[] data = dataBuffers.get(dataMap.get(audioSource)).getShortArray();
                                int result = audioTrack.write(data, 0, audioBufferSize);
                                if (result < audioBufferSize)
                                    Log.d("updateViews", "Unexpected audio write result: " + result + " written / " + audioBufferSize + " buffer size");
                                audioTrack.reloadStaticData();
                            } else
                                audioTrack.setPlaybackHeadPosition(0);
                        }
                        if (audioRecord != null) {
                            audioRecord.startRecording();
                        }
                        if (audioTrack != null) {
                            audioTrack.play();
                        }
                        analysisStart = System.currentTimeMillis();
                    }
                }
                for (dataBuffer buffer : dataBuffers) {
                    for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
                        try {
                            if (eve.getValueInput() != null && eve.getValueInput().equals(buffer.name)) {
                                eve.setValue(buffer.value);
                            }
                            if (eve.getDataXInput() != null && eve.getDataXInput().equals(buffer.name)) {
                                eve.setDataX(buffer);
                            }
                            if (eve.getDataYInput() != null && eve.getDataYInput().equals(buffer.name)) {
                                eve.setDataY(buffer);
                            }
                        } catch (Exception e) {
                            Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " while sending data.", e);
                        }
                    }
                }
                for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
                    try {
                        eve.dataComplete();
                    } catch (Exception e) {
                        Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " on data completion.", e);
                    }
                }
            }
            catch (Exception e) {
                Log.e("updateViews", "Unhandled exception.", e);
            }
            finally{
                if (!shutdown) {
                    if (measuring)
                        updateViewsHandler.postDelayed(this, 40);
                    else
                        updateViewsHandler.postDelayed(this, 400);
                }
            }
        }
    };

    private void lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            Display display = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            int rotation = display.getRotation();
            int tempOrientation = this.getResources().getConfiguration().orientation;
            int orientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            switch (tempOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Configuration.ORIENTATION_PORTRAIT:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
            setRequestedOrientation(orientation);
        }
    }

    public void startMeasurement() {
        synchronized (dataBuffers) {
            for (dataBuffer buffer : dataBuffers)
                buffer.clear();
            long t0 = System.nanoTime();
            for (sensorInput sensor : inputSensors)
                sensor.start(t0);
        }

        measuring = true;

        lockScreen();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (timedRun) {
            millisUntilFinished = Math.round(timedRunStopDelay * 1000);
            cdTimer = new CountDownTimer(millisUntilFinished, 100) {

                public void onTick(long muf) {
                    millisUntilFinished = muf;
                    invalidateOptionsMenu();
                }

                public void onFinish() {
                    stopMeasurement();
                }
            }.start();
        }
        invalidateOptionsMenu();
    }

    public void startTimedMeasurement() {
        lockScreen();
        millisUntilFinished = Math.round(timedRunStartDelay*1000);
        cdTimer = new CountDownTimer(millisUntilFinished, 100) {

            public void onTick(long muf) {
                millisUntilFinished = muf;
                invalidateOptionsMenu();
            }

            public void onFinish() {
                startMeasurement();
            }
        }.start();
        invalidateOptionsMenu();
    }

    public void stopMeasurement() {
        measuring = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        if (cdTimer != null) {
            cdTimer.cancel();
            cdTimer = null;
            millisUntilFinished = 0;
        }
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.stop();
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
            audioTrack.stop();
        for (sensorInput sensor : inputSensors)
            sensor.stop();
        invalidateOptionsMenu();
    //    titleText.setBackgroundColor(res.getColor(R.color.main));
    }

    public void remoteStopMeasurement() {
        remoteIntentMeasuring = false;
        updateState = true;
    }

    public void remoteStartMeasurement() {
        remoteIntentMeasuring = true;
        updateState = true;
    }

    public void requestDefocus() {
        shouldDefocus = true;
    }

    public void defocus() {
        findViewById(R.id.experimentView).requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!loadCompleted)
            return;
        try {
            outState.putInt(STATE_CURRENT_VIEW, currentView);
            synchronized (dataBuffers) {
                outState.putSerializable(STATE_DATA_BUFFERS, dataBuffers);
            }
            outState.putBoolean(STATE_REMOTE_SERVER, serverEnabled);
            outState.putBoolean(STATE_TIMED_RUN, timedRun);
            outState.putDouble(STATE_TIMED_RUN_START_DELAY, timedRunStartDelay);
            outState.putDouble(STATE_TIMED_RUN_STOP_DELAY, timedRunStopDelay);
        } catch (Exception e) {
            outState.clear();
        }
    }


}
