package de.rwth_aachen.phyphox;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

// Experiments are performed in this activity, which reacts to various intents.
// The intent has to provide a *.phyphox file which defines the experiment
public class Experiment extends AppCompatActivity implements View.OnClickListener {

    //String constants to identify values saved in onSaveInstanceState
    private static final String STATE_CURRENT_VIEW = "current_view"; //Which experiment view is selected?
    private static final String STATE_REMOTE_SERVER = "remote_server"; //Is the remote server activated?
    private static final String STATE_REMOTE_SESSION_ID = "remote_session_id"; //The session ID of the remote server
    private static final String STATE_BEFORE_START = "before_start"; //Has the experiment been started?
    private static final String STATE_TIMED_RUN = "timed_run"; //Are timed runs activated?
    private static final String STATE_TIMED_RUN_START_DELAY = "timed_run_start_delay"; //The start delay for a timed run
    private static final String STATE_TIMED_RUN_STOP_DELAY = "timed_run_stop_delay"; //The stop delay for a timed run
    private static final String STATE_EXPERIMENT = "experiment"; //The actual experiment
    private static final String STATE_HINT_DISMISSED = "hint_dismissed";
    private static final String STATE_SAVE_LOCALLY_DISMISSED = "save_locally_dismissed";

    //This handler creates the "main loop" as it is repeatedly called using postDelayed
    //Not a real loop to keep some resources available
    final Handler updateViewsHandler = new Handler();

    //Status variables
    boolean measuring = false; //Measurement running?
    boolean loadCompleted = false; //Set to true when an experiment has been loaded successfully
    boolean shutdown = false; //The activity should be stopped. Used to escape the measurement loop.
    boolean beforeStart = true; //Experiment has not yet been started even once
    boolean hintDismissed = false; //Remember that the user has clicked away the hint to the menu
    boolean saveLocallyDismissed = false; //Remember that the user did not want to save this experiment locally

    //Remote server
    private remoteServer remote = null; //The remote server (see remoteServer class)
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
    CountDownTimer cdTimer = null; //This holds the timer used for timed runs. If it is not null, a timed run is running and at the end of the countdown the measurement state will change
    long millisUntilFinished = 0; //This variable is used to cache the remaining countdown, so it is available outside the onTick-callback of the timer

    //The experiment
    phyphoxExperiment experiment; //The experiment (definition and functionality) after it has been loaded.
    TabLayout tabLayout;
    ViewPager pager;
    expViewPagerAdapter adapter;

    //Others...
    private Resources res; //Helper to easily access resources
    public SensorManager sensorManager; //The sensor manager
    Intent intent; //Another helper to easily access the data of the intent that triggered this activity
    ProgressDialog progress; //Holds a progress dialog when a file is being loaded
    Bundle savedInstanceState = null; //Holds the saved instance state, so it can be handled outside onCreate
    MenuItem hint = null; //Reference to play-hint button
    ImageView hintAnimation = null; //Reference to the animated part of the play-hint button

    //The analysis progress bar
    ProgressBar analysisProgress;       //Reference to the progress bar view
    boolean analysisInProgress = false; //Set to true by second thread while analysis is running
    float analysisProgressAlpha = 0.f;  //Will be increased while analysis is running and decreased while idle. This smoothes the display and results in an everage transparency representing the average load.

    PopupWindow popupWindow = null;

    @Override
    //Where it all begins...
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lockScreen(); //We do not want the activity to reload because of a screen rotation until the experiment has been loaded

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

