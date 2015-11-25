package de.rwth_aachen.phyphox;

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
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
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
import java.util.Vector;

//TODO Translation of Experiment-Texts
//TODO Sonar needs fine-tuning

// Experiments are performed in this activity, which reacts to various intents.
// The intent has to provide a *.phyphox file which defines the experiment
public class Experiment extends AppCompatActivity {

    //String constants to identify values saved in onSaveInstanceState
    private static final String STATE_CURRENT_VIEW = "current_view"; //Which experiment view is selected?
    private static final String STATE_DATA_BUFFERS = "data_buffers"; //The current data buffers
    private static final String STATE_REMOTE_SERVER = "remote_server"; //Is the remote server activated?
    private static final String STATE_TIMED_RUN = "timed_run"; //Are timed runs activated?
    private static final String STATE_TIMED_RUN_START_DELAY = "timed_run_start_delay"; //The start delay for a timed run
    private static final String STATE_TIMED_RUN_STOP_DELAY = "timed_run_stop_delay"; //The stop delay for a timed run

    //This handler creates the "main loop" as it is repeatedly called using postDelayed
    //Not a real loop to keep some resources available
    final Handler updateViewsHandler = new Handler();

    //Status variables
    boolean measuring = false; //Measurement running?
    boolean loadCompleted = false; //Set to true when an experiment has been loaded successfully
    boolean shutdown = false; //The activity should be stopped. Used to escape the measurement loop.

    //Remote server
    private remoteServer remote = null; //The remote server (see remoteServer class)
    private boolean serverEnabled = false; //Is the remote server activated?
    boolean remoteIntentMeasuring = false; //Is the remote interface expecting that the measurement is running?
    boolean updateState = false; //This is set to true when a state changed is initialized remotely. The measurement state will then be set to remoteIntentMeasuring.
    public boolean remoteInput = false; //Has there been an data input (inputViews for now) from the remote server that should be processed?
    public boolean shouldDefocus = false; //Should the current view loose focus? (Neccessary to remotely edit an input view, which has focus on this device)

    //Timed run status
    boolean timedRun = false; //Timed run enabled?
    double timedRunStartDelay = 0.; //Start delay for timed runs
    double timedRunStopDelay = 0.; //Stop delay for timed runs
    CountDownTimer cdTimer = null; //This holds the timer used for timed runs. If it is not null, a timed run is running and at the end of the countdown the measurement state will change
    long millisUntilFinished = 0; //This variable is used to cache the remaining countdown, so it is available outside the onTick-callback of the timer

    //The experiment
    phyphoxExperiment experiment; //The experiment (definition and functionality) after it has been loaded.
    private int currentView; //An experiment may define multiple view layouts. This is the index of the currently used one.
    long analysisStart = 0; //This variable holds the system time of the moment, an analysis (math modules) process started. This is necessary for experiments, which do analysis after given intervals

    //Others...
    private Resources res; //Helper to easily access resources
    public SensorManager sensorManager; //The sensor manager
    Intent intent; //Another helper to easily access the data of the intent that triggered this activity
    ProgressDialog progress; //Holds a progress dialog when a file is being loaded
    Bundle savedInstanceState = null; //Holds the saved instance state, so it can be handled outside onCreate

    @Override
    //Where it all begins...
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intent = getIntent(); //Store the intent for easy access
        res = getResources(); //The same for resources
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...

        this.savedInstanceState = savedInstanceState; //Store savedInstanceState so it can be accessed after loading the experiment in a second thread
        setContentView(R.layout.activity_experiment); //Setup the views...

