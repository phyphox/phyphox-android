package de.rwth_aachen.phyphox.Experiments;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import de.rwth_aachen.phyphox.BaseActivity.BaseActivity;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.GlobalConfig;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.PhyphoxAlertBuilder;
import de.rwth_aachen.phyphox.Helper.PhyphoxSharedPreference;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.databinding.ActivityExperimentListBinding;

public class ExperimentListActivity extends BaseActivity<ActivityExperimentListBinding, ExperimentListViewModel> {

    ExperimentItemAdapter experimentItemAdapter;

    private FloatingActionButton newExperimentButton;
    private FloatingActionButton newExperimentSimple;
    private FloatingActionButton newExperimentBluetooth;
    private FloatingActionButton newExperimentQR;
    private View experimentListDimmer;
    private TextView newExperimentSimpleLabel;
    private TextView newExperimentBluetoothLabel;
    private TextView newExperimentQRLabel;

    boolean newExperimentDialogOpen = false;

    ProgressDialog progress = null;

    @NonNull
    @Override
    protected ExperimentListViewModel createViewModel() {
        return null;
    }

    @NonNull
    @Override
    protected ActivityExperimentListBinding createViewBinding(LayoutInflater layoutInflater) {
        return null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity parent = ExperimentListActivity.this;

        if(displayDoNotDamageYourPhone())
            showSupportHintIfRequired();

        ImageView creditsV = (ImageView) findViewById(R.id.credits);
        creditsV.setOnClickListener(v -> {
            PopupMenu popupMenu = new ExperimentMenu(new ContextThemeWrapper(parent, R.style.Theme_Phyphox_DayNight), v);
            popupMenu.inflate(R.menu.menu_help);
            popupMenu.show();
        });

        setupFloatingActionButtons();

        handleIntent(getIntent());

        experimentItemAdapter = new ExperimentItemAdapter(parent, );

    }

    private final Button.OnClickListener neocl = v -> {
        if (newExperimentDialogOpen)
            hideNewExperimentDialog();
        else
            showNewExperimentDialog();
    };

    private final Button.OnClickListener neoclSimple = v -> {
        hideNewExperimentDialog();
        newExperimentDialog();
    };

    private final Button.OnClickListener neoclBluetooth = v -> {
        hideNewExperimentDialog();
        (new RunBluetoothScan(ExperimentListActivity.this)).execute();
    };

    private final Button.OnClickListener neoclQR = v -> {
        hideNewExperimentDialog();
        scanQRCode();
    };

    private void setupFloatingActionButtons() {
        newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        experimentListDimmer = (View) findViewById(R.id.experimentListDimmer);
        newExperimentButton.setOnClickListener(neocl);
        experimentListDimmer.setOnClickListener(neocl);

        newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        newExperimentSimple.setOnClickListener(neoclSimple);
        newExperimentSimpleLabel.setOnClickListener(neoclSimple);

        newExperimentBluetooth = (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        newExperimentBluetooth.setOnClickListener(neoclBluetooth);
        newExperimentBluetoothLabel.setOnClickListener(neoclBluetooth);

        newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        newExperimentQR.setOnClickListener(neoclQR);
        newExperimentQRLabel.setOnClickListener(neoclQR);
    }

    private boolean displayDoNotDamageYourPhone(){

        boolean skipTheWarning = PhyphoxSharedPreference.getBooleanValue(this,PhyphoxSharedPreference.SKIP_WARNING);
        if(skipTheWarning){
            return false;
        }
        AtomicBoolean skipWarning = new AtomicBoolean(false);
        AlertDialog.Builder doNotShowAgainAlert = new PhyphoxAlertBuilder(this, R.layout.donotshowagain, R.style.Theme_Phyphox_DayNight)
                .setTitle(R.string.warning)
                .addCheckBox(R.id.donotshowagain)
                .addPositiveWithTitle((String) getText(R.string.ok), (dialog, which) -> {
                    if(new PhyphoxAlertBuilder().getCheckBoxState()){
                        skipWarning.set(true);
                    }
                    PhyphoxSharedPreference.setBooleanValue(this, PhyphoxSharedPreference.SKIP_WARNING, skipWarning.get());
                }).build();
        doNotShowAgainAlert.show();
        return true;
    }

    private void showSupportHintIfRequired(){
        try {
            if (!getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_PERMISSIONS).versionName.equals(GlobalConfig.phyphoxCatHintRelease))
                return;
        } catch (Exception e) {
            return;
        }

        String lastSupportHint = PhyphoxSharedPreference.getStringValue(this, PhyphoxSharedPreference.LAST_SUPPORT_HINT);
        if(lastSupportHint.equals(GlobalConfig.phyphoxCatHintRelease))
            return;

        ReportingScrollView sv = ((ReportingScrollView)findViewById(R.id.experimentScroller));
        sv.setOnScrollChangedListener((scrollView, x, y, oldx, oldy) -> {
            int bottom = scrollView.getChildAt(scrollView.getChildCount()-1).getBottom();
            if (y + 10 > bottom - scrollView.getHeight()) {
                scrollView.setOnScrollChangedListener(null);
                PhyphoxSharedPreference.setStringValue(
                        sv.getContext(), PhyphoxSharedPreference.LAST_SUPPORT_HINT, GlobalConfig.phyphoxCatHintRelease);

            }
        });

    }

