package de.rwth_aachen.phyphox;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

//Keeps a running measurement alive while the experiment screen is not visible. Android delivers sensor,
//location and microphone data to an app in the background only through a foreground service: a Service
//pinned by a persistent notification that declares the kinds of data it uses. The service itself does no
//measuring. The Experiment activity survives in the background (stopped, not destroyed), keeps its inputs
//and analysis thread running and only rests its view loop. The service adds the notification and a partial
//wake lock, because a foreground service alone does not keep the CPU awake once the screen turns off.
public class MeasurementService extends Service {
    private static final String TAG = "MeasurementService";
    private static final String CHANNEL_ID = "measurement";
    private static final int NOTIFICATION_ID = 1;
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_TYPES = "types";
    private static final String ACTION_STOP = "de.rwth_aachen.phyphox.action.STOP_MEASUREMENT";

    //The activity that owns the running measurement, so the notification's stop action and a swiped-away task can end it
    private static WeakReference<Experiment> owner = new WeakReference<>(null);
    private static boolean running = false;

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

    //Called when a measurement starts while the activity is visible, which the location and microphone types require
    static void start(Experiment activity, PhyphoxExperiment experiment) {
        owner = new WeakReference<>(activity);
        Intent intent = new Intent(activity, MeasurementService.class);
        intent.putExtra(EXTRA_TITLE, experiment.title);
        intent.putExtra(EXTRA_TYPES, typesFor(experiment));
        try {
            ContextCompat.startForegroundService(activity, intent);
            running = true;
        } catch (Exception e) {
            //Android 12+ refuses starts from the background. The run then only lasts while the screen is visible, as without the setting.
            Log.e(TAG, "Could not start the foreground service: " + e.getMessage());
            running = false;
        }
    }

    //Called by the activity whose measurement ends. Another Experiment activity (a second experiment opened over
    //a run that continues in the background) must not end a service it does not own.
    static void stop(Experiment activity) {
        Experiment current = owner.get();
        if (current != null && current != activity)
            return;
        end(activity);
    }

    private static void end(Context context) {
        running = false;
        owner.clear();
        context.stopService(new Intent(context, MeasurementService.class));
    }

    //True between start and stop for the activity that started it, i.e. while its running measurement may continue in the background
    static boolean isRunningFor(Experiment activity) {
        return running && owner.get() == activity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.backgroundMeasurementChannel), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null)
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phyphox:measurement");
    }

    @SuppressLint({"WakelockTimeout", "InlinedApi"}) //A measurement has no bounded duration; the lock ends with the service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMeasurement();
            return START_NOT_STICKY;
        }
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        int types = intent != null ? intent.getIntExtra(EXTRA_TYPES, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(title), types);
        } catch (Exception e) {
            //A type whose prerequisite is not met (for example location services turned off) is refused with a
            //SecurityException. The run then only lasts while the screen is visible, as without the setting.
            Log.e(TAG, "Could not promote the service to the foreground: " + e.getMessage());
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (wakeLock != null && !wakeLock.isHeld())
            wakeLock.acquire();
        return START_NOT_STICKY;
    }

    //The notification's stop action and a swiped-away task end the measurement through its owner; without an owner only the service ends
    private void stopMeasurement() {
        Experiment activity = owner.get();
        if (activity != null)
            activity.stopMeasurement(); //Stops this service as well
        else
            end(this);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopMeasurement();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(String title) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        //Tapping returns to the experiment the way the launcher icon does: the existing task comes to the
        //front without creating a second Experiment activity (which would start a second remote server)
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = null;
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            open = PendingIntent.getActivity(this, 0, launch, flags);
        }
        Intent stopIntent = new Intent(this, MeasurementService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phyphox_transparent)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.backgroundMeasurementNotification, title != null ? title : ""))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, getString(R.string.stop), stop);
        if (open != null)
            builder.setContentIntent(open);
        return builder.build();
    }
}
