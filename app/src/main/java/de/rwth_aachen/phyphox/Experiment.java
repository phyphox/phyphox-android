package de.rwth_aachen.phyphox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.core.app.ShareCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.FileProvider;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothInput;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothOutput;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;

// Experiments are performed in this activity, which reacts to various intents.
// The intent has to provide a *.phyphox file which defines the experiment
public class Experiment extends AppCompatActivity implements View.OnClickListener, NetworkConnection.ScanDialogDismissedDelegate, NetworkConnection.NetworkConnectionDataPolicyInfoDelegate {

    //String constants to identify values saved in onSaveInstanceState
    private static final String STATE_CURRENT_VIEW = "current_view"; //Which experiment view is selected?
    private static final String STATE_REMOTE_SERVER = "remote_server"; //Is the remote server activated?
    private static final String STATE_REMOTE_SESSION_ID = "remote_session_id"; //The session ID of the remote server
    private static final String STATE_BEFORE_START = "before_start"; //Has the experiment been started?
    private static final String STATE_TIMED_RUN = "timed_run"; //Are timed runs activated?
    private static final String STATE_TIMED_RUN_START_DELAY = "timed_run_start_delay"; //The start delay for a timed run
    private static final String STATE_TIMED_RUN_STOP_DELAY = "timed_run_stop_delay"; //The stop delay for a timed run
    private static final String STATE_TIMED_RUN_BEEP_COUNTDOWN = "timed_run_beep_countdown";
    private static final String STATE_TIMED_RUN_BEEP_START = "timed_run_beep_start";
    private static final String STATE_TIMED_RUN_BEEP_RUNNING = "timed_run_beep_running";
    private static final String STATE_TIMED_RUN_BEEP_STOP = "timed_run_beep_stop";
    private static final String STATE_MENU_HINT_DISMISSED = "menu_hint_dismissed";
    private static final String STATE_START_HINT_DISMISSED = "start_hint_dismissed";
    private static final String STATE_SAVE_LOCALLY_DISMISSED = "save_locally_dismissed";

    //This handler creates the "main loop" as it is repeatedly called using postDelayed
    //Not a real loop to keep some resources available
    final Handler updateViewsHandler = new Handler();

    //Status variables
    boolean measuring = false; //Measurement running?
    boolean loadCompleted = false; //Set to true when an experiment has been loaded successfully
    boolean shutdown = false; //The activity should be stopped. Used to escape the measurement loop.
    boolean beforeStart = true; //Experiment has not yet been started even once
    boolean menuHintDismissed = false; //Remember that the user has clicked away the hint to the menu
    boolean startHintDismissed = false; //Remember that the user has clicked away the hint to the start button
    boolean saveLocallyDismissed = false; //Remember that the user did not want to save this experiment locally

    //Remote server
    private RemoteServer remote = null; //The remote server (see remoteServer class)
    private boolean serverEnabled = false; //Is the remote server activated?
    boolean remoteIntentMeasuring = false; //Is the remote interface expecting that the measurement is running?
    boolean updateState = false; //This is set to true when a state changed is initialized remotely. The measurement state will then be set to remoteIntentMeasuring.
    public boolean remoteInput = false; //Has there been an data input (inputViews for now) from the remote server that should be processed?
    public boolean shouldDefocus = false; //Should the current view loose focus? (Neccessary to remotely edit an input view, which has focus on this device)
    private String sessionID = "";

    //Timed run status
    boolean timedRun = false; //Timed run enabled?
    double timedRunStartDelay = 3.; //Start delay for timed runs
    double timedRunStopDelay = 10.; //Stop delay for timed runs
    boolean timedRunBeepCountdown = false;
    boolean timedRunBeepStart = false;
    boolean timedRunBeepRunning = false;
    boolean timedRunBeepStop = false;
    CountDownTimer cdTimer = null; //This holds the timer used for timed runs. If it is not null, a timed run is running and at the end of the countdown the measurement state will change
    long millisUntilFinished = 0; //This variable is used to cache the remaining countdown, so it is available outside the onTick-callback of the timer

    //The experiment
    PhyphoxExperiment experiment; //The experiment (definition and functionality) after it has been loaded.
    TabLayout tabLayout;
    ViewPager pager;
    ExpViewPagerAdapter adapter;

    //Others...
    private Resources res; //Helper to easily access resources
    public SensorManager sensorManager; //The sensor manager
    Intent intent; //Another helper to easily access the data of the intent that triggered this activity
    ProgressDialog progress; //Holds a progress dialog when a file is being loaded
    Bundle savedInstanceState = null; //Holds the saved instance state, so it can be handled outside onCreate
    MenuItem startMenuItem = null; //Reference to play-hint button
    ImageView hintAnimation = null; //Reference to the animated part of the play-hint button

    //The analysis progress bar
    ProgressBar analysisProgress;       //Reference to the progress bar view
    boolean analysisInProgress = false; //Set to true by second thread while analysis is running
    float analysisProgressAlpha = 0.f;  //Will be increased while analysis is running and decreased while idle. This smoothes the display and results in an everage transparency representing the average load.

    PopupWindow popupWindow = null;
    AudioOutput audioOutput = null;

    private void doLeaveExperiment(Activity activity) {
        Intent upIntent = NavUtils.getParentActivityIntent(activity);
        if (NavUtils.shouldUpRecreateTask(activity, upIntent)) {
            TaskStackBuilder.create(activity)
                    .addNextIntent(upIntent)
                    .startActivities();
            finish();
        } else {
            NavUtils.navigateUpTo(activity, upIntent);
        }
    }