        //We want to get the back-button in the actionbar (even on old Android versions)
        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);

        //Start loading the experiment in a second thread (mostly for network loading, but it won't hurt in any case...)
        //So display a ProgressDialog and instantiate and execute loadXMLAsyncTask (see phyphoxFile class)
        progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
        new phyphoxFile.loadXMLAsyncTask(intent, this).execute();
    }

    @Override
    //onPause event
    public void onPause() {
        super.onPause();

        stopRemoteServer(); //Remote server should stop when the app is not active
        shutdown = true; //Stop the loop
        stopMeasurement(); //Stop the measurement
        overridePendingTransition(R.anim.hold, R.anim.exit_experiment); //Make a nice animation...
    }

    @Override
    //Let's start again
    public void onResume() {
        super.onResume();

        shutdown = false; //Deactivate shutdown variable
        updateViewsHandler.postDelayed(updateViews, 40); //Start the "main loop" again
        startRemoteServer();  //Restart the remote server (if it is activated)
        //We do not start the measurement again automatically. If the user switched away, this might
        //   be confusing otherwise.
    }

    @Override
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                this.recreate();
            else {
                //This should not occur, as this is a Marhmallow (> Honeycomb) feature, but this is
                //   would be the way to restart the activity on older versions where recreate() is
                //   not available
                finish();
                startActivity(intent);
            }
        }
    }

    //Switch to a certain view (of the experiment views)
    private void setupView(int newView) {
        //Clear the base linear layout
        LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
        ll.removeAllViews();

        //Create all viewElements in the new view
        for (expView.expViewElement element : experiment.experimentViews.elementAt(newView).elements) {
            element.createView(ll, this);
        }

        //Store the index of the current view
        currentView = newView;
    }

    //This is called from the CopyXML thread in onPostExecute, so the copying process of a remote
    //   experiment to the local collection has been completed
    public void onCopyXMLCompleted(String result) {
        progress.dismiss(); //Close progress display
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
        progress.dismiss(); //Close progress display
        this.experiment = experiment; //Store the loaded experiment
        if (experiment.loaded) { //Everything went fine, no errors
            //We should set the experiment title....
            ((TextView) findViewById(R.id.titleText)).setText(experiment.title);

            //Create the dropdown menu from which the user may select different views of this experiment
            List<String> viewChoices = new ArrayList<>(); //List of view names
            for (expView v : experiment.experimentViews) { //For each experiment view
                viewChoices.add(v.name); //Add the name to the list
            }
            //Now get the dropdown view ("Spinner" element)
            Spinner viewSelector = (Spinner) findViewById(R.id.viewSelector);
            //Fill the name list into an array adapter
            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, viewChoices);
            //Setup the array adapter to a simple default
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            //And feet the adapter to the spinne
            viewSelector.setAdapter(spinnerArrayAdapter);

            //Now for the functionality of the spinner: Create an onclick listener
            viewSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    //The user has selected a view, so let's set up this view.
                    setupView(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    //Nothing to do if nothing is expected...
                }

            });

            //If we have a savedInstanceState, it would be a good time to interpret it...
            if (savedInstanceState != null) {
                //Load the old dataBuffer data from the state
                Vector<dataBuffer> oldData = (Vector<dataBuffer>) savedInstanceState.getSerializable(STATE_DATA_BUFFERS);
                if (oldData != null) { //Did it work?
                    //Then reload the other states that the user can control
                    serverEnabled = savedInstanceState.getBoolean(STATE_REMOTE_SERVER, false); //Remote server activated?
                    timedRun = savedInstanceState.getBoolean(STATE_TIMED_RUN, false); //timed run activated?
                    timedRunStartDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_START_DELAY); //start elay of timed run
                    timedRunStopDelay = savedInstanceState.getDouble(STATE_TIMED_RUN_STOP_DELAY); //stop delay of timed run

                    //Let's place the old data in our new buffers
                    for (int i = 0; i < experiment.dataBuffers.size() && i < oldData.size(); i++) { //For each dataBuffer
                        if (oldData.get(i) == null) //Do we have old data?
                            continue; //Nope, next one
                        experiment.dataBuffers.get(i).clear(); //Clear the new buffer (probably not neccessary)
                        Iterator it = oldData.get(i).getIterator(); //Get an iterator for the old Data
                        while (it.hasNext()) //Append each item to the new buffer
                            experiment.dataBuffers.get(i).append((double) it.next());
                    }

                    //Which view was active when we were stopped?
                    currentView = savedInstanceState.getInt(STATE_CURRENT_VIEW); //Get currentView
                    viewSelector.setSelection(currentView); //Set spinner to the right entry
                }
            }

            //Now actually display the selected view
            setupView(viewSelector.getSelectedItemPosition());

            //Everything is ready. Let's start the "main loop"
            loadCompleted = true;
            updateViewsHandler.postDelayed(updateViews, 40);

            //Also invalidate the options menu, so it can activate any controls, that are valid for a loaded experiment
            invalidateOptionsMenu();

            //Start the remote server if activated
            startRemoteServer();

            //Finally create the "be careful, it's not our fault"-dialog
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
                    //Callback if user clicked "ok" (the only option)
                    //Here we have to handle the case that the user decided not to see this dialog again
                    Boolean skipStartInfo = false;
                    if (dontShowAgain.isChecked())
                        skipStartInfo = true;

                    //Store this preference as a sharedPreference
                    SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("skipStartInfo", skipStartInfo);
                    editor.apply();
                }
            });

            //Check the shared preferences if the user already decided not to see this warning...
            SharedPreferences settings = getSharedPreferences(ExperimentList.PREFS_NAME, 0);
            Boolean skipStartInfo = settings.getBoolean("skipStartInfo", false);
            if (!skipStartInfo) //If he did not opt out of the warning, show it to him
                adb.show();

        } else {
            //There has been an error. Show the error to the user and leave the activity in its
            //   non-interactive state...

            //Append TextView with error message to the base linear layout
            LinearLayout ll = (LinearLayout) findViewById(R.id.experimentView);
            TextView errorView = new TextView(this);
            errorView.setText(experiment.message);
            errorView.setGravity(Gravity.CENTER); //Center the message
            errorView.setTextColor(ContextCompat.getColor(this, R.color.main)); //Set main color

            ll.addView(errorView); //Add the TextView to the linear layout
        }
    }



    @Override
    //Create options menu from out layout
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_experiment, menu);
        return true;
    }

    @Override
    //Refresh the options menu
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Do we have a valid experiment?
        if (experiment == null || !experiment.loaded) {
            return false; //Nope. Then there is no interaction.
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
        super.onPrepareOptionsMenu(menu);
        return true;
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

        //Desciption-button. Show the experiment description
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

        //Save locally button (copy to collection). Instantiate and start the copying thread.
        if (id == R.id.action_saveLocally) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new phyphoxFile.CopyXMLTask(intent, this).execute();
        }

        return super.onOptionsItemSelected(item);
    }

    //The runnable, which is our "main loop" (although this feels like a function, technically it is
    //   an instance of the Runnable class with our function being the overridden version of the
    //   run() function. But it feels better to write the whole thing down like a function...
    Runnable updateViews = new Runnable() {
        @Override
        public void run() {
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

            //Do the analysis and update the views. All of these elements might fire exceptions,
            // especially on badly defined experiments. So let's be save and catch anything that
            // gets through to here.
            if (experiment != null) { //This only makes sense if there is an experiment
                try {
                    synchronized (experiment.dataBuffers) { //Syncronize!!! Make sure that no other thread manipulates the buffers we are working on
                        //Get values from input views only if there isn't fresh data from the remote server which might get overridden
                        if (!remoteInput) {
                            experiment.handleInputViews(currentView);
                        }

                        //time for some analysis?
                        if (measuring) {
                            if (experiment.analysisOnUserInput) {
                                //If set by the experiment, the analysis is only done when there is new input from the user
                                if (experiment.newUserInput) {
                                    experiment.processAnalysis(); //Do the math.
                                    experiment.newUserInput = false; //Reset user input tracking
                                }
                            } else if (System.currentTimeMillis() - analysisStart > experiment.analysisPeriod * 1000) {
                                //This is the default: The analysis is done periodically. Either as fast as possible or after a period defined by the experiment
                                experiment.processAnalysis(); //Do the math.
                                analysisStart = System.currentTimeMillis(); //Remember when we were done this time
                            }
                        }
                    }
                    if (remoteInput) {
                        //If there has been remote input, we may reset it not as from here the buffers will not be changed by us anymore
                        //This also means, that there is new input from the user
                        remoteInput = false;
                        experiment.newUserInput = true;
                    }
                    //Update all the views currently visible
                    experiment.updateViews(currentView);
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
        //Clear the buffers and start the sensors. Also remember the start time as t0
        synchronized (experiment.dataBuffers) { //Synced, do not allow another thread to meddle here...
            for (dataBuffer buffer : experiment.dataBuffers)
                buffer.clear();
            experiment.startAllIO();
        }

        //Set measurement state
        measuring = true;

        //Lock the screen and keep it on. No more screen rotation or turning off during the measurement
        lockScreen();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

        //Lift the restrictions, so the screen may turn off again and the user may rotate the device
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

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

    //Start the remote server (see remoteServer class)
    private void startRemoteServer() {
        if (remote != null || !serverEnabled) //Check if it is actually activated. If not, just stop
            return;
        //Instantiate and start the server
        remote = new remoteServer(experiment, this);
        remote.start();

        //Announce this to the user as there are security concerns.
        Toast.makeText(this, "Remote access server started.", Toast.LENGTH_LONG).show();
    }

    //Stop the remote server (see remoteServer class)
    private void stopRemoteServer() {
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

        //Announce this to the user, so he knows why the webinterface stopped working.
        Toast.makeText(this, "Remote access server stopped.", Toast.LENGTH_LONG).show();
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
        if (!loadCompleted) //the experiment is not even ready yet?
            return; //Then we have nothing worth saving...
        try {
            outState.putInt(STATE_CURRENT_VIEW, currentView); //Save current experiment view
            synchronized (experiment.dataBuffers) { //Save dataBuffers (synchronized, so no other thread alters them while we access them)
                outState.putSerializable(STATE_DATA_BUFFERS, experiment.dataBuffers);
            }
            outState.putBoolean(STATE_REMOTE_SERVER, serverEnabled); //remote server status
            outState.putBoolean(STATE_TIMED_RUN, timedRun); //timed run status
            outState.putDouble(STATE_TIMED_RUN_START_DELAY, timedRunStartDelay); //timed run start delay
            outState.putDouble(STATE_TIMED_RUN_STOP_DELAY, timedRunStopDelay); //timed run stop delay
        } catch (Exception e) {
            //Something went wrong?
            //Discard all the data to get a clean new activity and start fresh.
            outState.clear();
        }
    }


}
