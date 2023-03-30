package de.rwth_aachen.phyphox.Helper;

import android.content.Context;
import android.content.SharedPreferences;

public class PhyphoxSharedPreference {

    public static final String PREFS_NAME = "phyphox";
    // properties
    public static final String LAST_SUPPORT_HINT = "lastSupportHint";
    public static final String SKIP_WARNING = "skipWarning";

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getStringValue(Context context, String key) {
        return getSharedPreferences(context).getString(key , null);
    }

    public static boolean getBooleanValue(Context context, String key) {
        return getSharedPreferences(context).getBoolean(key , false);
    }

    public static void setStringValue(Context context, String key, String newValue) {
        final SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(key, newValue);
        editor.apply();
    }

    public static void setBooleanValue(Context context, String key, boolean newValue) {
        final SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putBoolean(key, newValue);
        editor.apply();
    }


}

