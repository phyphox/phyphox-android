package de.rwth_aachen.phyphox.Experiments.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.rwth_aachen.phyphox.App;
import de.rwth_aachen.phyphox.PhyphoxFile;
import de.rwth_aachen.phyphox.R;

public class Utility {

    Context context;
    Activity activity;

    public Utility(Activity activity){
        this.context = App.getContext();
        this.activity = activity;

    }

    public void scanQRCode() {
        IntentIntegrator qrScan = new IntentIntegrator(activity);

        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        qrScan.setPrompt(context.getResources().getString(R.string.newExperimentQRscan));
        qrScan.setBeepEnabled(false);
        qrScan.setOrientationLocked(true);

        qrScan.initiateScan();
    }
}
