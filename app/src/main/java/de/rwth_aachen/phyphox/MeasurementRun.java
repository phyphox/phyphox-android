package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;

//One experiment's measurement apart from the screen that shows it: the measuring state, the analysis thread,
//the countdowns of a timed run with their beeps, and the foreground service that keeps it alive in the
//background (see MeasurementService). A run with the service outlives its Experiment activity: when Android
//removes the activity (Recents trimming, a swiped-away task), the run goes on and the next Experiment activity
//for it attaches (from the notification). A configuration change hands any run to the recreated activity. At
//most one run is held like this at a time (current). Everything except stop() runs on the main thread.
public class MeasurementRun {

    //What the Experiment activity showing the run hears, on the main thread
    interface Client {
        void onRunStarted(); //Measuring began, directly or at the end of a countdown
        void onRunStopped(boolean seen); //Measuring or a countdown ended; seen: while the client was on screen
        void onRunCountdownChanged(); //The shown remaining time of a countdown changed (only while on screen)
        void onRunStartFailed(Exception e); //A countdown ended but an input refused to start
        void onRunTakenOver(); //Another Experiment activity shows the run from now on
    }

    //The settings of a timed run, fixed when it starts
    static final class Timing {
        final long startMillis, stopMillis;
        final boolean beepCountdown, beepStart, beepRunning, beepStop;

        Timing(double startDelay, double stopDelay, boolean beepCountdown, boolean beepStart, boolean beepRunning, boolean beepStop) {
            this.startMillis = Math.round(startDelay * 1000);
            this.stopMillis = Math.round(stopDelay * 1000);
            this.beepCountdown = beepCountdown;
            this.beepStart = beepStart;
            this.beepRunning = beepRunning;
            this.beepStop = beepStop;
        }

        boolean beeps() {
            return beepCountdown || beepStart || beepRunning || beepStop;
        }
    }

    //The run that may be without a screen: counting down or measuring, or ended unseen and kept by the service
    private static MeasurementRun current = null;

    final PhyphoxExperiment experiment;
    final Intent source; //The intent that opened the experiment, for the notification to reopen it
    Bundle screenState = null; //The last saved state of the screen showing the run, for the next one to continue from

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Client client = null;
    private boolean clientVisible = false;

    private volatile boolean active = false; //Counting down or measuring
    volatile boolean measuring = false;
    volatile long millisUntilFinished = 0; //Remaining time of the running countdown
    private volatile Object analysisPass = null; //Identifies the analysis thread of the latest start
    private CountDownTimer timer = null;
    private Timing timing = null;
    private AudioOutput beeper = null;

    private boolean background = false; //The service keeps the run alive (requested and not refused)
    private boolean finishedUnseen = false; //Ended while nobody watched; the service keeps the data until somebody does

    MeasurementRun(Context context, PhyphoxExperiment experiment, Intent source) {
        this.context = context.getApplicationContext();
        this.experiment = experiment;
        this.source = source;
    }

    static MeasurementRun current() {
        return current;
    }

    //Camera and depth experiments cannot go on without their screen: the cameras close with it
    static boolean usesCamera(PhyphoxExperiment experiment) {
        return experiment.cameraInput != null || experiment.depthInput != null;
    }

    boolean isActive() {
        return active;
    }

    boolean isClient(Client c) {
        return client == c;
    }

    boolean inBackground() {
        return background;
    }

    boolean isFinishedUnseen() {
        return finishedUnseen;
    }

    //A countdown of a timed run is running (main thread)
    boolean timerRunning() {
        return timer != null;
    }

    //Starts measuring now. An input that refuses to start ends the run and is thrown for the caller to explain.
    void start(boolean background, Timing timing) throws Bluetooth.BluetoothException, DepthInput.DepthInputException {
        if (active)
            return;
        if (timing != null && timing.beeps())
            prepareBeeper(); //Without it the run is silent
        this.timing = timing;
        begin(background);
        beginMeasuring();
    }

    //Starts a timed run's countdown, at the end of which measuring begins. False if the beeper cannot play.
    boolean startCountdown(boolean background, Timing timing) {
        if (active)
            return true;
        if (timing.beeps() && !prepareBeeper())
            return false;
        this.timing = timing;
        begin(background);
        startTimer(timing.startMillis, true);
        return true;
    }

    private void begin(boolean background) {
        if (current != null && current != this)
            current.release(); //The screen asked the user first
        if (timer != null) { //Left by a stop from another thread that has not reached the main thread yet
            timer.cancel();
            timer = null;
        }
        current = this;
        active = true;
        finishedUnseen = false;
        if (background && !this.background)
            this.background = MeasurementService.start(context, this);
        MeasurementService.update(this);
    }

    private void beginMeasuring() throws Bluetooth.BluetoothException, DepthInput.DepthInputException {
        try {
            experiment.startAllIO();
        } catch (Bluetooth.BluetoothException | DepthInput.DepthInputException e) {
            stop();
            throw e;
        }
        measuring = true;
        final Object pass = new Object();
        analysisPass = pass;
        new Thread(() -> analyse(pass)).start();
        if (client != null)
            client.onRunStarted();
        MeasurementService.update(this);
        if (timing != null)
            startTimer(timing.stopMillis, false); //Last: a stop delay of zero ends the run right here
    }

