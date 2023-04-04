package de.rwth_aachen.phyphox.Experiments;

import android.app.Activity;
import android.content.Context;

import com.google.zxing.integration.android.IntentIntegrator;

import de.rwth_aachen.phyphox.R;

public class CommonMethods {

    Context context;
    Activity activity;

    public CommonMethods(Context context, Activity activity){
        this.context = context;
        this.activity = activity;

    }


    public void scanQRCode() {
        IntentIntegrator qrScan = new IntentIntegrator(activity);

        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        qrScan.setPrompt(context.getResources().getString(R.string.newExperimentQRscan));
        qrScan.setOrientationLocked(true);

        qrScan.initiateScan();
    }
}