    private void leaveExperiment(Activity activity) {
        if (experiment != null && experiment.analysisTime > 10.0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(res.getString(R.string.leave_experiment_question))
                    .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            doLeaveExperiment(activity);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            doLeaveExperiment(activity);
        }
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && pager != null) {
            ExpViewFragment f = (ExpViewFragment)getSupportFragmentManager().findFragmentByTag("android:switcher:" + pager.getId() + ":" + adapter.getItemId(pager.getCurrentItem()));
            if (f != null && f.hasExclusive()) {
                f.leaveExclusive();
                return;
            }
        }
        leaveExperiment(this);
        //super.onBackPressed();
    }

    @Override
    //Where it all begins...
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intent = getIntent(); //Store the intent for easy access
        res = getResources(); //The same for resources
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...

        this.savedInstanceState = savedInstanceState; //Store savedInstanceState so it can be accessed after loading the experiment in a second thread
        setContentView(R.layout.activity_experiment); //Setup the views...

        this.analysisProgress = (ProgressBar)findViewById(R.id.progressBar);
        analysisProgress.setVisibility(View.INVISIBLE);

        //Set our custom action bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.customActionBar);
        setSupportActionBar(toolbar);

        //We want to get the back-button in the actionbar (even on old Android versions)
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowTitleEnabled(false);
        }

        if (savedInstanceState != null) {
            App app = (App) this.getApplicationContext();
            experiment = app.experiment;
            //experiment = (phyphoxExperiment) savedInstanceState.getSerializable(STATE_EXPERIMENT);
        };
        if (experiment != null) {
            //We saved our experiment. Lets just retrieve it and continue
            onExperimentLoaded(experiment);
        } else {
            //Start loading the experiment in a second thread (mostly for network loading, but it won't hurt in any case...)
            //So display a ProgressDialog and instantiate and execute loadXMLAsyncTask (see phyphoxFile class)
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            (new PhyphoxFile.loadXMLAsyncTask(intent, this)).execute();
        }

    }

    @Override
    //onPause event
    public void onStop() {
        super.onStop();
        hidePlayHintAnimation();

        try {
            progress.dismiss(); //Close progress display
        } catch (Exception e) {
            //This should only fail if the window has already been destroyed. Ignore.
        } finally {
            progress = null;
        }

        stopRemoteServer(); //Remote server should stop when the app is not active
        shutdown = true; //Stop the loop
        stopMeasurement(); //Stop the measurement

        if (experiment != null && experiment.loaded) {
            for (NetworkConnection networkConnection : experiment.networkConnections) {
                networkConnection.disconnect();
                networkConnection.specificAddress = null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                //Close all bluetooth connections, when the activity is recreated, they will be reestablished while initializing the experiment
                for (BluetoothInput bti : experiment.bluetoothInputs)
                    bti.closeConnection();
                for (BluetoothOutput bti : experiment.bluetoothOutputs)
                    bti.closeConnection();
            }
        }

        if (popupWindow != null)
            popupWindow.dismiss();

        overridePendingTransition(R.anim.hold, R.anim.exit_experiment); //Make a nice animation...
    }

    @Override
    //Let's start again
    public void onRestart() {
        super.onRestart();

        shutdown = false; //Deactivate shutdown variable

        updateViewsHandler.postDelayed(updateViews, 40); //Start the "main loop" again
        startRemoteServer();  //Restart the remote server (if it is activated)
        //We do not start the measurement again automatically. If the user switched away, this might
        //   be confusing otherwise.

        invalidateOptionsMenu();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.recreate();
        }
    }

    //This is called from the CopyXML thread in onPostExecute, so the copying process of a remote
    //   experiment to the local collection has been completed
    public void onCopyXMLCompleted(String result) {
        try {
            progress.dismiss(); //Close progress display
        } catch (Exception e) {
            //This should only fail if the window has already been destroyed. Ignore.
        } finally {
            progress = null;
        }
        if (result.equals("")) { //No error
            //Show that it has been successfull and return to the experiment selection so the user
            //  can see the new addition to his collection
            Toast.makeText(this, R.string.save_locally_done, Toast.LENGTH_LONG).show(); //Present message
            //Create intent to experiment list
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            TaskStackBuilder.create(this)
                    .addNextIntent(upIntent)
                    .startActivities();
            finish(); //Close this activity
        } else // There has been an error
            Toast.makeText(this, result, Toast.LENGTH_LONG).show(); //Show error to user
    }

    //This is called from the experiment loading thread in onPostExecute, so the experiment should
    //   be ready and can be presented to the user
    public void onExperimentLoaded(PhyphoxExperiment experiment) {
        try {
            progress.dismiss(); //Close progress display
        } catch (Exception e) {
            //This should only fail if the window has already been destroyed. Ignore.
        } finally {
            progress = null;
        }
        this.experiment = experiment; //Store the loaded experiment
        if (experiment.loaded) { //Everything went fine, no errors
            if (experiment.gpsIn != null) {
                experiment.gpsIn.prepare(res);
            }

            timedRun = experiment.timedRun;
            timedRunStartDelay = experiment.timedRunStartDelay;
            timedRunStopDelay = experiment.timedRunStopDelay;

            //If the experiment has been launched from a Bluetooth scan, we need to set the bluetooth device in the experiment so it does not ask the user again
            String btAddress = intent.getStringExtra(ExperimentList.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS);
            if (btAddress != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                for (Bluetooth bt : this.experiment.bluetoothInputs)
                    bt.deviceAddress = btAddress;
                for (Bluetooth bt : this.experiment.bluetoothOutputs)
                    bt.deviceAddress = btAddress;
            }

            //We should set the experiment title....
            TextView titleText = ((TextView) findViewById(R.id.titleText));
            titleText.setText(experiment.title);
            float defaultSize = titleText.getTextSize();
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(titleText, (int)(defaultSize*0.49), Math.round(defaultSize), 1, TypedValue.COMPLEX_UNIT_PX);


            int startView = 0;

            //If we have a savedInstanceState, it would be a good time to interpret it...
            if (savedInstanceState != null) {
                //Reload the states that the user can control
                serverEnabled = savedInstanceState.getBoolean(STATE_REMOTE_SERVER, false); //Remote server activated?
                sessionID = savedInstanceState.getString(STATE_REMOTE_SESSION_ID, ""); //Remote server session id?
                beforeStart = savedInstanceState.getBoolean(STATE_BEFORE_START, false); //Has the experiment ever been started?
                timedRun = savedInstanceState.getBoolean(STATE_TIMED_RUN, false); //timed run activated?
                timedRunStartDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_START_DELAY); //start elay of timed run
                timedRunStopDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_STOP_DELAY); //stop delay of timed run
                timedRunBeepCountdown = savedInstanceState.getBoolean(STATE_TIMED_RUN_BEEP_COUNTDOWN);
                timedRunBeepStart = savedInstanceState.getBoolean(STATE_TIMED_RUN_BEEP_START);
                timedRunBeepRunning = savedInstanceState.getBoolean(STATE_TIMED_RUN_BEEP_RUNNING);
                timedRunBeepStop = savedInstanceState.getBoolean(STATE_TIMED_RUN_BEEP_STOP);
                menuHintDismissed = savedInstanceState.getBoolean(STATE_MENU_HINT_DISMISSED);
                startHintDismissed = savedInstanceState.getBoolean(STATE_START_HINT_DISMISSED);
                saveLocallyDismissed = savedInstanceState.getBoolean(STATE_SAVE_LOCALLY_DISMISSED);

                //Which view was active when we were stopped?
                startView = savedInstanceState.getInt(STATE_CURRENT_VIEW);
            }

            tabLayout = ((TabLayout)findViewById(R.id.tab_layout));
            pager = ((ViewPager)findViewById(R.id.view_pager));
            FragmentManager manager = getSupportFragmentManager();
            adapter = new ExpViewPagerAdapter(manager, this.experiment);
            pager.setAdapter(adapter);
            tabLayout.setupWithViewPager(pager);
            pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

            for (int i = 0; i < adapter.getCount(); i++) {
                ExpViewFragment f = (ExpViewFragment)getSupportFragmentManager().findFragmentByTag("android:switcher:" + pager.getId() + ":" + adapter.getItemId(i));
                if (f != null)
                    f.recreateView();
            }

            if (adapter.getCount() < 2)
                tabLayout.setVisibility(View.GONE);

            try {
                experiment.init(sensorManager, (LocationManager)this.getSystemService(Context.LOCATION_SERVICE));
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }

            tabLayout.getTabAt(startView).select();

            //Everything is ready. Let's start the "main loop"
            loadCompleted = true;

            updateViewsHandler.postDelayed(updateViews, 40);

            //Also invalidate the options menu, so it can activate any controls, that are valid for a loaded experiment
            invalidateOptionsMenu();

            //Start the remote server if activated
            startRemoteServer();
        } else {
            //There has been an error. Show the error to the user and leave the activity in its
            //   non-interactive state...

            //Append TextView with error message to the base linear layout
            TextView tv = (TextView) findViewById(R.id.errorMessage);
            tv.setText(experiment.message);
            tv.setVisibility(View.VISIBLE);
            this.experiment = null;
        }

        for (SensorInput sensor : experiment.inputSensors) {
            if (sensor.vendorSensor) {
                showSensorWarning(sensor);
            }
        }

        //Check if experiment is already in list and if so, flag it as local.
        if (experiment.source != null && Helper.experimentInCollection(experiment.crc32, this)) {
            experiment.isLocal = true;
        }

        if (experiment.loaded && experiment.networkConnections.size() > 0) {
            String[] sensors = new String[experiment.inputSensors.size()];
            for (int i = 0; i < experiment.inputSensors.size(); i++) {
                sensors[i] = res.getString(experiment.inputSensors.get(i).getDescriptionRes());
            }
            experiment.networkConnections.get(0).getDataAndPolicyDialog(experiment.audioRecord != null, experiment.gpsIn != null, experiment.inputSensors.size() > 0, sensors, this, this).show();
        } else if (!experiment.isLocal && experiment.loaded) { //If this experiment has been loaded from a external source, we offer to save it locally
            menuHintDismissed = true; //Do not show menu startMenuItem for external experiments

            if (!saveLocallyDismissed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(res.getString(R.string.save_locally_message))
                        .setTitle(R.string.save_locally)
                        .setPositiveButton(R.string.save_locally_button, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                progress = ProgressDialog.show(Experiment.this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
                                new PhyphoxFile.CopyXMLTask(intent, Experiment.this).execute();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                saveLocallyDismissed = true;
                                connectBluetoothDevices(false, false);
                            }
                        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                saveLocallyDismissed = true;
                                connectBluetoothDevices(false, false);
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                connectBluetoothDevices(false, false);
            }
        } else if (experiment.loaded) {
            connectBluetoothDevices(false, false);
        }

        //An explanation is not necessary for raw sensors and of course we don't want it if there is an error
        if (experiment.category.equals(res.getString(R.string.categoryRawSensor)) || !experiment.loaded)
            menuHintDismissed = true;

        if (!experiment.loaded)
            startHintDismissed = true;

        //If the hint has been shown a few times, we do not show it again
        SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
        int menuHintDismissCount= settings.getInt("menuHintDismissCount", 0);
        if (menuHintDismissCount >= 3)
            menuHintDismissed = true;

        //If the start button has been used a few times, we do not show its hint again
        int startHintDismissCount= settings.getInt("startHintDismissCount", 0);
        if (startHintDismissCount >= 3)
            startHintDismissed = true;

        if (!menuHintDismissed)
            showMenuHint();
        else if (!startHintDismissed)
            showStartHint();
    }

    private void showSensorWarning(SensorInput sensor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(res.getString(R.string.vendorSensorWarning1) + " " + res.getString(sensor.getDescriptionRes()) + " " + res.getString(R.string.vendorSensorWarning2))
                .setTitle(R.string.vendorSensorTitle)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void connectNetworkConnections() {
        for (NetworkConnection networkConnection : experiment.networkConnections) {
            if (networkConnection.specificAddress == null) {
                networkConnection.connect(this);
                return;
            }
        }
        connectBluetoothDevices(false, false);
    }

    public void networkScanDialogDismissed() {
        connectNetworkConnections();
    }

    public void dataPolicyInfoDismissed() {
        connectNetworkConnections();
    }

    // connects to the bluetooth devices in an async task
    // if startMeasurement is true the measurement will be started automatically once all devices are connected
    public void connectBluetoothDevices(boolean startMeasurement, final boolean timed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (!(experiment.bluetoothInputs.isEmpty() && experiment.bluetoothOutputs.isEmpty())) {
                // connect all bluetooth devices with an asyncTask
                final Bluetooth.ConnectBluetoothTask btTask = new Bluetooth.ConnectBluetoothTask();
                btTask.progress = ProgressDialog.show(Experiment.this, getResources().getString(R.string.loadingTitle), getResources().getString(R.string.loadingBluetoothConnectionText), true);

                // define onSuccess
                if (startMeasurement) {
                    btTask.onSuccess = new Runnable () {
                      @Override
                        public void run () {
                          if (timed) {
                              startTimedMeasurement();
                          } else {
                              startMeasurement();
                          }
                      }
                    };
                }

                // set attributes of errorDialog
                Bluetooth.errorDialog.context = Experiment.this;
                Bluetooth.errorDialog.cancel = new Runnable () {
                    @Override
                    public void run () {
                        btTask.progress.dismiss();
                    }
                };
                Bluetooth.errorDialog.tryAgain = new Runnable() {
                    @Override
                    public void run() {
                        // start a new task with the same attributes
                        Bluetooth.ConnectBluetoothTask newBtTask = new Bluetooth.ConnectBluetoothTask();
                        newBtTask.progress = btTask.progress;
                        newBtTask.onSuccess = btTask.onSuccess;
                        // show ProgressDialog again
                        if (btTask.progress != null) {
                            btTask.progress.show();
                        }
                        newBtTask.execute(experiment.bluetoothInputs, experiment.bluetoothOutputs);
                    }
                };
                btTask.execute(experiment.bluetoothInputs, experiment.bluetoothOutputs);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showHint(int textRessource, PopupWindow.OnDismissListener dismissListener, final int gravity, final int fromRight) {
        if (popupWindow != null)
            return;
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View hintView = inflater.inflate(R.layout.menu_hint, null);
        TextView text = (TextView)hintView.findViewById(R.id.hint_text);
        text.setText(textRessource);
        ImageView iv = ((ImageView) hintView.findViewById(R.id.hint_arrow));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)iv.getLayoutParams();
        lp.gravity = gravity;
        iv.setLayoutParams(lp);

        popupWindow = new PopupWindow(hintView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if(Build.VERSION.SDK_INT >= 21){
            popupWindow.setElevation(4.0f);
        }

        popupWindow.setOutsideTouchable(false);
        popupWindow.setTouchable(false);
        popupWindow.setFocusable(false);
        LinearLayout ll = (LinearLayout) hintView.findViewById(R.id.hint_root);

        ll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (popupWindow != null)
                    popupWindow.dismiss();
                return true;
            }
        });

        popupWindow.setOnDismissListener(dismissListener);


        findViewById(R.id.rootLayout).post(new Runnable() {
            public void run() {
                View viewItem = findViewById(R.id.customActionBar);
                if (viewItem == null) {
                    return;
                }
                int pos[] = new int[2];
                viewItem.getLocationOnScreen(pos);
                if(isFinishing())
                    return;
                try {
                    popupWindow.showAtLocation(viewItem, Gravity.TOP | gravity, pos[0] + fromRight * viewItem.getHeight(), pos[1] + (int) (viewItem.getHeight() * 0.8));
                } catch (WindowManager.BadTokenException e) {
                    Log.e("showHint", "Bad token when showing hint. This is not unusual when app is rotating while showing the hint.");
                }
            }
        });
    }

    @Override
    public void onUserInteraction() {
        if (popupWindow != null)
            popupWindow.dismiss();
    }

    private void showMenuHint() {
        showHint(R.string.experimentinfo_hint, new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                popupWindow = null;
                menuHintDismissed = true;
                SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                int menuHintDismissCount= settings.getInt("menuHintDismissCount", 0);
                settings.edit().putInt("menuHintDismissCount", menuHintDismissCount+1).apply();
            }
        }, Gravity.RIGHT, 0);
    }

    private void showStartHint() {
        showHint(R.string.start_hint, new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                popupWindow = null;
                startHintDismissed = true;
            }
        }, Gravity.RIGHT, 2 );
    }

    @Override
    //Create options menu from our layout
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_experiment, menu);
        return true;
    }

    //Create an animation to guide the inexperienced user to the start button.
    private void showPlayHintAnimation () {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        hintAnimation = (ImageView) inflater.inflate(R.layout.play_animated, null);

        Animation anim = AnimationUtils.loadAnimation(this, R.anim.play_highlight);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        if (!BuildConfig.FLAVOR.equals("screenshot")) {
            hintAnimation.startAnimation(anim); //Do not animate while taking screenshots
        }

        hintAnimation.setContentDescription(res.getString(R.string.start));

        startMenuItem.setActionView(hintAnimation);
    }

    //Hide the start button hint animation
    private void hidePlayHintAnimation () {
        if (hintAnimation != null) {
            hintAnimation.clearAnimation();
            hintAnimation.setVisibility(View.GONE);
            hintAnimation = null;
            startMenuItem.setActionView(null);
            startMenuItem = null;
        }
    }

    @Override
    //Refresh the options menu
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //Do we have a valid experiment?
        if (experiment == null || !experiment.loaded) {
            for (int i = 0; i < menu.size(); i++)
                menu.getItem(i).setVisible(false);
            return true; //Even though there are no menu elements, we need to enable the menu to allow up navigation through the back button
        }
        //Get all the menu items we want to manipulate
        MenuItem timed_play = menu.findItem(R.id.action_timed_play);
        MenuItem timed_pause = menu.findItem(R.id.action_timed_pause);
        MenuItem play = menu.findItem(R.id.action_play);
        MenuItem pause = menu.findItem(R.id.action_pause);
        MenuItem timer = menu.findItem(R.id.timer);
        MenuItem timed_run = menu.findItem(R.id.action_timedRun);
        MenuItem remote = menu.findItem(R.id.action_remoteServer);
        MenuItem saveLocally = menu.findItem(R.id.action_saveLocally);
        MenuItem calibratedMagnetometer = menu.findItem(R.id.action_calibrated_magnetometer);
        MenuItem forceGNSSItem = menu.findItem(R.id.action_force_gnss);

        Iterator it = experiment.highlightedLinks.entrySet().iterator();
        for (int i = 1; i <= 5; i++) {
            MenuItem link;
            switch (i) {
                case 1: link = menu.findItem(R.id.action_link1);
                    break;
                case 2: link = menu.findItem(R.id.action_link2);
                    break;
                case 3: link = menu.findItem(R.id.action_link3);
                    break;
                case 4: link = menu.findItem(R.id.action_link4);
                    break;
                case 5: link = menu.findItem(R.id.action_link5);
                    break;
                default: link = menu.findItem(R.id.action_link5);
                    break;
            }
            if (it.hasNext()) {
                link.setVisible(true);
                Map.Entry entry = (Map.Entry)it.next();
                link.setTitle((String)entry.getKey());
            } else
                link.setVisible(false);
        }

        //If a timed run timer is active, we show either timed_play or timed_pause. otherwise we show play or pause
        //The pause version is shown when we are measuring, the play version otherwise
        timed_play.setVisible(!measuring && cdTimer != null);
        timed_pause.setVisible(measuring && cdTimer != null);
        play.setVisible(!measuring && cdTimer == null);
        pause.setVisible(measuring && cdTimer == null);

        //If the experiment has not yet been started, highlight the play button
        if (beforeStart) {
            //Create an animation to guide the inexperienced user.
            if (startMenuItem != null) {
                //We have already created an animation, which we need to remove first.
                hidePlayHintAnimation();
            }
            startMenuItem = menu.findItem(R.id.action_play);
            showPlayHintAnimation();
            startMenuItem.getActionView().setOnClickListener(this);
        } else { //Either we cannot show the anymation or we should not show it as the start button has already been used. Hide the animation
            startMenuItem = menu.findItem(R.id.action_play);
            hidePlayHintAnimation();
        }

        //the timer is shown if the timed run mode is active at all. In this case the timed run option is also checked
        timer.setVisible(timedRun);
        timed_run.setChecked(timedRun);

        //The save locally option (copy to collection) is only available for experiments that are not already in the collection
        saveLocally.setVisible(!experiment.isLocal);

        //The remote server option is checked if activated
        remote.setChecked(serverEnabled);

        //The calibrated magnetometer entry is only shown if the experiment uses a magnetometer and if the API level is high enough to offer an uncalibrated alternative
        boolean magnetometer = false;
        boolean calibrated = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) != null) {
            for (SensorInput sensor : experiment.inputSensors) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
                    magnetometer = true;
                    calibrated = sensor.calibrated;
                }
            }
        }
        calibratedMagnetometer.setVisible(magnetometer);
        calibratedMagnetometer.setChecked(calibrated);

        boolean gps = false;
        boolean forceGNSS = false;
        if (experiment.gpsIn != null) {
            gps = true;
            forceGNSS = experiment.gpsIn.forceGNSS;
        }
        forceGNSSItem.setVisible(gps);
        forceGNSSItem.setChecked(forceGNSS);

        //If the timedRun is active, we have to set the value of the countdown
        if (timedRun) {
            if (cdTimer != null) { //Timer running? Show the last known value of millisUntilFinished
                timer.setTitle(String.format(Locale.US, "%.1f", millisUntilFinished / 1000.0));
            } else { //No timer running? Show the start value of the next timer, which is...
                if (measuring) //...the stop delay if we are already measuring
                    timer.setTitle(String.format(Locale.US, "%.1f", timedRunStopDelay));
                else //...the start delay if we are paused
                    timer.setTitle(String.format(Locale.US, "%.1f", timedRunStartDelay));
            }
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if (popupWindow != null)
            popupWindow.dismiss();
        if (v == hintAnimation) {
            if (timedRun) {
                startTimedMeasurement();
            } else {
                SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                int startHintDismissCount = settings.getInt("startHintDismissCount", 0);
                settings.edit().putInt("startHintDismissCount", startHintDismissCount + 1).apply();
                startMeasurement();
            }
        }
    }

    //Recursively get all TextureViews, used for screenshots
    public Vector<PlotAreaView> getAllPlotAreaViews(View v) {
        Vector<PlotAreaView> l = new Vector<>();
        if (v.getVisibility() != View.VISIBLE)
            return l;
        if (v instanceof PlotAreaView) {
            l.add((PlotAreaView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                l.addAll(getAllPlotAreaViews(vg.getChildAt(i)));
            }
        }
        return l;
    }

    public Vector<InteractiveGraphView> getAllInteractiveGraphViews(View v) {
        Vector<InteractiveGraphView> l = new Vector<>();
        if (v.getVisibility() != View.VISIBLE)
            return l;
        if (v instanceof InteractiveGraphView) {
            l.add((InteractiveGraphView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                l.addAll(getAllInteractiveGraphViews(vg.getChildAt(i)));
            }
        }
        return l;
    }

    @Override
    //the user has clicked an option.
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //Home-button. Back to the Experiment List
        if (id == android.R.id.home) {
            leaveExperiment(this);
            return true;
        }

        //Play button. Start a measurement
        if (id == R.id.action_play) {
            SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
            int startHintDismissCount= settings.getInt("startHintDismissCount", 0);
            settings.edit().putInt("startHintDismissCount", startHintDismissCount+1).apply();

            if (timedRun) {
                startTimedMeasurement();
            } else
                startMeasurement();
            return true;
        }

        //Pause button. Stop the measurement
        if (id == R.id.action_pause) {
            stopMeasurement();
            return true;
        }

        //Timed play button. Abort the start count-down (by stopping the measurement)
        if (id == R.id.action_timed_play) {
            stopMeasurement();
            return true;
        }

        //Timed stop button. Stop the running measurement
        if (id == R.id.action_timed_pause) {
            stopMeasurement();
            return true;
        }

        //Timed Run button. Show the dialog to set up the timed run
        if (id == R.id.action_timedRun) {
            final MenuItem itemRef = item;
            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
            View vLayout = inflater.inflate(R.layout.timed_run_layout, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            final CheckBox cbTimedRunEnabled = (CheckBox) vLayout.findViewById(R.id.timedRunEnabled);
            final TableLayout tTimedRunTimeOptions = (TableLayout) vLayout.findViewById(R.id.timedRunTimeOptions);
            final RelativeLayout tTimedRunBeeperAllRow = (RelativeLayout) vLayout.findViewById(R.id.timedRunBeepAllRow);
            final TableLayout tTimedRunBeeperOptions = (TableLayout) vLayout.findViewById(R.id.timedRunBeepOptions);
            cbTimedRunEnabled.setChecked(timedRun);

            final CompoundButton.OnCheckedChangeListener enabledChanged = new CompoundButton.OnCheckedChangeListener() {
                  @Override
                  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                      tTimedRunTimeOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                      tTimedRunBeeperAllRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                      tTimedRunBeeperOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                  }

            };

            cbTimedRunEnabled.setOnCheckedChangeListener(enabledChanged);
            enabledChanged.onCheckedChanged(cbTimedRunEnabled, timedRun);

            final EditText etTimedRunStartDelay = (EditText) vLayout.findViewById(R.id.timedRunStartDelay);
            etTimedRunStartDelay.setText(String.valueOf(timedRunStartDelay));
            final EditText etTimedRunStopDelay = (EditText) vLayout.findViewById(R.id.timedRunStopDelay);
            etTimedRunStopDelay.setText(String.valueOf(timedRunStopDelay));

            final class IgnoreChanges {
                boolean ignore = true;
            }
            final IgnoreChanges ignoreChanges = new IgnoreChanges();
            final Button cbTimedRunBeeperAll = (Button) vLayout.findViewById(R.id.timedRunBeepAll);
            final class AllButtonOn {
                boolean on = false;
            }
            final AllButtonOn allButtonOn = new AllButtonOn();
            final SwitchCompat cbTimedRunBeeperCountdown = (SwitchCompat) vLayout.findViewById(R.id.timedRunBeepCountdown);
            final SwitchCompat cbTimedRunBeeperStart = (SwitchCompat) vLayout.findViewById(R.id.timedRunBeepStart);
            final SwitchCompat cbTimedRunBeeperRunning = (SwitchCompat) vLayout.findViewById(R.id.timedRunBeepRunning);
            final SwitchCompat cbTimedRunBeeperStop = (SwitchCompat) vLayout.findViewById(R.id.timedRunBeepStop);

            final View.OnClickListener allButtonClicked;

            final CompoundButton.OnCheckedChangeListener updateAllButton = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (ignoreChanges.ignore)
                        return;

                    allButtonOn.on = (cbTimedRunBeeperCountdown.isChecked() || cbTimedRunBeeperStart.isChecked() || cbTimedRunBeeperRunning.isChecked() || cbTimedRunBeeperStop.isChecked());
                    cbTimedRunBeeperAll.setText(allButtonOn.on ? R.string.deactivate_all : R.string.activate_all);
                }
            };

            allButtonClicked = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ignoreChanges.ignore = true;

                    allButtonOn.on = !allButtonOn.on;
                    cbTimedRunBeeperAll.setText(allButtonOn.on ? R.string.deactivate_all : R.string.activate_all);
                    cbTimedRunBeeperCountdown.setChecked(allButtonOn.on);
                    cbTimedRunBeeperStart.setChecked(allButtonOn.on);
                    cbTimedRunBeeperRunning.setChecked(allButtonOn.on);
                    cbTimedRunBeeperStop.setChecked(allButtonOn.on);

                    ignoreChanges.ignore = false;
                }
            };

            cbTimedRunBeeperAll.setOnClickListener(allButtonClicked);
            cbTimedRunBeeperCountdown.setOnCheckedChangeListener(updateAllButton);
            cbTimedRunBeeperStart.setOnCheckedChangeListener(updateAllButton);
            cbTimedRunBeeperRunning.setOnCheckedChangeListener(updateAllButton);
            cbTimedRunBeeperStop.setOnCheckedChangeListener(updateAllButton);

            cbTimedRunBeeperCountdown.setChecked(timedRunBeepCountdown);
            cbTimedRunBeeperStart.setChecked(timedRunBeepStart);
            cbTimedRunBeeperRunning.setChecked(timedRunBeepRunning);
            cbTimedRunBeeperStop.setChecked(timedRunBeepStop);
            ignoreChanges.ignore = false;
            updateAllButton.onCheckedChanged(cbTimedRunBeeperStop, timedRunBeepStop);

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

                            timedRunBeepCountdown = cbTimedRunBeeperCountdown.isChecked();
                            timedRunBeepStart = cbTimedRunBeeperStart.isChecked();
                            timedRunBeepRunning = cbTimedRunBeeperRunning.isChecked();
                            timedRunBeepStop = cbTimedRunBeeperStop.isChecked();

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

        //Clear data button. Clear the data :)
        if (id == R.id.action_clear) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(res.getString(R.string.clear_data_question))
                    .setPositiveButton(R.string.clear_data, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            clearData();
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

        //Export button. Call the export function of the DataExport class
        if (id == R.id.action_export) {
            if (experiment.exporter.exportSets.size() == 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(res.getString(R.string.export_empty))
                        .setTitle(R.string.export)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                experiment.export(this);
            }
            return true;
        }

        //Saving the state - either locally or through a share intent
        if (id == R.id.action_saveState) {
            stopMeasurement();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final View dialogView = this.getLayoutInflater().inflate(R.layout.savestate_dialog, null);
            builder.setView(dialogView);
            final EditText customTitleET = (EditText) dialogView.findViewById(R.id.customTitle);
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            final Date now = Calendar.getInstance().getTime();
            customTitleET.setText(getString(R.string.save_state_default_title) + " " + df.format(now));
            builder.setMessage(res.getString(R.string.save_state_message))
                    .setTitle(R.string.save_state)
                    .setPositiveButton(R.string.save_state_save, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            try {
                                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"; //Random file name
                                FileOutputStream output = openFileOutput(file, Activity.MODE_PRIVATE);
                                String result = experiment.writeStateFile(customTitleET.getText().toString(), output);
                                output.close();
                                if (result != null) {
                                    Toast.makeText(getBaseContext(), "Error: " + result, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            } catch (Exception e) {
                                Toast.makeText(getBaseContext(), "Error wirting state file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e("updateData", "Unhandled exception.", e);
                                return;
                            }
                            Toast.makeText(getBaseContext(), getString(R.string.save_state_success), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNeutralButton(R.string.save_state_share, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            final String fileName = experiment.title.replaceAll("[^0-9a-zA-Z \\-_]", "");
                            String filename = fileName.isEmpty() ? getString(R.string.save_state_default_title) : fileName + " " + (new SimpleDateFormat("yyyy-MM-dd HH-mm-ss")).format(now)+".phyphox";
                            File file = new File(getCacheDir(), "/"+filename);
                            try {
                                FileOutputStream output = new FileOutputStream(file);
                                String result = experiment.writeStateFile(customTitleET.getText().toString(), output);
                                output.close();
                                if (result != null) {
                                    Toast.makeText(getBaseContext(), "Error: " + result, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            } catch (Exception e) {
                                Toast.makeText(getBaseContext(), "Error wirting state file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e("updateData", "Unhandled exception.", e);
                                return;
                            }

                            final Uri uri = FileProvider.getUriForFile(getBaseContext(), getPackageName() + ".exportProvider", file);
                            final Intent intent = ShareCompat.IntentBuilder.from(Experiment.this)
                                    .setType("application/octet-stream") //mime type from the export filter
                                    .setSubject(getString(R.string.save_state_subject))
                                    .setStream(uri)
                                    .getIntent()
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, 0);
                            for (ResolveInfo ri : resInfoList) {
                                grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }


                            //Create chooser
                            Intent chooser = Intent.createChooser(intent, getString(R.string.share_pick_share));
                            //And finally grant permissions again for any activities created by the chooser
                            resInfoList = getPackageManager().queryIntentActivities(chooser, 0);
                            for (ResolveInfo ri : resInfoList) {
                                if (ri.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID
                                ))
                                    continue;
                                grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }
                            //Execute this intent
                            startActivity(chooser);
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

        //The share button. Take a screenshot and send a share intent to all those social media apps...
        if (id == R.id.action_share) {
            View screenView = findViewById(R.id.rootLayout).getRootView();

            screenView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
            screenView.setDrawingCacheEnabled(false);

            Canvas canvas = new Canvas(bitmap);

            Vector<PlotAreaView> pavList = getAllPlotAreaViews(screenView);
            for (PlotAreaView pav : pavList) {
                pav.setDrawingCacheEnabled(true);
                Bitmap bmp = pav.getBitmap();
                pav.setDrawingCacheEnabled(false);

                int location[] = new int[2];
                pav.getLocationOnScreen(location);
                canvas.drawBitmap(bmp, location[0], location[1], null);
            }

            Vector<InteractiveGraphView> gvList = getAllInteractiveGraphViews(screenView);
            for (InteractiveGraphView gv : gvList) {
                gv.setDrawingCacheEnabled(true);
                Bitmap bmp = Bitmap.createBitmap(gv.getDrawingCache());
                gv.setDrawingCacheEnabled(false);

                int location[] = new int[2];
                gv.getLocationOnScreen(location);
                canvas.drawBitmap(bmp, location[0], location[1], null);

                if (gv.popupWindowInfo != null) {
                    View popupView = gv.popupWindowInfo.getContentView();
                    popupView.setDrawingCacheEnabled(true);
                    bmp = Bitmap.createBitmap(popupView.getDrawingCache());
                    popupView.setDrawingCacheEnabled(false);

                    popupView.getLocationOnScreen(location);
                    canvas.drawBitmap(bmp, location[0], location[1], null);
                }


            }

            final String fileName = experiment.title.replaceAll("[^0-9a-zA-Z \\-_]", "");
            File file = new File(this.getCacheDir(), "/"+ (fileName.isEmpty() ? "phyphox" : fileName) + " " + (new SimpleDateFormat("yyyy-MM-dd HH-mm-ss")).format(new Date())+".png");
            try {
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
                bitmap.recycle();

                final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".exportProvider", file);

                final Intent intent = ShareCompat.IntentBuilder.from(this)
                        .setType("image/png")
                        .setSubject(getString(R.string.share_subject))
                        .setStream(uri)
                        .getIntent()
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, 0);
                for (ResolveInfo ri : resInfoList) {
                    grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                //Create chooser
                Intent chooser = Intent.createChooser(intent, getString(R.string.share_pick_share));
                //And finally grant permissions again for any activities created by the chooser
                resInfoList = getPackageManager().queryIntentActivities(chooser, 0);
                for (ResolveInfo ri : resInfoList) {
                    if (ri.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID
                    ))
                        continue;
                    grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                //Execute this intent
                startActivity(chooser);
            } catch (Exception e) {
                Log.e("action_share", "Unhandled exception", e);
            }
        }

        if (id == R.id.action_calibrated_magnetometer) {
            stopMeasurement();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                for (SensorInput sensor : experiment.inputSensors) {
                    if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
                        sensor.calibrated = !item.isChecked();
                    }
                }
            }
        }

        if (id == R.id.action_force_gnss) {
            stopMeasurement();
            if (experiment.gpsIn != null) {
                experiment.gpsIn.forceGNSS= !item.isChecked();
            }
        }

        //The remote server button. Show a warning with IP information and start the server if confirmed.
        //or: stop the server if it was active before.
        if (id == R.id.action_remoteServer) {
            if (item.isChecked()) {
                item.setChecked(false);
                serverEnabled = false;
                stopRemoteServer();
            } else {
                final MenuItem itemRef = item;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(res.getString(R.string.remoteServerWarning))
                        .setTitle(R.string.remoteServerWarningTitle)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                itemRef.setChecked(true);
                                serverEnabled = true;
                                startRemoteServer();
                            }
                        })
                        .setNeutralButton(R.string.hotspotSettings, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                                final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                                intent.setComponent(cn);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity( intent);
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

        //Desciption-button. Show the experiment description
        if (id == R.id.action_description) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(experiment.title);

            LinearLayout ll = new LinearLayout(builder.getContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            int marginX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_horizontal_padding), res.getDisplayMetrics());
            int marginY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_vertical_padding), res.getDisplayMetrics());
            ll.setPadding(marginX, marginY, marginX, marginY);

            if (!experiment.stateTitle.isEmpty()) {
                TextView stateLabel = new TextView(builder.getContext());
                stateLabel.setText(experiment.stateTitle);
                stateLabel.setTextColor(res.getColor(R.color.main));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0,0,0,Math.round(res.getDimension(R.dimen.font)));
                stateLabel.setLayoutParams(lp);
                ll.addView(stateLabel);
            }

            TextView description = new TextView(builder.getContext());
            description.setText(experiment.description);
            description.setTextColor(res.getColor(R.color.main2));

            ll.addView(description);

            for (String label : experiment.links.keySet()) {
                Button btn = new Button(builder.getContext());
                btn.setText(label);
                final String url = experiment.links.get(label);
                btn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        Uri uri = Uri.parse(url);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        }
                    }
                });
                btn.setBackgroundResource(R.drawable.background_ripple2);
                ll.addView(btn);
            }

            ScrollView sv = new ScrollView(builder.getContext());
            sv.setHorizontalScrollBarEnabled(false);
            sv.setVerticalScrollBarEnabled(true);
            sv.addView(ll);

            builder.setView(sv);

            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        }

        int highlightLink = -1;
        switch (id) {
            case R.id.action_link1: highlightLink = 0;
                break;
            case R.id.action_link2: highlightLink = 1;
                break;
            case R.id.action_link3: highlightLink = 2;
                break;
            case R.id.action_link4: highlightLink = 3;
                break;
            case R.id.action_link5: highlightLink = 4;
                break;
            default: highlightLink = -1;
                break;
        }
        if (highlightLink >= 0) {
            Map.Entry entry = (Map.Entry)experiment.highlightedLinks.entrySet().toArray()[highlightLink];
            Uri uri = Uri.parse((String)entry.getValue());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        }


        //Save locally button (copy to collection). Instantiate and start the copying thread.
        if (id == R.id.action_saveLocally) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new PhyphoxFile.CopyXMLTask(intent, this).execute();
        }

        return super.onOptionsItemSelected(item);
    }

    //Below follow two runnable, which act as our "main loop" (although this feels like a function,
    //   technically these are instances of the Runnable class with our function being the
    //   overridden version of the run() function. But it feels better to write the whole thing down
    //   like a function...

    //The updateData runnable runs on a second thread and performs the heavy math (if defined in the
    // experiment)
    Runnable updateData = new Runnable() {
        @Override
        public void run() {
            //Do the analysis. All of these elements might fire exceptions,
            // especially on badly defined experiments. So let's be save and catch anything that
            // gets through to here.
            while (measuring && !shutdown) {
                if (experiment != null) { //This only makes sense if there is an experiment
                    try {
                        //time for some analysis?
                        if (measuring) {
                            analysisInProgress = true;
                            experiment.processAnalysis(true); //Do the math.
                            analysisInProgress = false;
                        }
                    } catch (Exception e) {
                        Log.e("updateData", "Unhandled exception.", e);
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (Exception e) {
                    Log.w("updateData", "Sleep interrupted");
                }
            }
        }
    };

    //The updateViews runnable does everything UI related and hence runs on the UI thread
    Runnable updateViews = new Runnable() {
        @Override
        public void run() {
            updateViewsHandler.removeCallbacksAndMessages(null);

            //Show progressbar if analysis is running (set by second thread)
            if (analysisInProgress) {
                analysisProgressAlpha += 0.05;
                if (analysisProgressAlpha > 1.f)
                    analysisProgressAlpha = 1.f;
            } else {
                analysisProgressAlpha -= 0.05;
                if (analysisProgressAlpha < 0.f)
                    analysisProgressAlpha = 0.f;
            }
            if (analysisProgressAlpha > 0.1) {
                if (analysisProgressAlpha >= 0.9)
                    analysisProgress.setAlpha(1.f);
                else
                    analysisProgress.setAlpha(analysisProgressAlpha);
                analysisProgress.setVisibility(View.VISIBLE);
            } else {
                analysisProgress.setVisibility(View.INVISIBLE);
            }

            //If a defocus has been requested (on another thread), do so.
            if (shouldDefocus) {
                defocus();
                shouldDefocus = false;
            }

            //If a state change has been requested (on another thread, i.e. remote server), do so
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

            if (experiment != null) {
                try {
                    //Get values from input views only if there isn't fresh data from the remote server which might get overridden
                    if (!remoteInput) {
                        experiment.handleInputViews(measuring);
                    }
                    //Update all the views currently visible
                    if (experiment.updateViews(tabLayout.getSelectedTabPosition(), false)) {
                        if (remoteInput) {
                            //If there has been remote input, we may reset it as updateViews will have taken care of this
                            //This also means, that there is new input from the user
                            remoteInput = false;
                            experiment.newUserInput = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e("updateViews", "Unhandled exception.", e);
                } finally {
                    //If we are not supposed to stop, let's do it again in a short while
                    if (!shutdown) {
                        if (measuring)
                            updateViewsHandler.postDelayed(this, 40);
                        else
                            updateViewsHandler.postDelayed(this, 400); //As there is no experiment running, we can take our time and maybe save some battery
                    }
                }
            }
        }
    };


    //Start a measurement
    public void startMeasurement() {
        //Disable play-button highlight
        beforeStart = false;

        //Start the sensors
        try {
            experiment.startAllIO();
        } catch (Bluetooth.BluetoothException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stopMeasurement(); // stop experiment
                // show an error dialog
                Bluetooth.errorDialog.message = e.getMessage();
                Bluetooth.errorDialog.context = Experiment.this;
                // try to connect the bluetooth devices again when the user clicks "try again"
                Bluetooth.errorDialog.tryAgain = new Runnable() {
                  @Override
                     public void run() {
                      connectBluetoothDevices(true, false);
                  }
                 };
                 Bluetooth.errorDialog.run();
                 return;
	    }
        }

        //Set measurement state
        measuring = true;

        //No more turning off during the measurement
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Start the analysis "loop"
        Thread t = new Thread(updateData);
        t.start();

        //If this is a timed run, we have to start the countdown which stops it again later.
        if (timedRun) {

            millisUntilFinished = Math.round(timedRunStopDelay * 1000);
            cdTimer = new CountDownTimer(millisUntilFinished, 20) {

                int nextBeep = -1;
                boolean relative = false;

                public void onTick(long muf) {
                    //On each tick update the menu to show the remaining time
                    if (timedRunBeepRunning || timedRunBeepStop) {
                        if (nextBeep < 0)
                            nextBeep = (int)(Math.floor(muf/1000. - 0.6));
                        if (muf/1000. < nextBeep + 0.4) {
                            if (nextBeep == 0 && timedRunBeepStop) {
                                if (relative)
                                    audioOutput.beepRelative(800, 0.5, 1.0);
                                else {
                                    audioOutput.beep(800, 0.5, muf / 1000. - nextBeep);
                                    relative = true;
                                }
                            } else if (nextBeep > 0 && timedRunBeepRunning) {
                                if (relative)
                                    audioOutput.beepRelative(1000, 0.1, 1.0);
                                else {
                                    audioOutput.beep(1000, 0.1, muf / 1000. - nextBeep);
                                    relative = true;
                                }
                            }
                            nextBeep--;
                        }
                    }
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

    //Start a timed measurement
    public void startTimedMeasurement() {
        //No more turning off during the measurement
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // check if all Bluetooth devices are connected and display an errorDialog if not
            Bluetooth notConnectedDevice = null;
            for (Bluetooth b : experiment.bluetoothInputs) {
                if (!b.isConnected()) {
                    notConnectedDevice = b;
                    break;
                }
            }
            if (notConnectedDevice == null) {
                for (Bluetooth b : experiment.bluetoothOutputs) {
                    if (!b.isConnected()) {
                        notConnectedDevice = b;
                        break;
                    }
                }
            }
            if (notConnectedDevice != null) {
                // show an error dialog
                Bluetooth.errorDialog.message = getResources().getString(R.string.bt_exception_no_connection)+Bluetooth.BluetoothException.getMessage(notConnectedDevice);
                Bluetooth.errorDialog.context = Experiment.this;
                // try to connect the bluetooth devices again when the user clicks "try again"
                Bluetooth.errorDialog.tryAgain = new Runnable() {
                    @Override
                    public void run() {
                        connectBluetoothDevices(true, true);
                    }
                };
                Bluetooth.errorDialog.run();
                return;
            }
        }

        if (timedRunBeepCountdown || timedRunBeepStart || timedRunBeepRunning || timedRunBeepStop) {
            if (experiment.audioOutput == null) {
                if (audioOutput == null) {
                    audioOutput = new AudioOutput(false, 48000, true);
                    try {
                        audioOutput.init();
                    } catch (Exception e) {
                        return;
                    }
                }
            } else
                audioOutput = experiment.audioOutput;
            audioOutput.start(true);
            audioOutput.play();
        }

        //Not much more to do here. Just set up a countdown that will start the measurement
        millisUntilFinished = Math.round(timedRunStartDelay*1000);
        cdTimer = new CountDownTimer(millisUntilFinished, 20) {
            int nextBeep = -1;
            boolean relative = false;

            public void onTick(long muf) {
                //On each tick update the menu to show the remaining time
                if (timedRunBeepCountdown || timedRunBeepStart) {
                    if (nextBeep < 0)
                        nextBeep = (int)(Math.floor(muf/1000. - 0.5));
                    if (muf/1000. < nextBeep + 0.4) {
                        if (nextBeep == 0 && timedRunBeepStart) {
                            if (relative)
                                audioOutput.beepRelative(1000, 0.5, 1.0);
                            else {
                                audioOutput.beep(1000, 0.5, muf / 1000. - nextBeep);
                                relative = true;
                            }
                        } else if (nextBeep > 0 && timedRunBeepCountdown) {
                            if (relative)
                                audioOutput.beepRelative(800, 0.1, 1.0);
                            else {
                                audioOutput.beep(800, 0.1, muf / 1000. - nextBeep);
                                relative = true;
                            }
                        }
                        nextBeep--;
                    }
                }
                millisUntilFinished = muf;
                invalidateOptionsMenu();
            }

            public void onFinish() {
                startMeasurement();
            }
        }.start();
        invalidateOptionsMenu();
    }

    //Stop the measurement
    public void stopMeasurement() {
        measuring = false; //Set the state
        analysisProgressAlpha = 0.f; //Disable the progress bar

        //Lift the restrictions, so the screen may turn off again and the user may rotate the device (unless remote server is active)
        if (!serverEnabled)
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Deactivate any timer
        if (cdTimer != null) {
            cdTimer.cancel();
            cdTimer = null;
            millisUntilFinished = 0;
        }

        //Stop any inputs (Sensors, microphone) or outputs (speaker) that might still be running
        //be careful as stopMeasurement might be called without a valid experiment
        if (experiment != null) {
            experiment.stopAllIO();
        }

        //refresh the options menu
        invalidateOptionsMenu();
    }

    public void clearData() {
        //Clear the buffers

        stopMeasurement();

        experiment.dataLock.lock(); //Synced, do not allow another thread to meddle here...
        try {
            for (DataBuffer buffer : experiment.dataBuffers)
                buffer.clear(true);
        } finally {
            experiment.dataLock.unlock();
        }
        experiment.experimentTimeReference.reset();
        experiment.newData = true;
        experiment.newUserInput = true;
        if (remote != null && serverEnabled)
            remote.forceFullUpdate = true;
    }

    //Start the remote server (see remoteServer class)
    private void startRemoteServer() {
        TextView announcer = (TextView)findViewById(R.id.remoteInfo);

        if (remote != null || !serverEnabled) { //Check if it is actually activated. If not, just stop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                announcer.animate().translationY(announcer.getMeasuredHeight());
            else
                announcer.setVisibility(View.INVISIBLE);
            return;
        }

        //Instantiate and start the server
        if (sessionID.isEmpty()) {
            remote = new RemoteServer(experiment, this);
            sessionID = remote.sessionID;
        } else
            remote = new RemoteServer(experiment, this, sessionID);
        remote.start();

        //Announce this to the user as there are security concerns.
        final String addressList = RemoteServer.getAddresses(getBaseContext()).replaceAll("\\s+$", "");
        if (addressList.isEmpty())
            announcer.setText(res.getString(R.string.remoteServerNoNetwork));
        else
            announcer.setText(res.getString(R.string.remoteServerActive, addressList));
        announcer.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            announcer.animate().translationY(0).alpha(1.0f);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.BELOW, R.id.tab_layout);
        lp.addRule(RelativeLayout.ABOVE, R.id.remoteInfo);
        ((ViewPager)findViewById(R.id.view_pager)).setLayoutParams(lp);

        //Also we want to keep the device active for remote access
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    //Stop the remote server (see remoteServer class)
    private void stopRemoteServer() {
        if (!measuring)
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Announce this to the user, so he knows why the webinterface stopped working.
        TextView announcer = (TextView)findViewById(R.id.remoteInfo);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            announcer.animate().translationY(announcer.getMeasuredHeight()).alpha(0.0f);
        else
            announcer.setVisibility(View.INVISIBLE);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.addRule(RelativeLayout.BELOW, R.id.tab_layout);
        ((ViewPager)findViewById(R.id.view_pager)).setLayoutParams(lp);

        if (remote == null) //no server there? never mind.
            return;

        //Stop it!
        remote.stopServer();

        //Wait for the second thread and remove the instance
        try {
            remote.join();
        } catch (Exception e) {
            Log.e("stopRemoteServer", "Exception on join.", e);
        }
        remote = null;

    }

    //Called by remote server to stop the measurement from other thread
    public void remoteStopMeasurement() {
        remoteIntentMeasuring = false;
        updateState = true;
    }

    //Called by remote server to start the measurement from other thread
    public void remoteStartMeasurement() {
        remoteIntentMeasuring = true;
        updateState = true;
    }

    //Called by remote server request a defocus from other thread
    public void requestDefocus() {
        shouldDefocus = true;
    }

    //Defocus helper function. Moves the focus to the experiment view linear layout to remove the
    //   focus from input view text fields. (The focus would prevent an update of this view)
    public void defocus() {
        findViewById(R.id.experimentView).requestFocus();
    }

    @Override
    //store our data if the activity gets destroyed or recreated (device rotation!)
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!loadCompleted) { //the experiment is not even ready yet?
            //If we have an old state (activity closed before completely loaded), we may save this again.
            if (savedInstanceState != null)
                outState.putAll(savedInstanceState);
            return;
        }
        try {
            outState.putInt(STATE_CURRENT_VIEW, tabLayout.getSelectedTabPosition()); //Save current experiment view
            experiment.dataLock.lock(); //Save dataBuffers (synchronized, so no other thread alters them while we access them)
            try {
                App app = (App) getApplicationContext();
                app.experiment = experiment;
                //outState.putSerializable(STATE_EXPERIMENT, (Serializable)experiment);
            } finally {
                experiment.dataLock.unlock();
            }
            outState.putBoolean(STATE_REMOTE_SERVER, serverEnabled); //remote server status
            outState.putString(STATE_REMOTE_SESSION_ID, sessionID); //remote server status
            outState.putBoolean(STATE_BEFORE_START, beforeStart); //Has the experiment ever been started
            outState.putBoolean(STATE_TIMED_RUN, timedRun); //timed run status
            outState.putDouble(STATE_TIMED_RUN_START_DELAY, timedRunStartDelay); //timed run start delay
            outState.putDouble(STATE_TIMED_RUN_STOP_DELAY, timedRunStopDelay); //timed run stop delay
            outState.putBoolean(STATE_TIMED_RUN_BEEP_COUNTDOWN, timedRunBeepCountdown);
            outState.putBoolean(STATE_TIMED_RUN_BEEP_START, timedRunBeepStart);
            outState.putBoolean(STATE_TIMED_RUN_BEEP_RUNNING, timedRunBeepRunning);
            outState.putBoolean(STATE_TIMED_RUN_BEEP_STOP, timedRunBeepStop);
            outState.putBoolean(STATE_MENU_HINT_DISMISSED, menuHintDismissed);
            outState.putBoolean(STATE_START_HINT_DISMISSED, startHintDismissed);
            outState.putBoolean(STATE_SAVE_LOCALLY_DISMISSED, saveLocallyDismissed);
        } catch (Exception e) {
            //Something went wrong?
            //Discard all the data to get a clean new activity and start fresh.
            e.printStackTrace();
            outState.clear();
        }
    }


}
