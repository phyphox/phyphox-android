package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

//TODO Clean-up loading-code and make it more error-proof
//TODO Clean-up and annotate everything
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

    long analysisStart = 0;

    phyphoxFile.phyphoxExperiment experiment;

    private int currentView;

    private Resources res;

    private boolean serverEnabled = false;

    public SensorManager sensorManager;

    private remoteServer remote = null;
    public boolean remoteInput = false;
    public boolean shouldDefocus = false;

    Intent intent;
    ProgressDialog progress;

    Bundle savedInstanceState = null;

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

    private class CopyXMLTask extends AsyncTask<String, Void, Boolean> {
        private Intent intent;
        private Activity parent;

        CopyXMLTask(Intent intent, Activity parent) {
            this.intent = intent;
            this.parent = parent;
        }

        protected Boolean doInBackground(String... params) {
            phyphoxFile.PhyphoxStream input = phyphoxFile.openXMLInputStream(intent, parent);
            //TODO process error message in phyphox stream
            try {
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";
                FileOutputStream output = parent.openFileOutput(file, MODE_PRIVATE);
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.inputStream.read(buffer)) != -1)
                    output.write(buffer, 0, count);
                output.close();
                input.inputStream.close();
            } catch (Exception e) {
                //TODO relay error message to main thread
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

    private void setupView(int newView) {
        LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
        ll.removeAllViews();
        for (expView.expViewElement element : experiment.experimentViews.elementAt(newView).elements) {
            element.createView(ll, this);
        }
        currentView = newView;
    }

    public void onExperimentLoaded(phyphoxFile.phyphoxExperiment experiment) {
        progress.dismiss();
        this.experiment = experiment;
        if (experiment.loaded) {
            List<String> viewChoices = new ArrayList<>();

            for (expView v : experiment.experimentViews) {
                viewChoices.add(v.name);
            }

            Spinner viewSelector = (Spinner) findViewById(R.id.viewSelector);

            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, viewChoices);
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

            ((TextView) findViewById(R.id.titleText)).setText(experiment.title);

            if (savedInstanceState != null) {
                Vector<dataBuffer> oldData = (Vector<dataBuffer>) savedInstanceState.getSerializable(STATE_DATA_BUFFERS);
                if (oldData != null) {
                    serverEnabled = savedInstanceState.getBoolean(STATE_REMOTE_SERVER, false);
                    timedRun = savedInstanceState.getBoolean(STATE_TIMED_RUN, false);
                    timedRunStartDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_START_DELAY);
                    timedRunStopDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_STOP_DELAY);
                    for (int i = 0; i < experiment.dataBuffers.size() && i < oldData.size(); i++) {
                        if (oldData.get(i) == null)
                            continue;
                        experiment.dataBuffers.get(i).clear();
                        Iterator it = oldData.get(i).getIterator();
                        while (it.hasNext())
                            experiment.dataBuffers.get(i).append((double) it.next());
                    }
                    currentView = savedInstanceState.getInt(STATE_CURRENT_VIEW);
                    viewSelector.setSelection(currentView);
                }
            }
            setupView(viewSelector.getSelectedItemPosition());
            updateViewsHandler.postDelayed(updateViews, 40);
            invalidateOptionsMenu();
            loadCompleted = true;

            startRemoteServer();

            ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.AppTheme);
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
            LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
            TextView errorView = new TextView(this);
            errorView.setText(experiment.message);
            errorView.setGravity(Gravity.CENTER);
            errorView.setTextColor(res.getColor(R.color.main));

            ll.addView(errorView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        intent = getIntent();
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;
        setContentView(R.layout.activity_experiment);

        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);

        res = getResources();

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
        new phyphoxFile.loadXMLAsyncTask(intent, this).execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_experiment, menu);
        return true;
    }

    private void startRemoteServer() {
        if (remote != null || !serverEnabled)
            return;
        remote = new remoteServer(experiment.experimentViews, experiment.dataBuffers, experiment.dataMap, getResources(), experiment.title, this);
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
        if (experiment == null || !experiment.loaded) {
            return false;
        }
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

        saveLocally.setVisible(!experiment.isLocal);

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
            experiment.exporter.export(experiment.dataBuffers, experiment.dataMap);
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
            builder.setMessage(experiment.description)
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

                synchronized (experiment.dataBuffers) {
                    if (!remoteInput) {
                        for (dataBuffer buffer : experiment.dataBuffers) {
                            for (expView.expViewElement eve : experiment.experimentViews.elementAt(currentView).elements) {
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

                    if (measuring && System.currentTimeMillis() - analysisStart > experiment.analysisPeriod * 1000) {
                        if (experiment.audioRecord != null) {
                            experiment.audioRecord.stop();
                            int bytesRead = 1;
                            short[] buffer = new short[1024];
                            while (bytesRead > 0) {
                                bytesRead = experiment.audioRecord.read(buffer, 0, 1024);
                                experiment.dataBuffers.get(experiment.dataMap.get(experiment.micOutput)).append(buffer, bytesRead);
                            }
                        }
                        for (Analysis.analysisModule mod : experiment.analysis) {
                            try {
                                mod.updateIfNotStatic();
                            } catch (Exception e) {
                                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                            }
                        }
                        if (experiment.audioTrack != null) {
                            if (experiment.audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                                experiment.audioTrack.stop();
                            if (!(experiment.audioTrack.getState() == AudioTrack.STATE_INITIALIZED && experiment.dataBuffers.get(experiment.dataMap.get(experiment.audioSource)).isStatic)) {
                                short[] data = experiment.dataBuffers.get(experiment.dataMap.get(experiment.audioSource)).getShortArray();
                                int result = experiment.audioTrack.write(data, 0, experiment.audioBufferSize);
                                if (result < experiment.audioBufferSize)
                                    Log.d("updateViews", "Unexpected audio write result: " + result + " written / " + experiment.audioBufferSize + " buffer size");
                                experiment.audioTrack.reloadStaticData();
                            } else
                                experiment.audioTrack.setPlaybackHeadPosition(0);
                        }
                        if (experiment.audioRecord != null) {
                            experiment.audioRecord.startRecording();
                        }
                        if (experiment.audioTrack != null) {
                            experiment.audioTrack.play();
                        }
                        analysisStart = System.currentTimeMillis();
                    }
                }
                for (dataBuffer buffer : experiment.dataBuffers) {
                    for (expView.expViewElement eve : experiment.experimentViews.elementAt(currentView).elements) {
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
                for (expView.expViewElement eve : experiment.experimentViews.elementAt(currentView).elements) {
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
        synchronized (experiment.dataBuffers) {
            for (dataBuffer buffer : experiment.dataBuffers)
                buffer.clear();
            long t0 = System.nanoTime();
            for (sensorInput sensor : experiment.inputSensors)
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
        if (experiment.audioRecord != null && experiment.audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            experiment.audioRecord.stop();
        if (experiment.audioTrack != null && experiment.audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
            experiment.audioTrack.stop();
        for (sensorInput sensor : experiment.inputSensors)
            sensor.stop();
        invalidateOptionsMenu();
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
            synchronized (experiment.dataBuffers) {
                outState.putSerializable(STATE_DATA_BUFFERS, experiment.dataBuffers);
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
