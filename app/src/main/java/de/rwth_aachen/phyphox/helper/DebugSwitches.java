package de.rwth_aachen.phyphox.helper;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

//Automation switches for unattended runs (lab driver, store screenshots); the Android counterpart
//of iOS's launch arguments (AutomationLaunchOptions in AppDelegate.swift). Read via getprop, fresh
//on every call (the driver may set them while the app runs), except assumeSensors (cached per process).
//  debug.phyphox.remote        "1"/"true": enable remote access for every experiment launched, no confirmation dialog (iOS -phyphoxRemote)
//  debug.phyphox.remotePort    serve remote access on this port, without the free-port fallback ladder (iOS -phyphoxRemotePort)
//  debug.phyphox.autoConfirm   dismiss the network privacy and photosensitivity notices, decline saving a downloaded experiment (iOS -phyphoxAutoConfirm)
//  debug.phyphox.assumeSensors report every sensor as present when building the collection list; affects rendering only (iOS -phyphoxAssumeSensors)
//  debug.phyphox.view          0-based view (tab) index to open on; absent/invalid/out of range = first view; restored instance state wins (iOS -phyphoxView)
//Safe to ship in release builds: the debug.* namespace is writable only by the adb shell user and root, never by apps.
public class DebugSwitches {

    private static final String REMOTE = "debug.phyphox.remote";
    private static final String REMOTE_PORT = "debug.phyphox.remotePort";
    private static final String AUTO_CONFIRM = "debug.phyphox.autoConfirm";
    private static final String ASSUME_SENSORS = "debug.phyphox.assumeSensors";
    private static final String VIEW = "debug.phyphox.view";

    private static Boolean assumeSensors = null;

    public static boolean remoteEnabled() {
        return isSet(REMOTE);
    }

    public static boolean autoConfirm() {
        return isSet(AUTO_CONFIRM);
    }

    public static boolean assumeSensors() {
        if (assumeSensors == null)
            assumeSensors = isSet(ASSUME_SENSORS);
        return assumeSensors;
    }

    //0 if absent or invalid; the caller still checks it against the experiment's view count.
    public static int startView() {
        try {
            int view = Integer.parseInt(get(VIEW));
            if (view > 0)
                return view;
        } catch (NumberFormatException e) {
        }
        return 0;
    }

    //0 if absent or invalid, in which case the configured port and its fallback ladder apply.
    public static int remotePort() {
        try {
            int port = Integer.parseInt(get(REMOTE_PORT));
            if (port > 0 && port < 65536)
                return port;
        } catch (NumberFormatException e) {
        }
        return 0;
    }

    private static boolean isSet(String property) {
        String value = get(property).toLowerCase(Locale.US);
        return value.equals("1") || value.equals("true");
    }

    //Unset property or any failure to run getprop reads as "": switch off.
    private static String get(String property) {
        try {
            Process process = new ProcessBuilder("/system/bin/getprop", property)
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                process.destroy();
            }
        } catch (Exception e) {
            Log.w("debugSwitches", "Could not read " + property + ": " + e.getMessage());
            return "";
        }
    }
}
