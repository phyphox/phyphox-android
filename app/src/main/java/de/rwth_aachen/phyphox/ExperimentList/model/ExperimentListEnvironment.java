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

    public ExperimentListEnvironment(Activity parent) {
        if (parent == null) {
            throw new NullPointerException("Parent cannot be null");
        }
        this.context = parent.getApplicationContext();
        this.assetManager = parent.getAssets();
        this.resources = parent.getResources();
        this.parent = parent;

    }

    public File getFilesDir() {
        return context.getFilesDir();
    }
}
