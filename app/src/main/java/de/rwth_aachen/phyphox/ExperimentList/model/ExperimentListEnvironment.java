package de.rwth_aachen.phyphox.ExperimentList.model;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.io.File;

public class ExperimentListEnvironment {
    public AssetManager assetManager;
    public Resources resources;
    public Context context;
    public Activity parent;

    public ExperimentListEnvironment(AssetManager assetManager, Resources resources,
                                     Context context, Activity parent) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.assetManager = assetManager;
        this.resources = resources;
        this.parent = parent;

    }

    public File getFilesDir() {
        return context.getFilesDir();
    }
}