        if (savedInstanceState != null)
            experiment = (phyphoxExperiment) savedInstanceState.getSerializable(STATE_EXPERIMENT);
        if (experiment != null) {
            //We saved our experiment. Lets just retrieve it and continue
            onExperimentLoaded(experiment);
        } else {
            //Start loading the experiment in a second thread (mostly for network loading, but it won't hurt in any case...)
            //So display a ProgressDialog and instantiate and execute loadXMLAsyncTask (see phyphoxFile class)
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            (new phyphoxFile.loadXMLAsyncTask(intent, this)).execute();
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
            //Close all bluetooth connections, when the activity is recreated, they will be reestablished in the phyphoxFile class
            for (bluetoothInput bti : experiment.bluetoothInputs)
                bti.closeConnection();
            for (bluetoothOutput bti : experiment.bluetoothOutputs)
                bti.closeConnection();
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
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
    public void onExperimentLoaded(phyphoxExperiment experiment) {
        try {
            progress.dismiss(); //Close progress display
        } catch (Exception e) {
            //This should only fail if the window has already been destroyed. Ignore.
        } finally {
            progress = null;
        }
        this.experiment = experiment; //Store the loaded experiment
        if (experiment.loaded) { //Everything went fine, no errors
            //We should set the experiment title....
            ((TextView) findViewById(R.id.titleText)).setText(experiment.title);

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
                hintDismissed = savedInstanceState.getBoolean(STATE_HINT_DISMISSED);
                saveLocallyDismissed = savedInstanceState.getBoolean(STATE_SAVE_LOCALLY_DISMISSED);

                //Which view was active when we were stopped?
                startView = savedInstanceState.getInt(STATE_CURRENT_VIEW);
            }

            tabLayout = ((TabLayout)findViewById(R.id.tab_layout));
            pager = ((ViewPager)findViewById(R.id.view_pager));
            FragmentManager manager = getSupportFragmentManager();
            adapter = new expViewPagerAdapter(manager, this.experiment);
            pager.setAdapter(adapter);
            tabLayout.setupWithViewPager(pager);
            pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

            for (int i = 0; i < adapter.getCount(); i++) {
                expViewFragment f = (expViewFragment)getSupportFragmentManager().findFragmentByTag("android:switcher:" + pager.getId() + ":" + adapter.getItemId(i));
                if (f != null)
                    f.recreateView();
            }

            if (adapter.getCount() < 2)
                tabLayout.setVisibility(View.GONE);

            try {
                experiment.init(sensorManager);
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

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); //We are ready. Now the user may rotate.

        //If this experiment has been loaded from a external source, we offer to save it locally
        if (!experiment.isLocal) {
            hintDismissed = true; //Do not show menu hint for external experiments

            if (!saveLocallyDismissed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(res.getString(R.string.save_locally_message))
                        .setTitle(R.string.save_locally)
                        .setPositiveButton(R.string.save_locally_button, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                progress = ProgressDialog.show(Experiment.this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
                                new phyphoxFile.CopyXMLTask(intent, Experiment.this).execute();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                saveLocallyDismissed = true;
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }

        //An explanation is not necessary for raw sensors
        if (experiment.category.equals(res.getString(R.string.categoryRawSensor)))
            hintDismissed = true;

        if (!hintDismissed)
            showMenuHint();

    }


    private void showMenuHint() {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View hintView = inflater.inflate(R.layout.menu_hint, null);

        popupWindow = new PopupWindow(hintView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if(Build.VERSION.SDK_INT >= 21){
            popupWindow.setElevation(4.0f);
        }

        popupWindow.setOutsideTouchable(true);
        popupWindow.setTouchable(true);
        popupWindow.setFocusable(true);
        LinearLayout ll = (LinearLayout) hintView.findViewById(R.id.hint_root);

        ll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (popupWindow != null)
                    popupWindow.dismiss();
                return true;
            }
        });

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                hintDismissed = true;
                popupWindow = null;
            }
        });


