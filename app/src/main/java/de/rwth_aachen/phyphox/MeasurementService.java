package de.rwth_aachen.phyphox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

//Keeps a measurement alive while no experiment screen is visible. Android delivers sensor, location and
//microphone data to an app in the background only through a foreground service: a Service pinned by a
//persistent notification that declares the kinds of data it uses. The service does no measuring itself, the
//MeasurementRun does. The service adds the notification and a partial wake lock, because a foreground service
//alone does not keep the CPU awake once the screen turns off. A run that ends while nobody watches keeps the
//service, with a notification saying so, so Android does not reclaim the process and its unsaved data before
//the user returns.
public class MeasurementService extends Service {
    private static final String TAG = "MeasurementService";
    private static final String CHANNEL_ID = "measurement";
    static final int NOTIFICATION_ID = 1;
    private static final String EXTRA_TYPES = "types";
    private static final String ACTION_STOP = "de.rwth_aachen.phyphox.action.STOP_MEASUREMENT";
    private static final String ACTION_CLOSE = "de.rwth_aachen.phyphox.action.CLOSE_MEASUREMENT";
    private static final String ACTION_REPOST = "de.rwth_aachen.phyphox.action.REPOST_MEASUREMENT";

    //Main thread only. The run the service keeps alive, and how far its start has come: after
    //startForegroundService the service must call startForeground before it stops, or Android kills the app,
    //so an end that arrives first waits for the promotion.
    private static MeasurementRun run = null;
    private static boolean promoted = false;
    private static boolean endPending = false;
    private static MeasurementService instance = null;

    private PowerManager.WakeLock wakeLock = null;

    //The foreground service types the experiment needs. Without the matching type Android cuts a background
    //service off from location, the microphone or connected devices. The constants are plain ints, and
    //ServiceCompat drops the ones an older Android does not know.
    @SuppressLint("InlinedApi")
    static int typesFor(PhyphoxExperiment experiment) {
        int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE; //Plain sensors have no type of their own
        if (experiment.gpsIn != null)
            types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        if (experiment.audioRecord != null)
            types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        if (!experiment.bluetoothInputs.isEmpty() || !experiment.bluetoothOutputs.isEmpty())
            types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
        return types;
    }

    //Called for a run that starts or counts down while its screen is visible, which the location and microphone
    //types require. False if Android refuses: the run then lasts only while it is seen, as without the setting.
    static boolean start(Context context, MeasurementRun forRun) {
        Intent intent = new Intent(context, MeasurementService.class);
        intent.putExtra(EXTRA_TYPES, typesFor(forRun.experiment));
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            //Android 12+ refuses starts from the background
            Log.e(TAG, "Could not start the foreground service: " + e.getMessage());
            return false;
        }
        run = forRun;
        endPending = false;
        return true;
    }

    //Called by a run that no longer needs the service
    static void end(Context context, MeasurementRun forRun) {
        if (run != forRun)
            return; //Another run has taken the service over
        run = null;
        if (!promoted) {
            endPending = true; //onStartCommand stops right after its startForeground
            return;
        }
        promoted = false;
        context.stopService(new Intent(context, MeasurementService.class));
    }

    //The run changed between counting down, measuring and ended: the notification and the wake lock follow
    static void update(MeasurementRun forRun) {
        if (run == forRun && promoted && instance != null)
            instance.refresh();
    }

    //Back to the run: the Experiment activity showing it comes to the front (CLEAR_TOP with SINGLE_TOP delivers
    //onNewIntent instead of creating another), or a new one attaches to the run when its screen is gone
    static Intent returnIntent(Context context, MeasurementRun forRun) {
        Intent intent = new Intent(forRun.source);
        intent.setClass(context, Experiment.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(Experiment.EXTRA_RETURN_TO_RUN, true);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.backgroundMeasurementChannel), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null)
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phyphox:measurement");
    }

    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action != null) {
            //From the notification
            MeasurementRun current = run;
            if (current != null) {
                if (ACTION_STOP.equals(action))
                    current.stop();
                else if (ACTION_CLOSE.equals(action))
                    current.release();
                else if (ACTION_REPOST.equals(action) && promoted)
                    refresh(); //Android 14+ lets the user swipe it away, but a running measurement needs its stop button
            } else if (!endPending)
                stopSelf(); //Started only for this, nothing to keep
            return START_NOT_STICKY;
        }

        int types = intent != null ? intent.getIntExtra(EXTRA_TYPES, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types);
        } catch (Exception e) {
            //A type whose prerequisite is not met (for example location services turned off) is refused with a
            //SecurityException. The run then only lasts while it is seen, as without the setting.
            Log.e(TAG, "Could not promote the service to the foreground: " + e.getMessage());
            MeasurementRun failed = run;
            run = null;
            promoted = false;
            endPending = false;
            stopSelf();
            if (failed != null)
                failed.onServiceFailed();
            return START_NOT_STICKY;
        }
        promoted = true;
        if (endPending || run == null) {
            //The run ended before the service got here
            endPending = false;
            promoted = false;
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        refreshWakeLock();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        //Only the service's own state: a start for the next run may already be on its way to a new instance
        if (instance == this) {
            instance = null;
            if (promoted) {
                //Ended by Android, not by the run
                MeasurementRun lost = run;
                run = null;
                promoted = false;
                if (lost != null)
                    lost.onServiceFailed();
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refresh() {
        //Without the permission (Android 13+) the service runs on, listed only in the system's task manager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        refreshWakeLock();
    }

    @SuppressLint("WakelockTimeout") //A measurement has no bounded duration; the lock ends with it
    private void refreshWakeLock() {
        if (wakeLock == null)
            return;
        boolean needed = run != null && run.isActive();
        if (needed && !wakeLock.isHeld())
            wakeLock.acquire();
        else if (!needed && wakeLock.isHeld())
            wakeLock.release();
    }

    private PendingIntent action(String action, int requestCode, int flags) {
        Intent intent = new Intent(this, MeasurementService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private Notification buildNotification() {
        MeasurementRun current = run;
        boolean ended = current != null && current.isFinishedUnseen();
        String title = current != null ? current.experiment.title : "";
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phyphox_transparent)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(ended ? R.string.backgroundMeasurementEnded : R.string.backgroundMeasurementNotification, title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                //Swiped away (Android 14+): a running measurement shows it again, an ended one lets the process go
                .setDeleteIntent(action(ended ? ACTION_CLOSE : ACTION_REPOST, 2, flags));
        if (current != null)
            builder.setContentIntent(PendingIntent.getActivity(this, 0, returnIntent(this, current), flags));
        if (ended)
            builder.addAction(0, getString(R.string.close), action(ACTION_CLOSE, 3, flags));
        else
            builder.addAction(0, getString(R.string.stop), action(ACTION_STOP, 1, flags));
        return builder.build();
    }
}
