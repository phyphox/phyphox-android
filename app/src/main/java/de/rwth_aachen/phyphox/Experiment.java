package de.rwth_aachen.phyphox;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

//TODO Auto-Pause / Auto-Start
//TODO User-Input view
//TODO Raw-Experiment
//TODO Inputs/Outputs: Audio
//TODO Translation of Experiment-Texts

public class Experiment extends AppCompatActivity {


    boolean measuring = false;
    boolean updateState = false;
    boolean shutdown = false;
    final Handler updateViewsHandler = new Handler();

    String title = "";
    String category = "";
    String icon = "";
    private Vector<expView> experimentViews = new Vector<>();
    private expView currentView;
    private Vector<sensorInput> inputSensors = new Vector<>();
    public final Vector<dataBuffer> dataBuffers = new Vector<>();
    public final Map<String, Integer> dataMap = new HashMap<>();
    private Vector<Analysis.analysisModule> analysis = new Vector<>();
    private Resources res;

    private boolean serverEnabled = false;

    public dataExport exporter;

    private SensorManager sensorManager;

    private remoteServer remote = null;

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
                                                                        expView.valueElement ve = newView.new valueElement(xpp.getAttributeValue(null, "label"), xpp.getAttributeValue(null, "input"), null, null, getResources());
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
                                                                        expView.graphElement ge = newView.new graphElement(xpp.getAttributeValue(null, "label"), null, xpp.getAttributeValue(null, "inputX"), xpp.getAttributeValue(null, "inputY"), getResources());
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
                                            Vector <String> inputs = new Vector<>();
                                            Vector <Boolean> isValue = new Vector<>();
                                            while (xpp.getAttributeValue(null, "input"+i) != null) {
                                                isValue.add(xpp.getAttributeValue(null, "type"+i) != null && xpp.getAttributeValue(null, "type"+i).equals("value"));
                                                if (!isValue.lastElement()) {
                                                    int inputSize = dataBuffers.get(dataMap.get(xpp.getAttributeValue(null, "input" + i))).size;
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

                                            switch (xpp.getName()) {
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
        MenuItem play = menu.findItem(R.id.action_play);
        MenuItem pause = menu.findItem(R.id.action_pause);
        play.setVisible(!measuring);
        pause.setVisible(measuring);
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
            startMeasurement();
            return true;
        }

        if (id == R.id.action_pause) {
            stopMeasurement();
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
        startMeasurement();
        startRemoteServer();
    }

    Runnable updateViews = new Runnable() {
        @Override
        public void run() {
            try{
                if (updateState) {
                    if (measuring)
                        startMeasurement();
                    else
                        stopMeasurement();
                    updateState = false;
                }
                if (measuring) {
                    synchronized (dataBuffers) {
                        for (Analysis.analysisModule mod : analysis) {
                            try {
                                mod.update();
                            } catch (Exception e) {
                                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                            }
                        }
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
                        updateViewsHandler.postDelayed(this, 500);
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
        invalidateOptionsMenu();
    //    titleText.setBackgroundColor(res.getColor(R.color.highlight));
    }

    public void stopMeasurement() {
        measuring = false;
        for (sensorInput sensor : inputSensors)
            sensor.stop();
        invalidateOptionsMenu();
    //    titleText.setBackgroundColor(res.getColor(R.color.main));
    }

    public void remoteStopMeasurement() {
        measuring = false;
        updateState = true;
    }

    public void remoteStartMeasurement() {
        measuring = true;
        updateState = true;
    }

}
