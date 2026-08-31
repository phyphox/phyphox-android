package de.rwth_aachen.phyphox.helper;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

//Automation seam for unattended runs (the lab driver of the cross-platform test strategy), the
//Android counterpart of iOS's launch arguments (AutomationLaunchOptions in AppDelegate.swift) -
//one host-controlled switch each, in two platform idioms:
//
//  debug.phyphox.remote       "1" or "true" enables remote access for every experiment launched
//                             while it is set, exactly as the menu toggle would but without its
//                             confirmation dialog. iOS: -phyphoxRemote.
//  debug.phyphox.remotePort   serves remote access on this port instead of the configured one,
//                             and without the fallback ladder that hunts for a free port, so a
//                             host script does not have to discover which port was picked.
//                             iOS: -phyphoxRemotePort.
//  debug.phyphox.autoConfirm  confirms the notices an experiment shows when it opens - the
//                             network privacy warning and the photosensitivity warning - and
//                             declines the offer to save a downloaded experiment locally. They
//                             are informational (their only action is OK), but unattended they
//                             stall the run: the network privacy notice in particular gates the
//                             connection setup, so the network fixture experiments cannot run
//                             without this. Exactly those three, the same set as iOS's
//                             -phyphoxAutoConfirm and the switch-bypassed-ui test row; the
//                             Android-only vendor sensor warning is not bypassed. It skips no
//                             user choice and no system permission dialog, which the app cannot
//                             dismiss anyway. iOS: -phyphoxAutoConfirm.
//  debug.phyphox.assumeSensors reports every sensor as present while building the experiment
//                             collection, so no entry is greyed out as unavailable. This one is
//                             for the store screenshot system, not for the lab driver: the
//                             emulators it captures on have almost no sensors, which would turn
//                             the collection screenshot into a wall of half-faded entries. It
//                             only affects how the list is rendered - an experiment that is
//                             started anyway still finds no sensor and records nothing, which is
//                             fine for a generated copy that carries its data as init values.
//                             iOS: -phyphoxAssumeSensors.
//  debug.phyphox.view         the view (tab) index the experiment opens on, counting from 0 in
//                             the order the views appear in the file. Absent, not a number or
//                             out of range means the first view, i.e. current behaviour. Also
//                             for the screenshot system: one scene wants the second view, and
//                             tapping a tab at coordinates that differ per form factor is what
//                             made the old screenshot tests unmaintainable. A restored instance
//                             state still wins - it reopens the view the user was on.
//                             iOS: -phyphoxView.
//
//Android needs no counterpart of iOS's -phyphoxUrl: "adb shell am start" opens a URL without the
//system asking for confirmation.
//
//Why system properties, and why this ships ungated in release builds: the debug.* namespace is
//writable only by the shell user (adb) and root - the platform's SELinux policy grants no app
//that permission - while every app may read it. So these switches can only be turned on by
//whoever already controls the device over adb, which is the same trust boundary as installing a
//build in the first place; another app, or a user of a store install, cannot reach them. That is
//what lets the lab phones run store-identical release builds instead of a separate debug build,
//which would be a different binary from the one under test.
//
//The properties are sticky until reboot, so the semantics are "experiments launched while the
//property is set", and the driver clears them when it is done:
//
//  adb shell setprop debug.phyphox.remote 1
//  adb shell am start -a android.intent.action.VIEW -d "phyphox://asset=accelerometer.phyphox"
//  ...
//  adb shell setprop debug.phyphox.remote '""'
//
//On Android 17 and newer the remote server also needs ACCESS_LOCAL_NETWORK, which the menu path
//requests interactively; an unattended run has to grant it beforehand
//("adb shell pm grant de.rwth_aachen.phyphox android.permission.ACCESS_LOCAL_NETWORK").
//
//The values are read through getprop rather than the hidden SystemProperties class, which is not
//part of the SDK and is blocked for apps on recent Android versions. They are read fresh every
//time, because the driver may set a property while the app is already running. The exception is
//assumeSensors, which is asked once per experiment while the collection is built and would spawn
//a getprop process for each of the sixty-odd entries, so it is read once per app process. The
//screenshot host sets it before it starts the app, so that costs it nothing.
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

    //Whether every sensor should be treated as available. Cached for the lifetime of the process,
    //see the note above.
    public static boolean assumeSensors() {
        if (assumeSensors == null)
            assumeSensors = isSet(ASSUME_SENSORS);
        return assumeSensors;
    }

    //The view (tab) index an experiment should open on, or 0 if the property is absent or does
    //not name a non-negative index. The caller still has to check it against the number of views
    //the experiment actually has.
    public static int startView() {
        try {
            int view = Integer.parseInt(get(VIEW));
            if (view > 0)
                return view;
        } catch (NumberFormatException e) {
            //Not a number. Treat it like an unset property.
        }
        return 0;
    }

    //The port remote access should be served on, or 0 if the property is absent or does not name
    //a valid port - in which case the configured port and its fallback ladder apply as usual.
    public static int remotePort() {
        try {
            int port = Integer.parseInt(get(REMOTE_PORT));
            if (port > 0 && port < 65536)
                return port;
        } catch (NumberFormatException e) {
            //Not a number. Treat it like an unset property.
        }
        return 0;
    }

    private static boolean isSet(String property) {
        String value = get(property).toLowerCase(Locale.US);
        return value.equals("1") || value.equals("true");
    }

    //An unset property reads as an empty line, and everything that goes wrong here - no getprop
    //on this system, no permission to execute it - means the same thing: the switch is off.
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
