package de.rwth_aachen.phyphox;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

//TODO Raw-Experiment (Select inputs, buffer length and aquisition rate)
//TODO Inputs/Outputs: Audio
//TODO Translation of Experiment-Texts

public class Experiment extends AppCompatActivity {


    boolean measuring = false;
    boolean remoteIntentMeasuring = false;
    boolean updateState = false;
    boolean shutdown = false;
    boolean timedRun = false;
    double timedRunStartDelay = 0.;
    double timedRunStopDelay = 0.;
    CountDownTimer cdTimer = null;
    long millisUntilFinished = 0;
    final Handler updateViewsHandler = new Handler();

    double outputPeriod = 0.;
    long outputStart = 0;

    String title = "";
    String category = "";
    String icon = "";
    private Vector<expView> experimentViews = new Vector<>();
    private expView currentView;
    private Vector<sensorInput> inputSensors = new Vector<>();
    public final Vector<dataBuffer> dataBuffers = new Vector<>();
    public final Map<String, Integer> dataMap = new HashMap<>();
    private Vector<Analysis.analysisModule> analysis = new Vector<>();
    AudioTrack audioTrack = null;
    String audioSource;
    int audioRate = 48000;
    int audioBufferSize = 0;
    private Resources res;

    private boolean serverEnabled = false;

    public dataExport exporter;

    private SensorManager sensorManager;

    private remoteServer remote = null;
    public boolean remoteInput = false;
    public boolean shouldDefocus = false;

    private boolean loadExperiment(InputStream xml) {
        try {
            XmlPullParser xpp = Xml.newPullParser();
            xpp.setInput(xml, "UTF-8");

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
                                                        Toast.makeText(this, "Invalid view ignored.", Toast.LENGTH_LONG).show();
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
                                                        if (!inputSensors.lastElement().isAvailable()) {
                                                            new AlertDialog.Builder(this)
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
                                                        Toast.makeText(this, "Undefined sensor ignored.", Toast.LENGTH_LONG).show();
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
                                outputPeriod = Double.valueOf(xpp.getAttributeValue(null, "period"));
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
                                                        Toast.makeText(this, "Could not initialize audio. (" + audioTrack.getState() + ")", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Error loading this experiment.", Toast.LENGTH_LONG).show();
            Log.e("loadExperiment", "Error loading this experiment.", e);
            return false;
        }

        if (experimentViews.size() == 0) {
            Toast.makeText(this, "No valid view found.", Toast.LENGTH_LONG).show();
            return false;
        }


        return true;
    }

    private void setupView(expView newView) {
        LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
        ll.removeAllViews();
        for (expView.expViewElement element : newView.elements) {
            element.createView(ll, this);
        }
        currentView = newView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment);

        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);

        res = getResources();
        exporter = new dataExport(this);

        Intent intent = getIntent();
        AssetManager assetManager = getAssets();
        InputStream input;
        try {
            input = assetManager.open("experiments/" + intent.getStringExtra(ExperimentList.EXPERIMENT_XML));
        } catch (Exception e) {
            Toast.makeText(this, "Error loading this experiment from assets.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        if (loadExperiment(input)) {
            List<String> viewChoices = new ArrayList<>();

            for (expView v : experimentViews) {
                viewChoices.add(v.name);
            }

            Spinner viewSelector = (Spinner) findViewById(R.id.viewSelector);

            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, viewChoices);
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            viewSelector.setAdapter(spinnerArrayAdapter);

            viewSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    setupView(experimentViews.elementAt(position));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {

                }

            });

            ((TextView) findViewById(R.id.titleText)).setText(title);

            setupView(experimentViews.elementAt(viewSelector.getSelectedItemPosition()));

            startMeasurement();
            updateViewsHandler.postDelayed(updateViews, 40);

        } else
            finish();
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

        timed_play.setVisible(!measuring && cdTimer != null);
        timed_pause.setVisible(measuring && cdTimer != null);
        play.setVisible(!measuring && cdTimer == null);
        pause.setVisible(measuring && cdTimer == null);
        timer.setVisible(timedRun);

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
            Intent upIntent = new Intent(this, ExperimentList.class);
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
                            for (expView.expViewElement eve : currentView.elements) {
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
                    if (measuring && System.currentTimeMillis() - outputStart > outputPeriod * 1000) {
                        for (Analysis.analysisModule mod : analysis) {
                            try {
                                mod.updateIfNotStatic();
                            } catch (Exception e) {
                                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                            }
                        }
                        if (audioTrack != null) {
                            short[] data = dataBuffers.get(dataMap.get(audioSource)).getShortArray();
                            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                                audioTrack.stop();
                            int result = audioTrack.write(data, 0, audioBufferSize);
                            if (result < audioBufferSize)
                                Log.d("debug", "Unexpected audio write: " + result + " written / " + audioBufferSize + " buffer size");
                            audioTrack.reloadStaticData();
                            audioTrack.play();
                        }
                        outputStart = System.currentTimeMillis();
                    }
                }
                for (dataBuffer buffer : dataBuffers) {
                    for (expView.expViewElement eve : currentView.elements) {
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
                for (expView.expViewElement eve : currentView.elements) {
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

    public void startMeasurement() {
        synchronized (dataBuffers) {
            for (dataBuffer buffer : dataBuffers)
                buffer.clear();
            long t0 = System.nanoTime();
            for (sensorInput sensor : inputSensors)
                sensor.start(t0);
        }
        measuring = true;
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
        if (cdTimer != null) {
            cdTimer.cancel();
            cdTimer = null;
            millisUntilFinished = 0;
        }
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

}
