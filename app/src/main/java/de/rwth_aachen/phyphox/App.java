package de.rwth_aachen.phyphox;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDexApplication;

//This extension to application is only used to store measured data in memory as this may easily exceed the amount of data allowed on the transaction stack

public class App extends MultiDexApplication {

    //Not recommended Approach
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        App.context = getApplicationContext();
    }

    public PhyphoxExperiment experiment = null;

    public static Context getContext() {
        return context;
    }
}