    //The analysis "loop" of one start, on its own thread
    private void analyse(Object pass) {
        while (measuring && analysisPass == pass) {
            //Badly defined experiments may throw anything here
            try {
                if (measuring)
                    experiment.processAnalysis(true); //Do the math.
            } catch (Exception e) {
                Log.e("updateData", "Unhandled exception.", e);
            }
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                Log.w("updateData", "Sleep interrupted");
            }
        }
    }

    //The countdown before a timed run (countdown) or until its end, with the beeps chosen for it
    private void startTimer(final long millis, final boolean countdown) {
        if (timer != null)
            timer.cancel();
        millisUntilFinished = millis;
        CountDownTimer t = new CountDownTimer(millis, 20) {
            int nextBeep = -1;
            boolean relative = false;
            long shownTenth = millis / 100;

            @Override
            public void onTick(long muf) {
                beep(muf);
                millisUntilFinished = muf;
                //The menu shows tenths of a second: refreshing it more often only costs
                if (muf / 100 != shownTenth) {
                    shownTenth = muf / 100;
                    if (client != null && clientVisible)
                        client.onRunCountdownChanged();
                }
            }

            //Every second and at the end: of the countdown (start) or of the measurement (stop)
            private void beep(long muf) {
                boolean everySecond = countdown ? timing.beepCountdown : timing.beepRunning;
                boolean atTheEnd = countdown ? timing.beepStart : timing.beepStop;
                if (beeper == null || !(everySecond || atTheEnd))
                    return;
                if (nextBeep < 0)
                    nextBeep = (int) (Math.floor(muf / 1000. - (countdown ? 0.5 : 0.6)));
                if (muf / 1000. < nextBeep + 0.4) {
                    if (nextBeep == 0 && atTheEnd)
                        play(countdown ? 1000 : 800, 0.5, muf);
                    else if (nextBeep > 0 && everySecond)
                        play(countdown ? 800 : 1000, 0.1, muf);
                    nextBeep--;
                }
            }

            private void play(double frequency, double duration, long muf) {
                if (relative)
                    beeper.beepRelative(frequency, duration, 1.0);
                else {
                    beeper.beep(frequency, duration, muf / 1000. - nextBeep);
                    relative = true;
                }
            }

            @Override
            public void onFinish() {
                if (timer == this)
                    timer = null;
                millisUntilFinished = 0;
                if (!countdown) {
                    stop();
                    return;
                }
                try {
                    beginMeasuring();
                } catch (Bluetooth.BluetoothException | DepthInput.DepthInputException e) {
                    if (client != null)
                        client.onRunStartFailed(e);
                }
            }
        };
        timer = t;
        t.start();
    }

    //The timed run's beeps sound through the experiment's own audio output if it has one
    private boolean prepareBeeper() {
        if (experiment.audioOutput != null)
            beeper = experiment.audioOutput;
        else if (beeper == null) {
            AudioOutput output = new AudioOutput(false, 48000, true);
            try {
                output.init();
            } catch (Exception e) {
                return false;
            }
            beeper = output;
        }
        beeper.start(true);
        beeper.play();
        return true;
    }

    //Ends measuring or a countdown. Any thread, as the remote server clears data from its own: the inputs stop
    //at once, the rest follows on the main thread. Also called when nothing runs (leaving the screen does).
    void stop() {
        boolean wasActive = active;
        active = false;
        measuring = false;
        experiment.stopAllIO();
        if (!wasActive)
            return;
        if (Looper.myLooper() == Looper.getMainLooper())
            afterStop();
        else
            main.post(this::afterStop);
    }

    private void afterStop() {
        if (active)
            return; //Started again in the meantime
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        millisUntilFinished = 0;
        boolean seen = client != null && clientVisible;
        if (background) {
            if (seen)
                endService();
            else {
                finishedUnseen = true;
                MeasurementService.update(this);
            }
        }
        if (current == this && !finishedUnseen)
            current = null;
        if (client != null)
            client.onRunStopped(seen);
        else
            experiment.closeConnections(); //What leaving the screen would have closed
    }

    private void endService() {
        background = false;
        finishedUnseen = false;
        MeasurementService.end(context, this);
    }

    //Lets go of the run: stops it and ends its service. Another run takes over, or the notification of an ended
    //run was closed; the data stays with a screen that still shows it.
    void release() {
        stop();
        if (background)
            endService();
        finishedUnseen = false;
        if (current == this)
            current = null;
    }

    //An Experiment activity shows the run from now on; the one before, if still there, lets go of it
    void attach(Client newClient, boolean visible) {
        Client old = client;
        client = newClient;
        clientVisible = false;
        if (old != null && old != newClient)
            old.onRunTakenOver();
        setClientVisible(newClient, visible);
    }

    void setClientVisible(Client c, boolean visible) {
        if (client != c)
            return;
        clientVisible = visible;
        if (visible && finishedUnseen) {
            //Somebody looks at the ended run now, so the service may let the process go
            endService();
            if (current == this)
                current = null;
        }
    }

    //The activity is gone. A run with the service waits for the next one. A run without it only lives through a
    //recreation, where the new activity attaches before the posted check.
    void detach(Client c) {
        if (client != c)
            return;
        client = null;
        clientVisible = false;
        if (active && !background)
            main.post(() -> {
                if (client == null && active && !background)
                    stop();
            });
    }

    //Android refused or ended the service: the run can only go on while it is seen
    void onServiceFailed() {
        background = false;
        if (finishedUnseen) {
            finishedUnseen = false;
            if (current == this)
                current = null;
        }
        if (active && (client == null || !clientVisible))
            stop();
    }
}