        findViewById(R.id.rootLayout).post(new Runnable() {
            public void run() {
                Toolbar actionBar = (Toolbar) findViewById(R.id.customActionBar);
                if (actionBar == null)
                    return;
                int pos[] = new int[2];
                actionBar.getLocationOnScreen(pos);
                popupWindow.showAtLocation(actionBar, Gravity.TOP | Gravity.RIGHT, 0, pos[1] + (int)(actionBar.getHeight()*0.8));
            }
        });
    }

    @Override
    //Create options menu from out layout
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
        hintAnimation.startAnimation(anim);

        hint.setActionView(hintAnimation);
    }

    //Hide the start button hint animation
    private void hidePlayHintAnimation () {
        if (hintAnimation != null) {
            hintAnimation.clearAnimation();
            hintAnimation.setVisibility(View.GONE);
            hintAnimation = null;
            hint.setActionView(null);
            hint = null;
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

        //If a timed run timer is active, we show either timed_play or timed_pause. otherwise we show play or pause
        //The pause version is shown when we are measuring, the play version otherwise
        timed_play.setVisible(!measuring && cdTimer != null);
        timed_pause.setVisible(measuring && cdTimer != null);
        play.setVisible(!measuring && cdTimer == null);
        pause.setVisible(measuring && cdTimer == null);

        //If the experiment has not yet been started, highlight the play button
        if (beforeStart) {
            //Create an animation to guide the inexperienced user.
            if (hint != null) {
                //We have already created an animation, which we need to remove first.
                hidePlayHintAnimation();
            }
            hint = menu.findItem(R.id.action_play);
            showPlayHintAnimation();
            hint.getActionView().setOnClickListener(this);
        } else { //Either we cannot show the anymation or we should not show it as the start button has already been used. Hide the animation
            hint = menu.findItem(R.id.action_play);
            hidePlayHintAnimation();
        }

        //the timer is shown if the timed run mode is active at all. In this case the timed run option is also checked
        timer.setVisible(timedRun);
        timed_run.setChecked(timedRun);

        //The save locally option (copy to collection) is only available for experiments that are not already in the collection
        saveLocally.setVisible(!experiment.isLocal);

        //The remote server option is checked if activated
        remote.setChecked(serverEnabled);

        //If the timedRun is active, we have to set the value of the countdown
        if (timedRun) {
            if (cdTimer != null) { //Timer running? Show the last known value of millisUntilFinished
                timer.setTitle(String.valueOf(millisUntilFinished / 1000 + 1) + "s");
            } else { //No timer running? Show the start value of the next timer, which is...
                if (measuring) //...the stop delay if we are already measuring
                    timer.setTitle(String.valueOf(Math.round(timedRunStopDelay))+"s");
                else //...the start delay if we are paused
                    timer.setTitle(String.valueOf(Math.round(timedRunStartDelay))+"s");
            }
        }
        return true;
    }

    public void onClick(View v) {
        if (v == hintAnimation) {
            if (timedRun) {
                startTimedMeasurement();
            } else
                startMeasurement();
        }
    }

    //Recursively get all TextureViews, used for screenshots
    public Vector<PlotAreaView> getAllPlotAreaViews(View v) {
        Vector<PlotAreaView> l = new Vector<>();
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

    public Vector<graphView> getAllGraphViews(View v) {
        Vector<graphView> l = new Vector<>();
        if (v instanceof graphView) {
            l.add((graphView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                l.addAll(getAllGraphViews(vg.getChildAt(i)));
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

        //Play button. Start a measurement
        if (id == R.id.action_play) {
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

        //Clear data button. Clear the data :)
        if (id == R.id.action_clear) {
            clearData();
            return true;
        }

        //Export button. Call the export function of the dataExport class
        if (id == R.id.action_export) {
            experiment.export(this);
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

            Vector<graphView> gvList = getAllGraphViews(screenView);
            for (graphView gv : gvList) {
                gv.setDrawingCacheEnabled(true);
                Bitmap bmp = Bitmap.createBitmap(gv.getDrawingCache());
                gv.setDrawingCacheEnabled(false);

                int location[] = new int[2];
                gv.getLocationOnScreen(location);
                canvas.drawBitmap(bmp, location[0], location[1], null);
            }

            File file = new File(this.getCacheDir(), "/phyphox " + (new SimpleDateFormat("yyyy-MM-dd HH-mm-ss")).format(new Date())+".png");
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

                startActivity(Intent.createChooser(intent, getString(R.string.share_pick_share)));
            } catch (Exception e) {
                Log.e("action_share", "Unhandled exception", e);
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
            builder.setTitle(R.string.show_description);

            LinearLayout ll = new LinearLayout(builder.getContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            int marginX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_horizontal_padding), res.getDisplayMetrics());
            int marginY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.activity_vertical_padding), res.getDisplayMetrics());
            ll.setPadding(marginX, marginY, marginX, marginY);

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
                btn.setBackgroundResource(R.drawable.background_ripple);
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

        //Save locally button (copy to collection). Instantiate and start the copying thread.
        if (id == R.id.action_saveLocally) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new phyphoxFile.CopyXMLTask(intent, this).execute();
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
                            experiment.processAnalysis(); //Do the math.
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
                        experiment.handleInputViews(tabLayout.getSelectedTabPosition());
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

    //This function prevents the screen from rotating. it finds out the current orientation and
    // requests exactly this orientation. Just a "no-sensor"-mode is insufficient as it might revert
    // to the default orientation, but the user shall be able to rotate the screen if the experiment
    // is paused. After that it should stay the way it is...
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

    //Start a measurement
    public void startMeasurement() {
        //Disable play-button highlight
        beforeStart = false;

        //Start the sensors
        experiment.startAllIO();

        //Set measurement state
        measuring = true;

        //Lock the screen and keep it on. No more screen rotation or turning off during the measurement
        lockScreen();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Start the analysis "loop"
        Thread t = new Thread(updateData);
        t.start();

        //If this is a timed run, we have to start the countdown which stops it again later.
        if (timedRun) {
            millisUntilFinished = Math.round(timedRunStopDelay * 1000);
            cdTimer = new CountDownTimer(millisUntilFinished, 100) {

                public void onTick(long muf) {
                    //On each tick update the menu to show the remaining time
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
        //Lock the screen and keep it on. No more screen rotation or turning off during the measurement
        lockScreen();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Not much more to do here. Just set up a countdown that will start the measurement
        millisUntilFinished = Math.round(timedRunStartDelay*1000);
        cdTimer = new CountDownTimer(millisUntilFinished, 100) {

            public void onTick(long muf) {
                //On each tick update the menu to show the remaining time
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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

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
            for (dataBuffer buffer : experiment.dataBuffers)
                buffer.clear();
            for (expView views : experiment.experimentViews)
                for (expView.expViewElement view : views.elements)
                    view.clear();

        } finally {
            experiment.dataLock.unlock();
        }
        experiment.newData = true;
        if (remote != null && serverEnabled)
            remote.forceFullUpdate = true;
        experiment.firstAnalysisTime = 0;
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
            remote = new remoteServer(experiment, this);
            sessionID = remote.sessionID;
        } else
            remote = new remoteServer(experiment, this, sessionID);
        remote.start();

        //Announce this to the user as there are security concerns.
        announcer.setText(res.getString(R.string.remoteServerActive, remoteServer.getAddresses().replaceAll("\\s+$", "")));
        announcer.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            announcer.animate().translationY(0);

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
            announcer.animate().translationY(announcer.getMeasuredHeight());
        else
            announcer.setVisibility(View.INVISIBLE);

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
                outState.putSerializable(STATE_EXPERIMENT, (Serializable)experiment);
            } finally {
                experiment.dataLock.unlock();
            }
            outState.putBoolean(STATE_REMOTE_SERVER, serverEnabled); //remote server status
            outState.putString(STATE_REMOTE_SESSION_ID, sessionID); //remote server status
            outState.putBoolean(STATE_BEFORE_START, beforeStart); //Has the experiment ever been started
            outState.putBoolean(STATE_TIMED_RUN, timedRun); //timed run status
            outState.putDouble(STATE_TIMED_RUN_START_DELAY, timedRunStartDelay); //timed run start delay
            outState.putDouble(STATE_TIMED_RUN_STOP_DELAY, timedRunStopDelay); //timed run stop delay
            outState.putBoolean(STATE_HINT_DISMISSED, hintDismissed);
            outState.putBoolean(STATE_SAVE_LOCALLY_DISMISSED, saveLocallyDismissed);
        } catch (Exception e) {
            //Something went wrong?
            //Discard all the data to get a clean new activity and start fresh.
            e.printStackTrace();
            outState.clear();
        }
    }


}
