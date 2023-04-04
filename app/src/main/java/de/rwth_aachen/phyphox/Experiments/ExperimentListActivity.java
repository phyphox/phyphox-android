package de.rwth_aachen.phyphox.Experiments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import de.rwth_aachen.phyphox.BaseActivity.BaseActivity;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.Experiments.data.ExperimentDataManager;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepository;
import de.rwth_aachen.phyphox.GlobalConfig;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.PhyphoxAlertBuilder;
import de.rwth_aachen.phyphox.Helper.PhyphoxSharedPreference;
import de.rwth_aachen.phyphox.Helper.ReportingScrollView;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SettingsFragment;

public class ExperimentListActivity extends BaseActivity<ExperimentListViewModel> {

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

    PopupWindow popupWindow = null;

    long currentQRcrc32 = -1;
    int currentQRsize = -1;
    byte[][] currentQRdataPackets = null;



    @NonNull
    @Override
    protected ExperimentListViewModel createViewModel() {
        ExperimentsRepository experimentsRepository = ExperimentDataManager.getInstance().getExperimentRepository();
        ExperimentViewModelFactory factory = new ExperimentViewModelFactory(getApplication(), experimentsRepository);
        return new ViewModelProvider(this, factory).get(ExperimentListViewModel.class);
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.Theme_Phyphox_DayNight);
        String themePreference = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(getString(R.string.setting_dark_mode_key), SettingsFragment.DARK_MODE_ON);
        SettingsFragment.setApplicationTheme(themePreference);

        setContentView(R.layout.activity_experiment_list);

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

        observerViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadExperiments();
    }

    @Override
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.recreate();
        }
    }

    @Override
    public void onUserInteraction() {
        if (popupWindow != null)
            popupWindow.dismiss();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        String textResult;
        if (scanResult != null && (textResult = scanResult.getContents()) != null) {
            if (textResult.toLowerCase().startsWith("http://") || textResult.toLowerCase().startsWith("https://") || textResult.toLowerCase().startsWith("phyphox://")) {
                //This is an URL, open it
                //Create an intent for this new file
                Intent URLintent = new Intent(this, Experiment.class);
                URLintent.setData(Uri.parse("phyphox://" + textResult.split("//", 2)[1]));
                URLintent.setAction(Intent.ACTION_VIEW);
                //TODO handleIntent(URLintent);

            } else if (textResult.startsWith("phyphox")) {
                //The QR code contains the experiment itself. The first 13 bytes are:
                // p h y p h o x [crc32] [i] [n]
                //"phyphox" as string (7 bytes)
                //crc32 hash (big endian) of the submitted experiment (has to be the same for each qr code if the experiment is spread across multiple codes)
                //i is the index of this code in a sequence of n code (starting at zero, so i starts at 0 and end with n-1
                //n is the total number of codes for this experiment
                byte[] data = intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_0");
                if (data == null) {
                    Toast.makeText(ExperimentListActivity.this, "Unexpected error: Could not retrieve data from QR code.", Toast.LENGTH_LONG).show();
                    return;
                }
                long crc32 = (((long)(data[7] & 0xff) << 24) | ((long)(data[8] & 0xff) << 16) | ((long)(data[9] & 0xff) << 8) | ((long)(data[10] & 0xff)));
                int index = data[11];
                int count = data[12];

                if ((currentQRcrc32 >= 0 && currentQRcrc32 != crc32) || (currentQRsize >= 0 && count != currentQRsize) || (currentQRsize >= 0 && index >= currentQRsize)) {
                    showQRScanError(getString(R.string.newExperimentQRcrcMismatch), true);
                    currentQRsize = -1;
                    currentQRcrc32 = -1;
                }
                if (currentQRcrc32 < 0) {
                    currentQRcrc32 = crc32;
                    currentQRsize = count;
                    currentQRdataPackets = new byte[count][];
                }
                currentQRdataPackets[index] = Arrays.copyOfRange(data, 13, data.length);
                int missing = 0;
                for (int i = 0; i < currentQRsize; i++) {
                    if (currentQRdataPackets[i] == null)
                        missing++;
                }
                if (missing == 0) {
                    //We have all the data. Write it to a temporary file and give it to our default intent handler...
                    File tempPath = new File(getFilesDir(), "temp_qr");
                    if (!tempPath.exists()) {
                        if (!tempPath.mkdirs()) {
                            showQRScanError("Could not create temporary directory to write zip file.", true);
                            return;
                        }
                    }
                    String[] files = tempPath.list();
                    for (String file : files) {
                        if (!(new File(tempPath, file).delete())) {
                            showQRScanError("Could not clear temporary directory to extract zip file.", true);
                            return;
                        }
                    }

                    int totalSize = 0;

                    for (int i = 0; i < currentQRsize; i++) {
                        totalSize += currentQRdataPackets[i].length;
                    }
                    byte [] dataReceived = new byte[totalSize];
                    int offset = 0;
                    for (int i = 0; i < currentQRsize; i++) {
                        System.arraycopy(currentQRdataPackets[i], 0, dataReceived, offset, currentQRdataPackets[i].length);
                        offset += currentQRdataPackets[i].length;
                    }

                    CRC32 crc32Received = new CRC32();
                    crc32Received.update(dataReceived);
                    if (crc32Received.getValue() != crc32) {
                        Log.e("qrscan", "Received CRC32 " + crc32Received.getValue() + " but expected " + crc32);
                        showQRScanError(getString(R.string.newExperimentQRBadCRC), true);
                        return;
                    }

                    byte [] zipData = Helper.inflatePartialZip(dataReceived);

                    File zipFile;
                    try {
                        zipFile = new File(tempPath, "qr.zip");
                        FileOutputStream out = new FileOutputStream(zipFile);
                        out.write(zipData);
                        out.close();
                    } catch (Exception e) {
                        showQRScanError("Could not write QR content to zip file.", true);
                        return;
                    }

                    currentQRsize = -1;
                    currentQRcrc32 = -1;

                    Intent zipIntent = new Intent(this, Experiment.class);
                    zipIntent.setData(Uri.fromFile(zipFile));
                    zipIntent.setAction(Intent.ACTION_VIEW);
                    new HandleZipIntent(zipIntent, this, progress).execute();
                } else {
                    showQRScanError(getString(R.string.newExperimentQRCodesMissing1) + " " + currentQRsize + " " + getString(R.string.newExperimentQRCodesMissing2) + " " + missing, false);
                }
            } else {
                //QR code does not contain or reference a phyphox experiment
                showQRScanError(getString(R.string.newExperimentQRNoExperiment), true);
            }
        }
    }

    private void observerViewModel(){
        viewModel.getExperimentsLiveData().observe(this, new Observer<List<ExperimentDataModel>>() {
            @Override
            public void onChanged(List<ExperimentDataModel> experimentDataModels) {
                //Check all categories for the category of the new experiment
                List<ExperimentsInCategory> categories = viewModel.getCategories();
                for(ExperimentDataModel experimentDataModel: experimentDataModels){
                    for(ExperimentsInCategory iCat: categories){
                        if(iCat.hasName(experimentDataModel.getCategory())){
                            //Found it. Add the experiment and return
                            iCat.addExperiment(experimentDataModel);
                            return;
                        }
                    }
                    //Category does not yet exist. Create it and add the experiment
                    if(experimentDataModel.getCategory() != null){
                        categories.add(new ExperimentsInCategory(experimentDataModel.getCategory(), ExperimentListActivity.this));
                        categories.get(categories.size() -1).addExperiment(experimentDataModel);
                    }
                }



            }
        });
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
        CommonMethods commonMethods = new CommonMethods( getApplicationContext(), ExperimentListActivity.this);
        commonMethods.scanQRCode();
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

        //Use the app theme and create an AlertDialog-builder
        ContextThemeWrapper ctw = new ContextThemeWrapper( this, R.style.Theme_Phyphox_DayNight);
        AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
        LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View warningLayout = adbInflater.inflate(R.layout.donotshowagain, null);

        //This reference is used to address a do-not-show-again checkbox within the dialog
        final CheckBox dontShowAgain = (CheckBox) warningLayout.findViewById(R.id.donotshowagain);

        //Setup AlertDialog builder
        adb.setView(warningLayout);
        adb.setTitle(R.string.warning);
        adb.setPositiveButton(getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //User clicked ok. Did the user decide to skip future warnings?
                Boolean skipWarning = false;
                if (dontShowAgain.isChecked())
                    skipWarning = true;
                PhyphoxSharedPreference.setBooleanValue(ExperimentListActivity.this, PhyphoxSharedPreference.SKIP_WARNING, skipWarning);
                //Store user decision
            }});
        adb.show(); //User did not decide to skip, so show it.
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

        showSupportHint();
        final boolean disabled = false;

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

    @SuppressLint("ClickableViewAccessibility")
    private void showSupportHint() {
        if (popupWindow != null)
            return;
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View hintView = inflater.inflate(R.layout.support_phyphox_hint, null);
        TextView text = hintView.findViewById(R.id.support_phyphox_hint_text);
        text.setText(getString(R.string.categoryPhyphoxOrgHint));
        ImageView iv = hintView.findViewById(R.id.support_phyphox_hint_arrow);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)iv.getLayoutParams();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(lp);

        popupWindow = new PopupWindow(hintView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if(Build.VERSION.SDK_INT >= 21){
            popupWindow.setElevation(4.0f);
        }

        popupWindow.setOutsideTouchable(false);
        popupWindow.setTouchable(false);
        popupWindow.setFocusable(false);
        LinearLayout ll = hintView.findViewById(R.id.support_phyphox_hint_root);

        ll.setOnTouchListener((view, motionEvent) -> {
            if (popupWindow != null)
                popupWindow.dismiss();
            return true;
        });

        popupWindow.setOnDismissListener(() -> popupWindow = null);

        final View root = findViewById(R.id.rootExperimentList);
        root.post(() -> {
            try {
                popupWindow.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            } catch (WindowManager.BadTokenException e) {
                Log.e("showHint", "Bad token when showing hint. This is not unusual when app is rotating while showing the hint.");
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
               new HandleZipIntent(intent, this, progress).execute();
            }
        } else if (scheme.equals(ContentResolver.SCHEME_CONTENT) || scheme.equals("phyphox") || scheme.equals("http") || scheme.equals("https")) {
            progress = ProgressDialog.show(this, getString(R.string.loadingTitle), getString(R.string.loadingText), true);
           new HandleZipIntent(intent, this, progress).execute();
        }
    }


    protected void showQRScanError(String msg, Boolean isError) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentQRErrorTitle : R.string.newExperimentQR)
                .setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CommonMethods commonMethods = new CommonMethods( getApplicationContext(), ExperimentListActivity.this);
                        commonMethods.scanQRCode();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    //The third addExperiment function:
    //ExperimentItemAdapter.addExperiment(...) is called by category.addExperiment(...), which in
    //turn will be called here.
    //This addExperiment(...) is called for each experiment found. It checks if the experiment's
    // category already exists and adds it to this category or creates a category for the experiment
    private void addExperiment(ExperimentDataModel experimentInfo, String cat, Vector<ExperimentsInCategory> categories) {
        //Check all categories for the category of the new experiment
        for (ExperimentsInCategory icat : categories) {
            if (icat.hasName(cat)) {
                //Found it. Add the experiment and return
                icat.addExperiment(experimentInfo);
                return;
            }
        }
        //Category does not yet exist. Create it and add the experiment
        categories.add(new ExperimentsInCategory(cat, this));
        categories.lastElement().addExperiment(experimentInfo);
    }

}

