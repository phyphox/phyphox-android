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
//time, because the driver may set a property while the app is already running.
public class DebugSwitches {

    private static final String REMOTE = "debug.phyphox.remote";
    private static final String REMOTE_PORT = "debug.phyphox.remotePort";
    private static final String AUTO_CONFIRM = "debug.phyphox.autoConfirm";

    public static boolean remoteEnabled() {
        return isSet(REMOTE);
    }

    public static boolean autoConfirm() {
        return isSet(AUTO_CONFIRM);
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