    //This displays a rather complex dialog to allow users to set up a simple experiment
    private void newExperimentDialog() {
        //Build the dialog with an AlertDialog builder...
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder neDialog = new CreateSimpleExperimentDialog(ctw);
        neDialog.setTitle(R.string.newExperiment);
        neDialog.show();
    }

    private void showNewExperimentDialog(){
        newExperimentDialogOpen = true;

        Animation rotate45In = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_rotate45);
        Animation fabIn = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_in);
        Animation labelIn = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_label_in);
        Animation fadeDark = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fade_dark);

        newExperimentButton.startAnimation(rotate45In);
        newExperimentSimple.startAnimation(fabIn);
        newExperimentSimpleLabel.startAnimation(labelIn);
        newExperimentBluetooth.startAnimation(fabIn);
        newExperimentBluetoothLabel.startAnimation(labelIn);
        newExperimentQR.startAnimation(fabIn);
        newExperimentQRLabel.startAnimation(labelIn);
        experimentListDimmer.startAnimation(fadeDark);

        newExperimentSimple.setClickable(true);
        newExperimentSimpleLabel.setClickable(true);
        newExperimentBluetooth.setClickable(true);
        newExperimentBluetoothLabel.setClickable(true);
        newExperimentQR.setClickable(true);
        newExperimentQRLabel.setClickable(true);
        experimentListDimmer.setClickable(true);
    }

    protected void hideNewExperimentDialog() {
        newExperimentDialogOpen = false;

        Animation rotate0In = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_rotate0);
        Animation fabOut = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fab_out);
        Animation labelOut = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_label_out);
        Animation fadeTransparent = AnimationUtils.loadAnimation(getBaseContext(), R.anim.experiment_list_fade_transparent);

        newExperimentSimple.setClickable(false);
        newExperimentSimpleLabel.setClickable(false);
        newExperimentBluetooth.setClickable(false);
        newExperimentBluetoothLabel.setClickable(false);
        newExperimentQR.setClickable(false);
        newExperimentQRLabel.setClickable(false);
        experimentListDimmer.setClickable(false);

        newExperimentButton.startAnimation(rotate0In);
        newExperimentSimple.startAnimation(fabOut);
        newExperimentSimpleLabel.startAnimation(labelOut);
        newExperimentBluetooth.startAnimation(fabOut);
        newExperimentBluetoothLabel.startAnimation(labelOut);
        newExperimentQR.startAnimation(fabOut);
        newExperimentQRLabel.startAnimation(labelOut);
        experimentListDimmer.startAnimation(fadeTransparent);

    }

    protected void handleIntent(Intent intent) {
        if (progress != null)
            progress.dismiss();

        String scheme = intent.getScheme();
        if (scheme == null)
            return;
        boolean isZip = false;
        if (scheme.equals(ContentResolver.SCHEME_FILE)) {
            if (!intent.getData().getPath().startsWith(getFilesDir().getPath())
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Android 6.0: No permission? Request it!
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                //We will stop here. If the user grants the permission, the permission callback will restart the action with the same intent
                return;
            }
            Uri uri = intent.getData();

            byte[] data = new byte[4];
            InputStream is;
            try {
                is = this.getContentResolver().openInputStream(uri);
                if (is.read(data, 0, 4) < 4) {
                    Toast.makeText(this, "Error: File truncated.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (FileNotFoundException e) {
                Toast.makeText(this, "Error: File not found.", Toast.LENGTH_LONG).show();
                return;
            } catch (IOException e) {
                Toast.makeText(this, "Error: IOException.", Toast.LENGTH_LONG).show();
                return;
            }

            isZip = (data[0] == 0x50 && data[1] == 0x4b && data[2] == 0x03 && data[3] == 0x04);

            if (!isZip) {
                //This is just a single experiment - Start the Experiment activity and let it handle the intent
                Intent forwardedIntent = new Intent(intent);
                forwardedIntent.setClass(this, Experiment.class);
                this.startActivity(forwardedIntent);
            } else {
                //We got a zip-file. Let's see what's inside...
                progress = ProgressDialog.show(this, getString(R.string.loadingTitle), getString(R.string.loadingText), true);
                new HandleZipIntent(intent, this).execute();
            }
        } else if (scheme.equals(ContentResolver.SCHEME_CONTENT) || scheme.equals("phyphox") || scheme.equals("http") || scheme.equals("https")) {
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new ExperimentList.handleCopyIntent(intent, this).execute();
        }
    }

    //The third addExperiment function:
    //ExperimentItemAdapter.addExperiment(...) is called by category.addExperiment(...), which in
    //turn will be called here.
    //This addExperiment(...) is called for each experiment found. It checks if the experiment's
    // category already exists and adds it to this category or creates a category for the experiment
    private void addExperiment(String exp, String cat, int color, Drawable image, String description, String xmlFile, String isTemp, boolean isAsset, Integer unavailableSensor, String isLink, Vector<ExperimentsInCategory> categories) {
        //Check all categories for the category of the new experiment
        for (ExperimentsInCategory icat : categories) {
            if (icat.hasName(cat)) {
                //Found it. Add the experiment and return
                icat.addExperiment(exp, color, image, description, xmlFile, isTemp, isAsset, unavailableSensor, isLink);
                return;
            }
        }
        //Category does not yet exist. Create it and add the experiment
        categories.add(new ExperimentsInCategory(cat, this));
        categories.lastElement().addExperiment(exp, color, image, description, xmlFile, isTemp, isAsset, unavailableSensor, isLink);
    }

}
