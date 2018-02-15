package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static de.rwth_aachen.phyphox.BluetoothInput.CONFIG_DESCRIPTOR;

//ExperimentList implements the activity which lists all experiments to the user. This is the start
//activity for this app if it is launched without an intent.

public class ExperimentList extends AppCompatActivity {

    //Strings which define extra information for intents starting an experiment from local files
    public final static String EXPERIMENT_XML = "com.dicon.phyphox.EXPERIMENT_XML";
    public final static String EXPERIMENT_ISTEMP= "com.dicon.phyphox.EXPERIMENT_ISTEMP";
    public final static String EXPERIMENT_ISASSET = "com.dicon.phyphox.EXPERIMENT_ISASSET";
    public final static String EXPERIMENT_UNAVAILABLESENSOR = "com.dicon.phyphox.EXPERIMENT_UNAVAILABLESENSOR";
    public final static String EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS= "com.dicon.phyphox.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS";

    //String constant to identify our preferences
    public static final String PREFS_NAME = "phyphox";

    //A resource reference for easy access
    private Resources res;

    ProgressDialog progress = null;

    long currentQRcrc32 = -1;
    int currentQRsize = -1;
    byte[][] currentQRdataPackets = null;

    byte[] currentBluetoothData = null;
    int currentBluetoothDataSize = 0;
    int currentBluetoothDataIndex = 0;
    long currentBluetoothDataCRC32 = 0;
    CRC32 currentBluetoothDataActualCRC32 = new CRC32();

    boolean newExperimentDialogOpen = false;

    private Vector<ExperimentsInCategory> categories = new Vector<>(); //The list of categories. The ExperimentsInCategory class (see below) holds a ExperimentsInCategory and all its experiment items
    private HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    private HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>(); //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices

    //The class TextIcon is a drawable that displays up to three characters in a rectangle as a
    //substitution icon, used if an experiment does not have its own icon
    public class TextIcon extends Drawable {

        private final String text; //The characters too be displayed
        private final Paint paint; //The paint for the characters
        private final Paint paintBG; //The paint for the background

        //The constructor takes a context and the characters to display. It also sets up the paints
        public TextIcon(String text, Context c) {

            this.text = text; //Store the characters

            //Text-Paint
            this.paint = new Paint();
            paint.setColor(ContextCompat.getColor(c, R.color.main));
            paint.setTextSize(res.getDimension(R.dimen.expElementIconSize)*0.5f);
            paint.setAntiAlias(true);
            paint.setFakeBoldText(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);

            //Background paint
            this.paintBG = new Paint();
            paintBG.setColor(ContextCompat.getColor(c, R.color.highlight));
            paintBG.setStyle(Paint.Style.FILL);
        }

        @Override
        //Draw the icon
        public void draw(Canvas canvas) {
            //A rectangle and text on top. Quite simple.
            canvas.drawRect(new Rect(0, 0, (int)res.getDimension(R.dimen.expElementIconSize), (int)res.getDimension(R.dimen.expElementIconSize)), paintBG);
            canvas.drawText(text, (int)res.getDimension(R.dimen.expElementIconSize)/2, (int)res.getDimension(R.dimen.expElementIconSize)*2/3, paint);
        }

        @Override
        //Needs to be implemented.
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            paintBG.setAlpha(alpha);
        }

        @Override
        //Needs to be implemented.
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
            paintBG.setColorFilter(cf);
        }

        @Override
        //Needs to be implemented.
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    //The class BitmapIcon is a drawable that displays a user-given PNG on top of an orange background
    public class BitmapIcon extends Drawable {

        private final Paint paint; //The paint for the icon
        private final Paint paintBG; //The paint for the background
        private final Bitmap icon;

        //The constructor takes a context and the characters to display. It also sets up the paints
        public BitmapIcon(Bitmap icon, Context c) {
            this.icon = icon;

            //Icon-Paint
            this.paint = new Paint();
            paint.setAntiAlias(true);

            //Background paint
            this.paintBG = new Paint();
            paintBG.setColor(ContextCompat.getColor(c, R.color.highlight));
            paintBG.setStyle(Paint.Style.FILL);
        }

        @Override
        //Draw the icon
        public void draw(Canvas canvas) {
            //A rectangle and text on top. Quite simple.
            int size = (int)res.getDimension(R.dimen.expElementIconSize);
            int wSrc = icon.getWidth();
            int hSrc = icon.getHeight();
            canvas.drawRect(new Rect(0, 0, size, size), paintBG);
            canvas.drawBitmap(icon, new Rect(0, 0, wSrc, hSrc), new Rect(0, 0, size, size), paint);
        }

        @Override
        //Needs to be implemented.
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            paintBG.setAlpha(alpha);
        }

        @Override
        //Needs to be implemented.
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
            paintBG.setColorFilter(cf);
        }

        @Override
        //Needs to be implemented.
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    //This adapter is used to fill the gridView of the categories in the experiment list.
    //So, this can be considered to be the experiment entries within an category
    private class ExperimentItemAdapter extends BaseAdapter {
        final private Activity parentActivity; //Reference to the main activity for the alertDialog when deleting files

        private String preselectedBluetoothAddress = null;

        //Experiment data
        Vector<Drawable> icons = new Vector<>(); //List of icons for each experiment
        Vector<String> titles = new Vector<>(); //List of titles for each experiment
        Vector<String> infos = new Vector<>(); //List of short descriptions for each experiment
        Vector<String> xmlFiles = new Vector<>(); //List of xmlFile name for each experiment (has to be provided in the intent if the user wants to load this)
        Vector<Boolean> isTemp = new Vector<>(); //List of booleans for each experiment, which track whether the file is a temporary file
        Vector<Boolean> isAsset = new Vector<>(); //List of booleans for each experiment, which track whether the file is an asset or stored loacally (has to be provided in the intent if the user wants to load this)
        Vector<Integer> unavailableSensorList = new Vector<>(); //List of strings for each experiment, which give the name of the unavailable sensor if sensorReady is false

        //The constructor takes the activity reference. That's all.
        public ExperimentItemAdapter(Activity parentActivity) {
            this.parentActivity = parentActivity;
        }

        public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
            this.preselectedBluetoothAddress = preselectedBluetoothAddress;
        }

        //The number of elements is just the number of icons. (Any of the lists should do)
        public int getCount() {
            return icons.size();
        }

        //We don't need to pick an object with this interface, but it has to be implemented
        public Object getItem(int position) {
            return null;
        }

        //The index is used as an id. That's enough, but has to be implemented
        public long getItemId(int position) {
            return position;
        }

        //This starts the intent for an experiment if the user clicked an experiment.
        //It takes the index and the view that has been clicked (just for the animation)
        public void start(int position, View v) {
            //Create the intent and place the experiment location in it
            Intent intent = new Intent(v.getContext(), Experiment.class);
            intent.putExtra(EXPERIMENT_XML, xmlFiles.get(position));
            intent.putExtra(EXPERIMENT_ISTEMP, isTemp.get(position));
            intent.putExtra(EXPERIMENT_ISASSET, isAsset.get(position));
            intent.putExtra(EXPERIMENT_UNAVAILABLESENSOR, unavailableSensorList.get(position));
            if (this.preselectedBluetoothAddress != null)
                intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, this.preselectedBluetoothAddress);
            intent.setAction(Intent.ACTION_VIEW);

            //If we are on a recent API, we can add a nice zoom animation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityOptions options = ActivityOptions.makeScaleUpAnimation(v, 0,
                        0, v.getWidth(), v.getHeight());
                v.getContext().startActivity(intent, options.toBundle());
            } else { //old API? Just fire up the experiment.
                v.getContext().startActivity(intent);
            }
        }

        //Called to fill the adapter with experiment.
        //For each experiment we need an icon, a title, a short description, the location of the
        // file and whether it can be found as an asset or a local file.
        public void addExperiment(Drawable icon, String title, String info, String xmlFile, boolean isTemp, boolean isAsset, Integer unavailableSensor) {
            //Insert it alphabetically into out list. So find the element before which the new
            //title belongs.
            int i;
            for (i = 0; i < titles.size(); i++) {
                if (titles.get(i).compareTo(title) >= 0)
                    break;
            }

            //Now insert the experiment here
            icons.insertElementAt(icon, i);
            titles.insertElementAt(title, i);
            infos.insertElementAt(info, i);
            xmlFiles.insertElementAt(xmlFile, i);
            this.isTemp.insertElementAt(isTemp, i);
            this.isAsset.insertElementAt(isAsset, i);
            unavailableSensorList.insertElementAt(unavailableSensor, i);

            //Notify the adapter that we changed its contents
            this.notifyDataSetChanged();
        }

        //This mini class holds all the Android views to be displayed
        public class Holder {
            ImageView icon; //The icon
            TextView title; //The title text
            TextView info;  //The short description text
            ImageButton deleteBtn; //A button to delete local experiments (if they are not an asset)
        }

        //Construct the view for an element.
        public View getView(final int position, View convertView, ViewGroup parent) {
            Holder holder; //Holds all views. loaded from convertView or reconstructed
            if(convertView == null) { //No convertView there. Let's build from scratch.

                //Create the convertView from our layout and create an onClickListener
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.experiment_item, null);
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (unavailableSensorList.get(position) < 0)
                            start(position, v);
                        else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(res.getString(R.string.sensorNotAvailableWarningText1) + " " + res.getString(unavailableSensorList.get(position)) + " " + res.getString(R.string.sensorNotAvailableWarningText2))
                                    .setTitle(R.string.sensorNotAvailableWarningTitle)
                                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {

                                        }
                                    })
                                    .setNeutralButton(res.getString(R.string.sensorNotAvailableWarningMoreInfo), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            Uri uri = Uri.parse(res.getString(R.string.sensorNotAvailableWarningMoreInfoURL));
                                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                            if (intent.resolveActivity(getPackageManager()) != null) {
                                                startActivity(intent);
                                            }
                                        }
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }
                    }
                });

                //Create our holder and set its refernces to the views
                holder = new Holder();
                holder.icon = (ImageView) convertView.findViewById(R.id.expIcon);
                holder.title = (TextView) convertView.findViewById(R.id.expTitle);
                holder.info = (TextView) convertView.findViewById(R.id.expInfo);
                holder.deleteBtn = (ImageButton) convertView.findViewById(R.id.deleteButton);

                //Connect the convertView and the holder to retrieve it later
                convertView.setTag(holder);
            } else {
                //There is an existing view. Retrieve its holder
                holder = (Holder) convertView.getTag();
            }

            //Update icons and texts
            holder.icon.setImageDrawable(icons.get(position));
            holder.title.setText(titles.get(position));
            holder.info.setText(infos.get(position));

            if (unavailableSensorList.get(position) >= 0) {
                holder.title.setTextColor(res.getColor(R.color.mainDisabled));
                holder.info.setTextColor(res.getColor(R.color.mainDisabled));
            } else {
                holder.title.setTextColor(res.getColor(R.color.main));
                holder.info.setTextColor(res.getColor(R.color.main2));
            }

            //Handle the delete button. Set it visible only for non-assets
            if (isTemp.get(position) || isAsset.get(position))
                holder.deleteBtn.setVisibility(ImageView.GONE); //Asset - no delete button
            else {
                //No asset. Delete button visible and it needs an onClickListener
                holder.deleteBtn.setVisibility(ImageView.VISIBLE);
                holder.deleteBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //Create dialog to ask the user if he REALLY wants to delete...
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage(res.getString(R.string.confirmDelete))
                                .setTitle(R.string.confirmDeleteTitle)
                                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        //Confirmed. Delete the item and reload the list
                                        deleteFile(xmlFiles.get(position));
                                        loadExperimentList();
                                    }
                                })
                                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        //Aborted by user. Nothing to do.
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
            }

            return convertView;
        }
    }

    //The category class wraps all experiment entries and their views of a category, including the
    //grid view and the category headline
    private class ExperimentsInCategory {
        private Context parentContext; //Needed to create views
        public String name; //Category name (headline)
        private LinearLayout catLayout; //This is the base layout of the category, which will contain the headline and the gridView showing all the experiments
        private TextView categoryHeadline; //The TextView to display the headline
        private ExpandableHeightGridView experimentSubList; //The gridView holding experiment items. (See implementation below for the custom flavor "ExpandableHeightGridView")
        private ExperimentItemAdapter experiments; //Instance of the adapter to fill the gridView (implementation above)

        //ExpandableHeightGridView is derived from the original Android GridView.
        //The structure of our experiment list is such that we want to scroll the entire list, which
        //itself is structured into multiple categories showing multiple grid views. The original
        //grid view only expands as far as it needs to and then only loads the elements it needs to
        //show. This is a good idea for very long (or dynamically loaded) lists, but would make
        //each category scrollable on its own, which is not what we want.
        //ExpandableHeightGridView can be told to expand to show all elements at any time. This
        //destroys the memory efficiency of the original grid view, but we do not expect the
        //experiment to get so huge to need such efficiency. Also, we want to use a gridView instead
        //of a common table to achieve lever on its ability to determine the number of columns on
        //its own.
        //This has been derived from: http://stackoverflow.com/questions/4523609/grid-of-images-inside-scrollview/4536955#4536955
        private class ExpandableHeightGridView extends GridView {

            boolean expanded = false; //The full expand attribute. Is it expanded?

            //Constructor
            public ExpandableHeightGridView(Context context) {
                super(context);
            }

            //Constructor 2
            public ExpandableHeightGridView(Context context, AttributeSet attrs) {
                super(context, attrs);
            }

            //Constructor 3
            public ExpandableHeightGridView(Context context, AttributeSet attrs, int defStyle) {
                super(context, attrs, defStyle);
            }

            //Access to the expanded attribute
            public boolean isExpanded() {
                return expanded;
            }

            @Override
            //The expansion is achieved by overwriting the measured height in the onMeasure event
            public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (isExpanded()) {
                    // Calculate entire height by providing a very large height hint.
                    // View.MEASURED_SIZE_MASK represents the largest height possible.
                    int expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST);
                    //Send our height to the super onMeasure event
                    super.onMeasure(widthMeasureSpec, expandSpec);

                    ViewGroup.LayoutParams params = getLayoutParams();
                    params.height = getMeasuredHeight();
                } else {
                    //We should not expand. Just call the default onMeasure with the original parameters
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }

            //Interface to set the expanded attribute
            public void setExpanded(boolean expanded) {
                this.expanded = expanded;
            }

        }

        //Constructor for the category class, takes a category name, the layout into which it should
        // place its views and the calling activity (mostly to display the dialog in the onClick
        // listener of the delete button for each element - maybe this should be restructured).
        public ExperimentsInCategory(String name, Activity parentActivity) {
            //Store what we need.
            this.name = name;
            parentContext = parentActivity;

            //Create the base linear layout to hold title and list
            catLayout = new LinearLayout(parentContext);
            catLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lllp.setMargins(
                    res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-res.getDimensionPixelOffset(R.dimen.expElementMargin),
                    0,
                    res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-res.getDimensionPixelOffset(R.dimen.expElementMargin),
                    res.getDimensionPixelOffset(R.dimen.activity_vertical_margin)
            );
            catLayout.setLayoutParams(lllp);
            catLayout.setBackgroundColor(ContextCompat.getColor(parentContext, R.color.background));

            //Create the headline text view
            categoryHeadline = new TextView(parentContext);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
//            layout.setMargins(res.getDimensionPixelOffset(R.dimen.expElementMargin), 0, res.getDimensionPixelOffset(R.dimen.expElementMargin), res.getDimensionPixelOffset(R.dimen.expElementMargin));
            categoryHeadline.setLayoutParams(layout);
            categoryHeadline.setText(name);
            categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.headline_font));
            categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
            categoryHeadline.setBackgroundColor(ContextCompat.getColor(parentContext, R.color.highlight));
            categoryHeadline.setTextColor(ContextCompat.getColor(parentContext, R.color.main));
            categoryHeadline.setPadding(res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10, res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10);

            //Create the gridView for the experiment items
            experimentSubList = new ExpandableHeightGridView(parentContext);
            experimentSubList.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            experimentSubList.setColumnWidth(res.getDimensionPixelOffset(R.dimen.expElementWidth));
            experimentSubList.setNumColumns(ExpandableHeightGridView.AUTO_FIT);
            experimentSubList.setStretchMode(ExpandableHeightGridView.STRETCH_COLUMN_WIDTH);
            experimentSubList.setExpanded(true);

            //Create the adapter and give it to the gridView
            experiments = new ExperimentItemAdapter(parentActivity);
            experimentSubList.setAdapter(experiments);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                catLayout.setElevation(res.getDimensionPixelOffset(R.dimen.expElementElevation));
                catLayout.setClipToPadding(false);
                catLayout.setClipChildren(false);
                experimentSubList.setClipToPadding(false);
                experimentSubList.setClipChildren(false);
            }

            //Add headline and experiment list to our base layout
            catLayout.addView(categoryHeadline);
            catLayout.addView(experimentSubList);
        }

        public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
            experiments.setPreselectedBluetoothAddress(preselectedBluetoothAddress);
        }

        public void addToParent(LinearLayout parentLayout) {
            //Add the layout to the layout designated by the caller
            parentLayout.addView(catLayout);
        }

        //Wrapper to add an experiment to this category. This just hands it over to the adapter.
        public void addExperiment(String exp, Drawable image, String description, final String xmlFile, boolean isTemp, boolean isAsset, Integer unavailableSensor) {
            experiments.addExperiment(image, exp, description, xmlFile, isTemp, isAsset, unavailableSensor);
        }

        //Helper to check if the name of this category matches a given string
        public boolean hasName(String cat) {
            return cat.equals(name);
        }
    }

    class categoryComparator implements Comparator<ExperimentsInCategory> {
        public int compare(ExperimentsInCategory a, ExperimentsInCategory b) {
            if (a.name.equals(res.getString(R.string.categoryRawSensor)))
                return -1;
            if (b.name.equals(res.getString(R.string.categoryRawSensor)))
                return 1;
            if (a.name.equals(res.getString(R.string.save_state_category)))
                return -1;
            if (b.name.equals(res.getString(R.string.save_state_category)))
                return 1;
            return a.name.compareTo(b.name);
        }
    }

    //The third addExperiment function:
    //ExperimentItemAdapter.addExperiment(...) is called by category.addExperiment(...), which in
    //turn will be called here.
    //This addExperiment(...) is called for each experiment found. It checks if the experiment's
    // category already exists and adds it to this category or creates a category for the experiment
    private void addExperiment(String exp, String cat, Drawable image, String description, String xmlFile, boolean isTemp, boolean isAsset, Integer unavailableSensor, Vector<ExperimentsInCategory> categories) {
        //Check all categories for the category of the new experiment
        for (ExperimentsInCategory icat : categories) {
            if (icat.hasName(cat)) {
                //Found it. Add the experiment and return
                icat.addExperiment(exp, image, description, xmlFile, isTemp, isAsset, unavailableSensor);
                return;
            }
        }
        //Category does not yet exist. Create it and add the experiment
        categories.add(new ExperimentsInCategory(cat, this));
        categories.lastElement().addExperiment(exp, image, description, xmlFile, isTemp, isAsset, unavailableSensor);
    }

    //Decode the experiment icon (base64) and return a bitmap
    public static Bitmap decodeBase64(String input) throws IllegalArgumentException {
        byte[] decodedByte = Base64.decode(input, 0); //Decode the base64 data to binary
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length); //Interpret the binary data and return the bitmap
    }

    //Minimalistic loading function. This only retrieves the data necessary to list the experiment.
    private void loadExperimentInfo(InputStream input, String experimentXML, boolean isTemp, boolean isAsset, Vector<ExperimentsInCategory> categories, HashMap<String, Vector<String>> bluetoothDeviceNameList, HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList) {
        XmlPullParser xpp;
        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            //Prepare the PullParser
            xpp = Xml.newPullParser();
            xpp.setInput(input, "UTF-8");
        } catch (XmlPullParserException e) {
            Toast.makeText(this, "Cannot open " + experimentXML + ".", Toast.LENGTH_LONG).show();
            return;
        }

        //Strings to hold results of the few items we care about
        String title = ""; //Experiment title
        String stateTitle = ""; //A title given by the user for a saved experiment state
        String category = ""; //Experiment category
        String icon = ""; //Experiment icon (just the raw data as defined in the experiment file. Will be interpreted below)
        String description = ""; //First line of the experiment's descriptions as a short info
        Drawable image = null; //This will hold the icon

        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            int eventType = xpp.getEventType(); //should be START_DOCUMENT
            int phyphoxDepth = -1; //Depth of the first phyphox tag (We only care for title, icon, description and category directly below the phyphox tag)
            int translationBlockDepth = -1; //Depth of the translations block
            int translationDepth = -1; //Depth of a suitable translation, if found.

            //This part is used to check sensor availability before launching the experiment
            SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); //The sensor manager will probably be needed...
            boolean inInput = false;
            boolean inOutput = false;
            Integer unavailableSensor = -1;

            boolean perfectTranslationFound = false; //Becomes true if the global locale or a translation locale matches the system locale - if true, no other translations will be read.
            while (eventType != XmlPullParser.END_DOCUMENT){ //Go through all tags until the end...
                switch (eventType) {
                    case XmlPullParser.START_TAG: //React to start tags
                        switch (xpp.getName()) {
                            case "phyphox": //The phyphox tag is the root element of the experiment we want to interpret
                                if (phyphoxDepth < 0) { //There should not be a phyphox tag within an phyphox tag, but who cares. Just ignore it if it happens
                                    phyphoxDepth = xpp.getDepth(); //Remember depth of phyphox tag
                                    String globalLocale = xpp.getAttributeValue(null, "locale");
                                    if (globalLocale != null && globalLocale.equals(Locale.getDefault().getLanguage()))
                                        perfectTranslationFound = true;
                                }
                                break;
                            case "translations": //The translations block may contain a localized title and description
                                if (xpp.getDepth() != phyphoxDepth+1) //Translations block has to be immediately below phyphox tag
                                    break;
                                if (translationBlockDepth < 0) {
                                    translationBlockDepth = xpp.getDepth(); //Remember depth of the block
                                }
                                break;
                            case "translation": //The translation block may contain our localized version
                                if (xpp.getDepth() != translationBlockDepth+1) //The translation has to be immediately below he translations block
                                    break;
                                String thisLocale = xpp.getAttributeValue(null, "locale");
                                if (translationDepth < 0 && (thisLocale.equals(Locale.getDefault().getLanguage()) || (!perfectTranslationFound && thisLocale.equals("en")))) {
                                    if (thisLocale.equals(Locale.getDefault().getLanguage()))
                                        perfectTranslationFound = true;
                                    translationDepth = xpp.getDepth(); //Remember depth of the translation block
                                }
                                break;
                            case "title": //This should give us the experiment title
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    title = xpp.nextText().trim();
                                break;
                            case "state-title":
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    stateTitle = xpp.nextText().trim();
                                break;
                            case "icon": //This should give us the experiment icon (might be an acronym or a base64-encoded image)
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) { //May be in phyphox root or from a valid translation
                                    if (xpp.getAttributeValue(null, "format") != null && xpp.getAttributeValue(null, "format").equals("base64")) { //Check the icon type
                                        //base64 encoded image. Decode it
                                        icon = xpp.nextText().trim();
                                        try {
                                            Bitmap bitmap = decodeBase64(icon);
                                            image = new BitmapIcon(bitmap, this);
                                        } catch (IllegalArgumentException e) {
                                            Log.e("loadExperimentInfo", "Invalid icon: " + e.getMessage());
                                        }
                                    } else {
                                        //Just a string. Create an icon from it. We allow a maximum of three characters.
                                        icon = xpp.nextText().trim();
                                        if (icon.length() > 3)
                                            icon = icon.substring(0,3);
                                        image = new TextIcon(icon, this);
                                    }

                                }
                                break;
                            case "description": //This should give us the experiment description, but we only need the first line
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    description = xpp.nextText().trim().split("\n", 2)[0]; //Remove any whitespaces and take the first line until the first line break
                                break;
                            case "category": //This should give us the experiment category
                                if (xpp.getDepth() == phyphoxDepth+1 || xpp.getDepth() == translationDepth+1) //May be in phyphox root or from a valid translation
                                    category = xpp.nextText().trim();
                                break;
                            case "input": //We just have to check if there are any sensors, which are not supported on this device
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inInput = true;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inOutput = true;
                                break;
                            case "sensor":
                                if (!inInput || unavailableSensor >= 0)
                                    break;
                                String type = xpp.getAttributeValue(null, "type");
                                sensorInput testSensor;
                                try {
                                    testSensor = new sensorInput(type, 0, false, null, null);
                                    testSensor.attachSensorManager(sensorManager);
                                } catch (sensorInput.SensorException e) {
                                    unavailableSensor = sensorInput.getDescriptionRes(sensorInput.resolveSensorString(type));
                                    break;
                                }
                                if (!testSensor.isAvailable()) {
                                    unavailableSensor = sensorInput.getDescriptionRes(sensorInput.resolveSensorString(type));
                                }
                                break;
                            case "location":
                                if (!inInput || unavailableSensor >= 0)
                                    break;
                                if (!gpsInput.isAvailable(this)) {
                                    unavailableSensor = R.string.location;
                                }
                                break;
                            case "bluetooth":
                                if ((!inInput && !inOutput) || unavailableSensor >= 0) {
                                    break;
                                }
                                String name = xpp.getAttributeValue(null, "name");
                                String uuidStr = xpp.getAttributeValue(null, "uuid");
                                UUID uuid = (uuidStr == null ? null : UUID.fromString(uuidStr));
                                if (name != null && !name.isEmpty()) {
                                    if (bluetoothDeviceNameList != null) {
                                        if (!bluetoothDeviceNameList.containsKey(name))
                                            bluetoothDeviceNameList.put(name, new Vector<String>());
                                        bluetoothDeviceNameList.get(name).add(experimentXML);
                                    }
                                }
                                if (uuid != null) {
                                    if (bluetoothDeviceUUIDList != null) {
                                        if (!bluetoothDeviceUUIDList.containsKey(uuid))
                                            bluetoothDeviceUUIDList.put(uuid, new Vector<String>());
                                        bluetoothDeviceUUIDList.get(uuid).add(experimentXML);
                                    }
                                }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                    unavailableSensor = R.string.bluetooth;
                                } else if (!Bluetooth.isSupported(this)) {
                                    unavailableSensor = R.string.bluetooth;
                                }
                                break;
                        }
                        break;
                    case XmlPullParser.END_TAG: //React to end tags
                        switch (xpp.getName()) {
                            case "phyphox": //We are leaving the phyphox tag
                                if (xpp.getDepth() == phyphoxDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    phyphoxDepth = -1;
                                }
                                break;
                            case "translations": //We are leaving the phyphox tag
                                if (xpp.getDepth() == translationBlockDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    translationBlockDepth = -1;
                                }
                                break;
                            case "translation": //We are leaving the phyphox tag
                                if (xpp.getDepth() == translationDepth) { //Check if we in fact reached the surface. There might have been something else called phyphox within.
                                    translationDepth = -1;
                                }
                                break;
                            case "input":
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inInput = false;
                                break;
                            case "output":
                                if (xpp.getDepth() == phyphoxDepth+1)
                                    inOutput = false;
                                break;
                        }
                        break;

                }
                eventType = xpp.next(); //Next event in the file...
            }

            //Sanity check: We need a title!
            if (title.equals("")) {
                Toast.makeText(this, "Cannot add " + experimentXML + " as it misses a title.", Toast.LENGTH_LONG).show();
                return;
            }

            //Sanity check: We need a category!
            if (category.equals("")) {
                Toast.makeText(this, "Cannot add " + experimentXML + " as it misses a category.", Toast.LENGTH_LONG).show();
                return;
            }

            if (!stateTitle.equals("")) {
                description = title;
                title = stateTitle;
                category = getString(R.string.save_state_category);
            }

            //Let's check the icon
            if (image == null) //No icon given. Create a TextIcon from the first three characters of the title
                image = new TextIcon(title.substring(0, Math.min(title.length(), 3)), this);

            //We have all the information. Add the experiment.
            if (categories != null)
                addExperiment(title, category, image, description, experimentXML, isTemp, isAsset, unavailableSensor, categories);

        } catch (XmlPullParserException e) { //XML Pull Parser is unhappy... Abort and notify user.
            Toast.makeText(this, "Error loading " + experimentXML + " (XML Exception)", Toast.LENGTH_LONG).show();
            Log.e("list:loadExperiment", "Error loading " + experimentXML + " (XML Exception)", e);
        } catch (IOException e) { //IOException... Abort and notify user.
            Toast.makeText(this, "Error loading " + experimentXML + " (IOException)", Toast.LENGTH_LONG).show();
            Log.e("list:loadExperiment", "Error loading " + experimentXML + " (IOException)", e);
        }
    }

    //Load all experiments from assets and from local files
    private void loadExperimentList() {

        //Clear the old list first
        categories.clear();
        bluetoothDeviceNameList.clear();
        bluetoothDeviceUUIDList.clear();
        LinearLayout catList = (LinearLayout)findViewById(R.id.experimentList);
        catList.removeAllViews();

        //Load experiments from local files
        try {
            //Get all files that end on ".phyphox"
            File[] files = getFilesDir().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".phyphox");
                }
            });

            for (File file : files) {
                //Load details for each experiment
                InputStream input = openFileInput(file.getName());
                loadExperimentInfo(input, file.getName(), false, false, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

        //Load experiments from assets
        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments"); //All experiments are placed in the experiments folder
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                if (!experimentXML.endsWith(".phyphox"))
                    continue;
                InputStream input = assetManager.open("experiments/" + experimentXML);
                loadExperimentInfo(input, experimentXML, false,true, categories, null, null);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        Collections.sort(categories, new categoryComparator());

        for (ExperimentsInCategory cat : categories) {
            cat.addToParent(catList);
        }

        //Load hidden bluetooth experiments - these are not shown but will be offered if a matching Bluetooth device is found during a scan
        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments/bluetooth");
            for (String experimentXML : experimentXMLs) {
                //Load details for each experiment
                InputStream input = assetManager.open("experiments/bluetooth/" + experimentXML);
                loadExperimentInfo(input, experimentXML, false,true, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    //If we return to this activity we want to reload the experiment list as other activities may
    //have changed it
    protected void onResume() {
        super.onResume();
        loadExperimentList();
    }

    //This asyncTask extracts a zip file to a temporary directory
    //When it's done, it either opens a single phyphox file or asks the user how to handle multiple phyphox files
    protected static class handleZipIntent extends AsyncTask<String, Void, String> {
        private Intent intent; //The intent to read from
        private WeakReference<ExperimentList> parent;

        //The constructor takes the intent to copy from and the parent activity to call back when finished.
        handleZipIntent(Intent intent, ExperimentList parent) {
            this.intent = intent;
            this.parent = new WeakReference<ExperimentList>(parent);
        }

        //Copying is done on a second thread...
        protected String doInBackground(String... params) {
            phyphoxFile.PhyphoxStream phyphoxStream = phyphoxFile.openXMLInputStream(intent, parent.get());
            if (!phyphoxStream.errorMessage.isEmpty()) {
                return phyphoxStream.errorMessage;
            }

            //Copy the input stream to a random file name
            try {
                //Prepare temporary directory
                File tempPath = new File(parent.get().getFilesDir(), "temp");
                if (!tempPath.exists()) {
                    if (!tempPath.mkdirs())
                        return "Could not create temporary directory to extract zip file.";
                }
                String[] files = tempPath.list();
                for (String file : files) {
                    if (!(new File(tempPath, file).delete()))
                        return "Could not clear temporary directory to extract zip file.";
                }

                ZipInputStream zis = new ZipInputStream(phyphoxStream.inputStream);

                ZipEntry entry;
                byte[] buffer = new byte[2048];
                while((entry = zis.getNextEntry()) != null) {
                    FileOutputStream out = new FileOutputStream(new File(tempPath, entry.getName()));
                    int size = 0;
                    while ((size = zis.read(buffer)) > 0)
                    {
                        out.write(buffer, 0, size);
                    }
                    out.close();
                }
                zis.close();
            } catch (Exception e) {
                return "Error loading zip file: " + e.getMessage();
            }

            return "";
        }

        @Override
        //Call the parent callback when we are done.
        protected void onPostExecute(String result) {
            parent.get().zipReady(result);
        }
    }

    public void zipReady(String result) {
        if (progress != null)
            progress.dismiss();
        if (result.isEmpty()) {
            File tempPath = new File(getFilesDir(), "temp");
            final File[] files = tempPath.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".phyphox");
                }
            });
            if (files.length == 0) {
                Toast.makeText(this, "Error: There is no valid phyphox experiment in this zip file.", Toast.LENGTH_LONG).show();
            } else if (files.length == 1) {
                //Create an intent for this file
                Intent intent = new Intent(this, Experiment.class);
                intent.setData(Uri.fromFile(files[0]));
                intent.setAction(Intent.ACTION_VIEW);

                //Open the file
                startActivity(intent);
            } else {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.open_multipe_dialog, null);
                builder.setView(view)
                        .setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                for (File file : files) {
                                    file.renameTo(new File(getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                                }
                                loadExperimentList();
                                dialog.dismiss();
                            }

                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }

                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                            }
                        });
                AlertDialog dialog = builder.create();

                ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(R.string.open_zip_dialog_instructions);

                LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

                dialog.setTitle(getResources().getString(R.string.open_zip_title));

                Vector<ExperimentsInCategory> zipExperiments = new Vector<>();

                //Load experiments from local files
                for (File file : files) {
                    //Load details for each experiment
                    try {
                        InputStream input = new FileInputStream(file);
                        loadExperimentInfo(input, file.getName(), true, false, zipExperiments, null, null);
                        input.close();
                    } catch (IOException e) {
                        Log.e("zip", e.getMessage());
                        Toast.makeText(this, "Error: Could not load experiment \"" + file + "\" from zip file.", Toast.LENGTH_LONG).show();
                    }
                }

                Collections.sort(zipExperiments, new categoryComparator());

                for (ExperimentsInCategory cat : zipExperiments) {
                    cat.addToParent(catList);
                }

                dialog.show();
            }
        } else {
            Toast.makeText(ExperimentList.this, result, Toast.LENGTH_LONG).show();
        }
    }

    //The BluetoothScanDialog has been written to block execution until a device is found, so we should not run it on the UI thread.
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected class runBluetoothScan extends AsyncTask<String, Void, BluetoothScanDialog.BluetoothDeviceInfo> {
        private WeakReference<ExperimentList> parent;

        //The constructor takes the intent to copy from and the parent activity to call back when finished.
        runBluetoothScan(ExperimentList parent) {
            this.parent = new WeakReference<>(parent);
        }

        //Copying is done on a second thread...
        protected BluetoothScanDialog.BluetoothDeviceInfo doInBackground(String... params) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent.get())) {
                showBluetoothScanError(getResources().getString(R.string.bt_android_version), true, true);
                return null;
            } else {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if (btAdapter == null || !Bluetooth.isEnabled()) {
                    showBluetoothScanError(getResources().getString(R.string.bt_exception_disabled), true, false);
                    return null;
                }
                BluetoothScanDialog bsd = new BluetoothScanDialog(parent.get(), parent.get(), btAdapter);
                return bsd.getBluetoothDevice(null, null, bluetoothDeviceNameList.keySet(), bluetoothDeviceUUIDList.keySet());
            }
        }

        @Override
        //Call the parent callback when we are done.
        protected void onPostExecute(BluetoothScanDialog.BluetoothDeviceInfo result) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (result != null)
                openBluetoothExperiments(result.device, result.uuids, result.phyphoxService);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void loadExperimentFromBluetoothDevice(final BluetoothDevice device) {
        final ExperimentList parent = this;
        currentBluetoothData = null;
        currentBluetoothDataSize = 0;
        currentBluetoothDataIndex = 0;
        currentBluetoothDataActualCRC32.reset();
        final BluetoothGatt gatt = device.connectGatt(this, false, new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:

                        //Find characteristic
                        BluetoothGattService phyphoxService = gatt.getService(Bluetooth.phyphoxServiceUUID);
                        if (phyphoxService == null) {
                            gatt.disconnect();
                            showBluetoothExperimentReadError(res.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + res.getString(R.string.bt_exception_notification_enable) + " (no phyphox service)", device);
                            return;
                        }
                        BluetoothGattCharacteristic experimentCharacteristic = phyphoxService.getCharacteristic(Bluetooth.phyphoxExperimentCharacteristicUUID);
                        if (experimentCharacteristic == null) {
                            gatt.disconnect();
                            showBluetoothExperimentReadError(res.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + res.getString(R.string.bt_exception_notification_enable) + " (no experiment characteristic)", device);
                            return;
                        }

                        //Enable notifications
                        BluetoothGattDescriptor descriptor = experimentCharacteristic.getDescriptor(CONFIG_DESCRIPTOR);
                        if (descriptor == null) {
                            gatt.disconnect();
                            showBluetoothExperimentReadError(res.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + res.getString(R.string.bt_exception_notification_enable) + " (descriptor failed)", device);
                            return;
                        }
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);

                        //From here we can estimate the progress, so let's show a determinate progress dialog instead
                        progress.dismiss();
                        progress = ProgressDialog.show(parent, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), false, true, new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                gatt.disconnect();
                            }
                        });
                        progress.setProgress(0);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        // fall through to default
                    default:
                        progress.dismiss();
                        gatt.close();
                        return;
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] data = characteristic.getValue();
                if (currentBluetoothData == null) {
                    String header = new String(data);
                    if (!header.startsWith("phyphox")) {
                        gatt.disconnect();
                        showBluetoothExperimentReadError(res.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (invalid header)", device);
                    }
                    currentBluetoothDataSize = 0;
                    currentBluetoothDataIndex = 0;
                    for (int i = 0; i < 4; i++) {
                        currentBluetoothDataSize <<= 8;
                        currentBluetoothDataSize |= (data[7+i] & 0xFF);
                    }
                    currentBluetoothDataActualCRC32.reset();
                    currentBluetoothDataCRC32 = 0;
                    for (int i = 0; i < 4; i++) {
                        currentBluetoothDataCRC32 <<= 8;
                        currentBluetoothDataCRC32 |= (data[7+4+i] & 0xFF);
                    }
                    currentBluetoothData = new byte[currentBluetoothDataSize];
                } else {
                    int size = data.length;
                    if (currentBluetoothDataIndex + size > currentBluetoothDataSize) {
                        gatt.disconnect();
                        showBluetoothExperimentReadError(res.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (too large)", device);
                    }
                    System.arraycopy(data, 0, currentBluetoothData, currentBluetoothDataIndex, size);
                    currentBluetoothDataActualCRC32.update(data);
                    currentBluetoothDataIndex += size;

                    if (currentBluetoothDataIndex == currentBluetoothDataSize) {
                        //We are done. Check and use result

                        if (currentBluetoothDataActualCRC32.getValue() != currentBluetoothDataCRC32) {
                            gatt.disconnect();
                            showBluetoothExperimentReadError(res.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (CRC32)", device);
                        }

                        gatt.disconnect();

                        File tempPath = new File(getFilesDir(), "temp_bt");
                        if (!tempPath.exists()) {
                            if (!tempPath.mkdirs()) {
                                gatt.disconnect();
                                showBluetoothExperimentReadError("Could not create temporary directory to write bluetooth experiment file.", device);
                                return;
                            }
                        }
                        String[] files = tempPath.list();
                        for (String file : files) {
                            if (!(new File(tempPath, file).delete())) {
                                gatt.disconnect();
                                showBluetoothExperimentReadError("Could not clear temporary directory to extract bluetooth experiment file.", device);
                                return;
                            }
                        }

                        File zipFile;
                        try {
                            zipFile = new File(tempPath, "bt.zip");
                            FileOutputStream out = new FileOutputStream(zipFile);
                            out.write(currentBluetoothData);
                            out.close();
                        } catch (Exception e) {
                            gatt.disconnect();
                            showBluetoothExperimentReadError("Could not write Bluetooth experiment content to zip file.", device);
                            return;
                        }

                        Intent zipIntent = new Intent(parent, Experiment.class);
                        zipIntent.setData(Uri.fromFile(zipFile));
                        zipIntent.setAction(Intent.ACTION_VIEW);
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        new handleZipIntent(zipIntent, parent).execute();
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.disconnect();
                    showBluetoothExperimentReadError(res.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (could not write)", device);
                }
            }
        });

        progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true, true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                gatt.disconnect();
                progress.dismiss();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        Set<String> experiments = new HashSet<>();
        if (device.getName() != null && bluetoothDeviceNameList.get(device.getName()) != null)
            experiments.addAll(bluetoothDeviceNameList.get(device.getName()));
        for (UUID uuid : uuids) {
            Vector<String> experimentsForUUID = bluetoothDeviceUUIDList.get(uuid);
            if (experimentsForUUID != null)
                experiments.addAll(experimentsForUUID);
        }
        final Set<String> supportedExperiments = experiments;

        if (supportedExperiments.isEmpty() && phyphoxService) {
            //We do not have any experiments for this device, so there is no choice. Just load the experiment provided by the device.
            loadExperimentFromBluetoothDevice(device);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.open_multipe_dialog, null);
        builder.setView(view);
        if (!supportedExperiments.isEmpty()) {
            builder.setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AssetManager assetManager = getAssets();
                    try {
                        for (String file : supportedExperiments) {
                            InputStream in = assetManager.open("experiments/bluetooth/" + file);
                            OutputStream out = new FileOutputStream(new File(getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                            byte[] buffer = new byte[1024];
                            int count;
                            while ((count = in.read(buffer)) != -1) {
                                out.write(buffer, 0, count);
                            }
                            in.close();
                            out.flush();
                            out.close();
                        }
                    } catch (Exception e) {
                        Toast.makeText(ExperimentList.this, "Error: Could not retrieve assets.", Toast.LENGTH_LONG).show();
                    }

                    loadExperimentList();
                    dialog.dismiss();
                    }

                });
        }
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }

            })
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            });

        String instructions = "";
        if (!supportedExperiments.isEmpty()) {
            instructions += res.getString(R.string.open_bluetooth_assets);
        }
        if (!supportedExperiments.isEmpty() && phyphoxService)
            instructions += "\n\n";
        if (phyphoxService) {
            instructions += res.getString(R.string.newExperimentBluetoothLoadFromDeviceInfo);
            builder.setNeutralButton(R.string.newExperimentBluetoothLoadFromDevice, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    loadExperimentFromBluetoothDevice(device);
                    dialog.dismiss();
                }
            });
        }
        AlertDialog dialog = builder.create();

        ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(instructions);

        LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

        dialog.setTitle(getResources().getString(R.string.open_bluetooth_assets_title));

        //Load experiments from assets
        AssetManager assetManager = getAssets();
        Vector<ExperimentsInCategory> bluetoothExperiments = new Vector<>();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/bluetooth/"+file);
                loadExperimentInfo(input, "bluetooth/"+file, true, true, bluetoothExperiments, null, null);
                input.close();
            } catch (IOException e) {
                Log.e("bluetooth", e.getMessage());
                Toast.makeText(this, "Error: Could not load experiment \"" + file + "\" from asset.", Toast.LENGTH_LONG).show();
            }
        }

        Collections.sort(bluetoothExperiments, new categoryComparator());

        for (ExperimentsInCategory cat : bluetoothExperiments) {
            cat.setPreselectedBluetoothAddress(device.getAddress());
            cat.addToParent(catList);
        }

        dialog.show();
    }


    protected void handleIntent(Intent intent) {
        String scheme = intent.getScheme();
        if (scheme == null)
            return;
        boolean isZip = false;
        if (scheme.equals(ContentResolver.SCHEME_FILE) || scheme.equals("phyphox")) {
            if (scheme.equals(ContentResolver.SCHEME_FILE) && !intent.getData().getPath().startsWith(getFilesDir().getPath()) && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Android 6.0: No permission? Request it!
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                //We will stop here. If the user grants the permission, the permission callback will restart the action with the same intent
                return;
            }
            Uri uri = intent.getData();
            isZip = (uri != null && intent.getData().getPath().endsWith(".zip"));
        } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            String type = intent.getType();
            isZip = type != null && type.equals("application/zip");
        }
        if (!isZip) {
            //This is just a single experiment - Start the Experiment activity and let it handle the intent
            Intent forwardedIntent = new Intent(intent);
            forwardedIntent.setClass(this, Experiment.class);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            this.startActivity(forwardedIntent);
        } else {
            //We got a zip-file. Let's see what's inside...
            progress = ProgressDialog.show(this, res.getString(R.string.loadingTitle), res.getString(R.string.loadingText), true);
            new handleZipIntent(intent, this).execute();
        }
    }

    protected void showNewExperimentDialog() {
        newExperimentDialogOpen = true;
        final FloatingActionButton newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        final FloatingActionButton newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        final FloatingActionButton newExperimentBluetooth= (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        final View backgroundDimmer = (View) findViewById(R.id.experimentListDimmer);

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
        backgroundDimmer.startAnimation(fadeDark);

        newExperimentSimple.setClickable(true);
        newExperimentSimpleLabel.setClickable(true);
        newExperimentBluetooth.setClickable(true);
        newExperimentBluetoothLabel.setClickable(true);
        newExperimentQR.setClickable(true);
        newExperimentQRLabel.setClickable(true);
        backgroundDimmer.setClickable(true);
    }

    protected void hideNewExperimentDialog() {
        newExperimentDialogOpen = false;
        final FloatingActionButton newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        final FloatingActionButton newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        final FloatingActionButton newExperimentBluetooth= (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        final View backgroundDimmer = (View) findViewById(R.id.experimentListDimmer);

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
        backgroundDimmer.setClickable(false);

        newExperimentButton.startAnimation(rotate0In);
        newExperimentSimple.startAnimation(fabOut);
        newExperimentSimpleLabel.startAnimation(labelOut);
        newExperimentBluetooth.startAnimation(fabOut);
        newExperimentBluetoothLabel.startAnimation(labelOut);
        newExperimentQR.startAnimation(fabOut);
        newExperimentQRLabel.startAnimation(labelOut);
        backgroundDimmer.startAnimation(fadeTransparent);

    }

    protected void scanQRCode() {
        IntentIntegrator qrScan = new IntentIntegrator(this);

        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        qrScan.setPrompt(getResources().getString(R.string.newExperimentQRscan));
        qrScan.setBeepEnabled(false);
        qrScan.setOrientationLocked(true);

        lockScreen();
        qrScan.initiateScan();
    }

    //This function prevents the screen from rotating. it finds out the current orientation and
    // requests exactly this orientation. Just a "no-sensor"-mode is insufficient as it might revert
    // to the default orientation, but the user shall be able to rotate the screen if the experiment
    // is paused. After that it should stay the way it is...
    private void lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            Display display = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            int rotation = display.getRotation();
            int tempOrientation = this.getResources().getConfiguration().orientation;
            int orientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            switch (tempOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Configuration.ORIENTATION_PORTRAIT:
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                        orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    else
                        orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
            setRequestedOrientation(orientation);
        }
    }

    @Override
    //Callback for premission requests done during the activity. (since Android 6 / Marshmallow)
    //If a new permission has been granted, we will just restart the activity to reload the experiment
    //   with the formerly missing permission
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.recreate();
        }
    }

    protected void showQRScanError(String msg, Boolean isError) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentQRErrorTitle : R.string.newExperimentQR)
                .setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        scanQRCode();
                    }
                })
                .setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    protected void showBluetoothScanError(String msg, Boolean isError, Boolean isFatal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentBluetoothErrorTitle : R.string.newExperimentBluetooth);
        if (!isFatal) {
            builder.setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                scanQRCode();
                }
            });
        }
        builder.setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        })
        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void showBluetoothExperimentReadError(String msg, final BluetoothDevice device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setTitle(R.string.newExperimentBTReadErrorTitle)
                .setPositiveButton(R.string.tryagain, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        loadExperimentFromBluetoothDevice(device);
                    }
                })
                .setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        String textResult;
        if (scanResult != null && (textResult = scanResult.getContents()) != null) {
            if (textResult.toLowerCase().startsWith("http://") || textResult.toLowerCase().startsWith("https://") || textResult.toLowerCase().startsWith("phyphox://")) {
                //This is an URL, open it
                //Create an intent for this new file
                Intent URLintent = new Intent(this, Experiment.class);
                URLintent.setData(Uri.parse("phyphox://" + textResult.split("//", 2)[1]));
                URLintent.setAction(Intent.ACTION_VIEW);
                handleIntent(URLintent);

            } else if (textResult.startsWith("phyphox")) {
                //The QR code contains the experiment itself. The first 13 bytes are:
                // p h y p h o x [crc32] [i] [n]
                //"phyphox" as string (7 bytes)
                //crc32 hash (big endian) of the submitted experiment (has to be the same for each qr code if the experiment is spread across multiple codes)
                //i is the index of this code in a sequence of n code (starting at zero, so i starts at 0 and end with n-1
                //n is the total number of codes for this experiment
                byte[] data = intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_0");
                if (data == null) {
                    Toast.makeText(ExperimentList.this, "Unexpected error: Could not retrieve data from QR code.", Toast.LENGTH_LONG).show();
                    return;
                }
                long crc32 = ((data[7] << 24)&0xff000000 | (data[8] << 16)&0x00ff0000 | (data[9]<< 8)&0x0000ff00 | data[10]&0x000000ff);
                int index = data[11];
                int count = data[12];

                if ((currentQRcrc32 >= 0 && currentQRcrc32 != crc32) || (currentQRsize >= 0 && count != currentQRsize) || (currentQRsize >= 0 && index >= currentQRsize)) {
                    showQRScanError(res.getString(R.string.newExperimentQRcrcMismatch), true);
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

                    CRC32 crc32Received = new CRC32();

                    File zipFile;
                    try {
                        zipFile = new File(tempPath, "qr.zip");
                        FileOutputStream out = new FileOutputStream(zipFile);
                        for (int i = 0; i < currentQRsize; i++) {
                            out.write(currentQRdataPackets[i]);
                            crc32Received.update(currentQRdataPackets[i]);
                        }
                        out.close();
                    } catch (Exception e) {
                        showQRScanError("Could not write QR content to zip file.", true);
                        return;
                    }
                    if (crc32Received.getValue() != crc32) {
                        Log.e("qrscan", "Received CRC32 " + crc32Received.getValue() + " but expected " + crc32);
                        showQRScanError(res.getString(R.string.newExperimentQRBadCRC), true);
                        return;
                    }
                    currentQRsize = -1;
                    currentQRcrc32 = -1;

                    Intent zipIntent = new Intent(this, Experiment.class);
                    zipIntent.setData(Uri.fromFile(zipFile));
                    zipIntent.setAction(Intent.ACTION_VIEW);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    new handleZipIntent(zipIntent, this).execute();
                } else {
                    showQRScanError(res.getString(R.string.newExperimentQRCodesMissing1) + " " + currentQRsize + " " + res.getString(R.string.newExperimentQRCodesMissing2) + " " + missing, false);
                }
            } else {
                //QR code does not contain or reference a phyphox experiment
                showQRScanError(res.getString(R.string.newExperimentQRNoExperiment), true);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    //The onCreate block will setup some onClickListeners and display a do-not-damage-your-phone
    //  warning message.
    protected void onCreate(Bundle savedInstanceState) {

        //Switch from the theme used as splash screen to the theme for the activity
        setTheme(R.style.experimentList);

        //Basics. Call super-constructor and inflate the layout.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_list);

        res = getResources(); //Get Resource reference for easy access.

        displayDoNotDamageYourPhone(); //Show the do-not-damage-your-phone-warning

        //Set the on-click-listener for the credits
        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Context wrapper = new ContextThemeWrapper(ExperimentList.this, R.style.PopupMenuPhyphox);
                PopupMenu popup = new PopupMenu(wrapper, v);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_credits:
                                //Create the credits as an AlertDialog
                                ContextThemeWrapper ctw = new ContextThemeWrapper(ExperimentList.this, R.style.rwth);
                                AlertDialog.Builder credits = new AlertDialog.Builder(ctw);
                                LayoutInflater creditsInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
                                View creditLayout = creditsInflater.inflate(R.layout.credits, null);

                                //Set the credit texts, which require HTML markup
                                TextView tv = (TextView) creditLayout.findViewById(R.id.creditNames);
                                tv.setText(Html.fromHtml(res.getString(R.string.creditsNames)));
                                TextView tvA = (TextView) creditLayout.findViewById(R.id.creditsApache);
                                tvA.setText(Html.fromHtml(res.getString(R.string.creditsApache)));
                                TextView tvB = (TextView) creditLayout.findViewById(R.id.creditsZxing);
                                tvB.setText(Html.fromHtml(res.getString(R.string.creditsZxing)));

                                //Finish alertDialog builder
                                credits.setView(creditLayout);
                                credits.setPositiveButton(res.getText(R.string.close), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        //Nothing to do. Just close the thing.
                                    }
                                });

                                //Present the dialog
                                credits.show();
                                return true;
                            case R.id.action_helpExperiments: {
                                Uri uri = Uri.parse(res.getString(R.string.experimentsPhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            }
                            return true;
                            case R.id.action_helpFAQ: {
                                Uri uri = Uri.parse(res.getString(R.string.faqPhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            }
                            return true;
                            case R.id.action_helpRemote: {
                                Uri uri = Uri.parse(res.getString(R.string.remotePhyphoxOrgURL));
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            }
                            return true;
                            default:
                                return false;
                        }
                    }
                });
                popup.inflate(R.menu.menu_help);
                popup.show();

            }
        };
        ImageView creditsV = (ImageView) findViewById(R.id.credits);
        creditsV.setOnClickListener(ocl);

        //Setup the on-click-listener for the create-new-experiment button
        final ExperimentList thisRef = this; //Context needs to be accessed in the onClickListener

        Button.OnClickListener neocl = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (newExperimentDialogOpen)
                    hideNewExperimentDialog();
                else
                    showNewExperimentDialog();
            }
        };

        final FloatingActionButton newExperimentButton = (FloatingActionButton) findViewById(R.id.newExperiment);
        final View experimentListDimmer = (View) findViewById(R.id.experimentListDimmer);
        newExperimentButton.setOnClickListener(neocl);
        experimentListDimmer.setOnClickListener(neocl);

        Button.OnClickListener neoclSimple = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNewExperimentDialog();
                newExperimentDialog(thisRef);
            }
        };

        final FloatingActionButton newExperimentSimple = (FloatingActionButton) findViewById(R.id.newExperimentSimple);
        final TextView newExperimentSimpleLabel = (TextView) findViewById(R.id.newExperimentSimpleLabel);
        newExperimentSimple.setOnClickListener(neoclSimple);
        newExperimentSimpleLabel.setOnClickListener(neoclSimple);

        Button.OnClickListener neoclBluetooth = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNewExperimentDialog();
                lockScreen();
                (new runBluetoothScan(thisRef)).execute();
            }
        };

        final FloatingActionButton newExperimentBluetooth = (FloatingActionButton) findViewById(R.id.newExperimentBluetooth);
        final TextView newExperimentBluetoothLabel = (TextView) findViewById(R.id.newExperimentBluetoothLabel);
        newExperimentBluetooth.setOnClickListener(neoclBluetooth);
        newExperimentBluetoothLabel.setOnClickListener(neoclBluetooth);

        Button.OnClickListener neoclQR = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNewExperimentDialog();
                scanQRCode();
            }
        };

        final FloatingActionButton newExperimentQR = (FloatingActionButton) findViewById(R.id.newExperimentQR);
        final TextView newExperimentQRLabel = (TextView) findViewById(R.id.newExperimentQRLabel);
        newExperimentQR.setOnClickListener(neoclQR);
        newExperimentQRLabel.setOnClickListener(neoclQR);

        handleIntent(getIntent());

    }

    //Displays a warning message that some experiments might damage the phone
    private void displayDoNotDamageYourPhone() {
        //Use the app theme and create an AlertDialog-builder
        ContextThemeWrapper ctw = new ContextThemeWrapper( this, R.style.phyphox);
        AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
        LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View warningLayout = adbInflater.inflate(R.layout.donotshowagain, null);

        //This reference is used to address a do-not-show-again checkbox within the dialog
        final CheckBox dontShowAgain = (CheckBox) warningLayout.findViewById(R.id.donotshowagain);

        //Setup AlertDialog builder
        adb.setView(warningLayout);
        adb.setTitle(R.string.warning);
        adb.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //User clicked ok. Did the user decide to skip future warnings?
                Boolean skipWarning = false;
                if (dontShowAgain.isChecked())
                    skipWarning = true;

                //Store user decision
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("skipWarning", skipWarning);
                editor.apply();
            }
        });

        //Check preferences if the user does not want to see warnings
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        Boolean skipWarning = settings.getBoolean("skipWarning", false);
        if (!skipWarning)
            adb.show(); //User did not decide to skip, so show it.
    }

    //This displays a rather complex dialog to allow users to set up a simple experiment
    private void newExperimentDialog(final Context c) {
        //Build the dialog with an AlertDialog builder...
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.phyphox);
        AlertDialog.Builder neDialog = new AlertDialog.Builder(ctw);
        LayoutInflater neInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View neLayout = neInflater.inflate(R.layout.new_experiment, null);

        //Get a bunch of references to the dialog elements
        final EditText neTitle = (EditText) neLayout.findViewById(R.id.neTitle); //The edit box for the title of the new experiment
        final EditText neRate = (EditText) neLayout.findViewById(R.id.neRate); //Edit box for the aquisition rate

        //More references: Checkboxes for sensors
        final CheckBox neAccelerometer = (CheckBox) neLayout.findViewById(R.id.neAccelerometer);
        final CheckBox neGyroscope = (CheckBox) neLayout.findViewById(R.id.neGyroscope);
//        final CheckBox neHumidity = (CheckBox) neLayout.findViewById(R.id.neHumidity);
        final CheckBox neLight = (CheckBox) neLayout.findViewById(R.id.neLight);
        final CheckBox neLinearAcceleration = (CheckBox) neLayout.findViewById(R.id.neLinearAcceleration);
        final CheckBox neLocation = (CheckBox) neLayout.findViewById(R.id.neLocation);
        final CheckBox neMagneticField = (CheckBox) neLayout.findViewById(R.id.neMagneticField);
        final CheckBox nePressure = (CheckBox) neLayout.findViewById(R.id.nePressure);
        final CheckBox neProximity = (CheckBox) neLayout.findViewById(R.id.neProximity);
//        final CheckBox neTemperature = (CheckBox) neLayout.findViewById(R.id.neTemperature);

        //Setup the dialog builder...
        neDialog.setView(neLayout);
        neDialog.setTitle(R.string.newExperiment);
        neDialog.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //Here we have to create the experiment definition file
                //This is a lot of tedious work....

                //Prepare the variables from user input

                String title = neTitle.getText().toString(); //Title of the new experiment

                //Prepare the rate
                double rate;
                try {
                    rate = Double.valueOf(neRate.getText().toString());
                } catch (Exception e) {
                    rate = 0;
                    Toast.makeText(ExperimentList.this, "Invaid sensor rate. Fall back to fastest rate.", Toast.LENGTH_LONG).show();
                }

                //Collect the enabled sensors
                boolean acc = neAccelerometer.isChecked();
                boolean gyr = neGyroscope.isChecked();
//                boolean hum = neHumidity.isChecked();
                boolean light = neLight.isChecked();
                boolean lin = neLinearAcceleration.isChecked();
                boolean loc = neLocation.isChecked();
                boolean mag = neMagneticField.isChecked();
                boolean pressure = nePressure.isChecked();
                boolean prox = neProximity.isChecked();
//                boolean temp = neTemperature.isChecked();
                if (!(acc || gyr || light || lin || loc || mag || pressure || prox /*|| hum || temp*/)) {
                    acc = true;
                    Toast.makeText(ExperimentList.this, "No sensor selected. Adding accelerometer as default.", Toast.LENGTH_LONG).show();
                }

                //Generate random file name
                String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";

                //Now write the whole file...
                try {
                    FileOutputStream output = c.openFileOutput(file, MODE_PRIVATE);
                    output.write("<phyphox version=\"1.0\">".getBytes());

                    //Title, standard category and standard description
                    output.write(("<title>"+title.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("&", "&amp;")+"</title>").getBytes());
                    output.write(("<category>"+res.getString(R.string.categoryNewExperiment)+"</category>").getBytes());
                    output.write("<description>Get raw data from selected sensors.</description>".getBytes());

                    //Buffers for all sensors
                    output.write("<data-containers>".getBytes());
                    if (acc) {
                        output.write(("<container size=\"0\">acc_time</container>").getBytes());
                        output.write(("<container size=\"0\">accX</container>").getBytes());
                        output.write(("<container size=\"0\">accY</container>").getBytes());
                        output.write(("<container size=\"0\">accZ</container>").getBytes());
                    }
                    if (gyr) {
                        output.write(("<container size=\"0\">gyr_time</container>").getBytes());
                        output.write(("<container size=\"0\">gyrX</container>").getBytes());
                        output.write(("<container size=\"0\">gyrY</container>").getBytes());
                        output.write(("<container size=\"0\">gyrZ</container>").getBytes());
                    }
/*                    if (hum) {
                        output.write(("<container size=\"0\">hum_time</container>").getBytes());
                        output.write(("<container size=\"0\">hum</container>").getBytes());
                    }
*/
                    if (light) {
                        output.write(("<container size=\"0\">light_time</container>").getBytes());
                        output.write(("<container size=\"0\">light</container>").getBytes());
                    }
                    if (lin) {
                        output.write(("<container size=\"0\">lin_time</container>").getBytes());
                        output.write(("<container size=\"0\">linX</container>").getBytes());
                        output.write(("<container size=\"0\">linY</container>").getBytes());
                        output.write(("<container size=\"0\">linZ</container>").getBytes());
                    }
                    if (loc) {
                        output.write(("<container size=\"0\">loc_time</container>").getBytes());
                        output.write(("<container size=\"0\">locLat</container>").getBytes());
                        output.write(("<container size=\"0\">locLon</container>").getBytes());
                        output.write(("<container size=\"0\">locZ</container>").getBytes());
                        output.write(("<container size=\"0\">locV</container>").getBytes());
                        output.write(("<container size=\"0\">locDir</container>").getBytes());
                        output.write(("<container size=\"0\">locAccuracy</container>").getBytes());
                        output.write(("<container size=\"0\">locZAccuracy</container>").getBytes());
                        output.write(("<container size=\"0\">locStatus</container>").getBytes());
                        output.write(("<container size=\"0\">locSatellites</container>").getBytes());
                    }
                    if (mag) {
                        output.write(("<container size=\"0\">mag_time</container>").getBytes());
                        output.write(("<container size=\"0\">magX</container>").getBytes());
                        output.write(("<container size=\"0\">magY</container>").getBytes());
                        output.write(("<container size=\"0\">magZ</container>").getBytes());
                    }
                    if (pressure) {
                        output.write(("<container size=\"0\">pressure_time</container>").getBytes());
                        output.write(("<container size=\"0\">pressure</container>").getBytes());
                    }
                    if (prox) {
                        output.write(("<container size=\"0\">prox_time</container>").getBytes());
                        output.write(("<container size=\"0\">prox</container>").getBytes());
                    }
/*
                    if (temp) {
                        output.write(("<container size=\"0\">temp_time</container>").getBytes());
                        output.write(("<container size=\"0\">temp</container>").getBytes());
                    }
*/
                    output.write("</data-containers>".getBytes());

                    //Inputs for each sensor
                    output.write("<input>".getBytes());
                    if (acc)
                        output.write(("<sensor type=\"accelerometer\" rate=\"" + rate + "\" ><output component=\"x\">accX</output><output component=\"y\">accY</output><output component=\"z\">accZ</output><output component=\"t\">acc_time</output></sensor>").getBytes());
                    if (gyr)
                        output.write(("<sensor type=\"gyroscope\" rate=\"" + rate + "\" ><output component=\"x\">gyrX</output><output component=\"y\">gyrY</output><output component=\"z\">gyrZ</output><output component=\"t\">gyr_time</output></sensor>").getBytes());
/*
                    if (hum)
                        output.write(("<sensor type=\"humidity\" rate=\"" + rate + "\" ><output component=\"x\">hum</output><output component=\"t\">hum_time</output></sensor>").getBytes());
*/
                    if (light)
                        output.write(("<sensor type=\"light\" rate=\"" + rate + "\" ><output component=\"x\">light</output><output component=\"t\">light_time</output></sensor>").getBytes());
                    if (lin)
                        output.write(("<sensor type=\"linear_acceleration\" rate=\"" + rate + "\" ><output component=\"x\">linX</output><output component=\"y\">linY</output><output component=\"z\">linZ</output><output component=\"t\">lin_time</output></sensor>").getBytes());
                    if (loc)
                        output.write(("<location><output component=\"lat\">locLat</output><output component=\"lon\">locLon</output><output component=\"z\">locZ</output><output component=\"t\">loc_time</output><output component=\"v\">locV</output><output component=\"dir\">locDir</output><output component=\"accuracy\">locAccuracy</output><output component=\"zAccuracy\">locZAccuracy</output><output component=\"status\">locStatus</output><output component=\"satellites\">locSatellites</output></location>").getBytes());
                    if (mag)
                        output.write(("<sensor type=\"magnetic_field\" rate=\"" + rate + "\" ><output component=\"x\">magX</output><output component=\"y\">magY</output><output component=\"z\">magZ</output><output component=\"t\">mag_time</output></sensor>").getBytes());
                    if (pressure)
                        output.write(("<sensor type=\"pressure\" rate=\"" + rate + "\" ><output component=\"x\">pressure</output><output component=\"t\">pressure_time</output></sensor>").getBytes());
                    if (prox)
                        output.write(("<sensor type=\"proximity\" rate=\"" + rate + "\" ><output component=\"x\">prox</output><output component=\"t\">prox_time</output></sensor>").getBytes());
/*
                    if (temp)
                        output.write(("<sensor type=\"temperature\" rate=\"" + rate + "\" ><output component=\"x\">temp</output><output component=\"t\">temp_time</output></sensor>").getBytes());
*/
                    output.write("</input>".getBytes());

                    //Views for each sensor
                    output.write("<views>".getBytes());
                    if (acc) {
                        output.write("<view label=\"Accelerometer\">".getBytes());
                        output.write(("<graph label=\"Acceleration X\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accX</input></graph>").getBytes());
                        output.write(("<graph label=\"Acceleration Y\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accY</input></graph>").getBytes());
                        output.write(("<graph label=\"Acceleration Z\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">acc_time</input><input axis=\"y\">accZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (gyr) {
                        output.write("<view label=\"Gyroscope\">".getBytes());
                        output.write(("<graph label=\"Gyroscope X\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrX</input></graph>").getBytes());
                        output.write(("<graph label=\"Gyroscope Y\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrY</input></graph>").getBytes());
                        output.write(("<graph label=\"Gyroscope Z\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\"><input axis=\"x\">gyr_time</input><input axis=\"y\">gyrZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
/*
                    if (hum) {
                        output.write("<view label=\"Humidity\">".getBytes());
                        output.write(("<graph label=\"Humidity\" labelX=\"t (s)\" labelY=\"Relative Humidity (%)\" partialUpdate=\"true\"><input axis=\"x\">hum_time</input><input axis=\"y\">hum</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
*/
                    if (light) {
                        output.write("<view label=\"Light\">".getBytes());
                        output.write(("<graph label=\"Illuminance\" labelX=\"t (s)\" labelY=\"Ev (lx)\" partialUpdate=\"true\"><input axis=\"x\">light_time</input><input axis=\"y\">light</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (lin) {
                        output.write("<view label=\"Linear Acceleration\">".getBytes());
                        output.write(("<graph label=\"Linear Acceleration X\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linX</input></graph>").getBytes());
                        output.write(("<graph label=\"Linear Acceleration Y\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linY</input></graph>").getBytes());
                        output.write(("<graph label=\"Linear Acceleration Z\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\"><input axis=\"x\">lin_time</input><input axis=\"y\">linZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (loc) {
                        output.write("<view label=\"Location\">".getBytes());
                        output.write(("<graph label=\"Latitude\" labelX=\"t (s)\" labelY=\"Latitude (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locLat</input></graph>").getBytes());
                        output.write(("<graph label=\"Longitude\" labelX=\"t (s)\" labelY=\"Longitude (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locLon</input></graph>").getBytes());
                        output.write(("<graph label=\"Height\" labelX=\"t (s)\" labelY=\"z (m)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locZ</input></graph>").getBytes());
                        output.write(("<graph label=\"Velocity\" labelX=\"t (s)\" labelY=\"v (m/s)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locV</input></graph>").getBytes());
                        output.write(("<graph label=\"Direction\" labelX=\"t (s)\" labelY=\"heading (°)\" partialUpdate=\"true\"><input axis=\"x\">loc_time</input><input axis=\"y\">locDir</input></graph>").getBytes());
                        output.write(("<value label=\"Horizontal Accuracy\" size=\"1\" precision=\"1\" unit=\"m\"><input>locAccuracy</input></value>").getBytes());
                        output.write(("<value label=\"Vertical Accuracy\" size=\"1\" precision=\"1\" unit=\"m\"><input>locZAccuracy</input></value>").getBytes());
                        output.write(("<value label=\"Satellites\" size=\"1\" precision=\"0\"><input>locSatellites</input></value>").getBytes());
                        output.write(("<value label=\"Status\" size=\"1\" precision=\"0\"><input>locStatus</input><map max=\"-1\">GPS disabled</map><map max=\"0\">Waiting for signal</map><map max=\"1\">Active</map></value>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (mag) {
                        output.write("<view label=\"Magnetometer\">".getBytes());
                        output.write(("<graph label=\"Magnetic field X\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magX</input></graph>").getBytes());
                        output.write(("<graph label=\"Magnetic field Y\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magY</input></graph>").getBytes());
                        output.write(("<graph label=\"Magnetic field Z\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\"><input axis=\"x\">mag_time</input><input axis=\"y\">magZ</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (pressure) {
                        output.write("<view label=\"Pressure\">".getBytes());
                        output.write(("<graph label=\"Pressure\" labelX=\"t (s)\" labelY=\"P (hPa)\" partialUpdate=\"true\"><input axis=\"x\">pressure_time</input><input axis=\"y\">pressure</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
                    if (prox) {
                        output.write("<view label=\"Proximity\">".getBytes());
                        output.write(("<graph label=\"Proximity\" labelX=\"t (s)\" labelY=\"Distance (cm)\" partialUpdate=\"true\"><input axis=\"x\">prox_time</input><input axis=\"y\">prox</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
/*
                    if (temp) {
                        output.write("<view label=\"Temperature\">".getBytes());
                        output.write(("<graph label=\"Temperature\" labelX=\"t (s)\" labelY=\"Temperature (°C)\" partialUpdate=\"true\"><input axis=\"x\">temp_time</input><input axis=\"y\">temp</input></graph>").getBytes());
                        output.write("</view>".getBytes());
                    }
*/
                    output.write("</views>".getBytes());

                    //Export definitions for each sensor
                    output.write("<export>".getBytes());
                    if (acc) {
                        output.write("<set name=\"Accelerometer\">".getBytes());
                        output.write("<data name=\"Time (s)\">acc_time</data>".getBytes());
                        output.write("<data name=\"Acceleration x (m/s^2)\">accX</data>".getBytes());
                        output.write("<data name=\"Acceleration y (m/s^2)\">accY</data>".getBytes());
                        output.write("<data name=\"Acceleration z (m/s^2)\">accZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (gyr) {
                        output.write("<set name=\"Gyroscope\">".getBytes());
                        output.write("<data name=\"Time (s)\">gyr_time</data>".getBytes());
                        output.write("<data name=\"Gyroscope x (rad/s)\">gyrX</data>".getBytes());
                        output.write("<data name=\"Gyroscope y (rad/s)\">gyrY</data>".getBytes());
                        output.write("<data name=\"Gyroscope z (rad/s)\">gyrZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
/*
                    if (hum) {
                        output.write("<set name=\"Humidity\">".getBytes());
                        output.write("<data name=\"Time (s)\">hum_time</data>".getBytes());
                        output.write("<data name=\"Relative Humidity (%)\">hum</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
*/
                    if (light) {
                        output.write("<set name=\"Light\">".getBytes());
                        output.write("<data name=\"Time (s)\">light_time</data>".getBytes());
                        output.write("<data name=\"Illuminance (lx)\">light</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (lin) {
                        output.write("<set name=\"Linear Acceleration\">".getBytes());
                        output.write("<data name=\"Time (s)\">lin_time</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration x (m/s^2)\">linX</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration y (m/s^2)\">linY</data>".getBytes());
                        output.write("<data name=\"Linear Acceleration z (m/s^2)\">linZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (loc) {
                        output.write("<set name=\"Location\">".getBytes());
                        output.write("<data name=\"Time (s)\">loc_time</data>".getBytes());
                        output.write("<data name=\"Latitude (°)\">locLat</data>".getBytes());
                        output.write("<data name=\"Longitude (°)\">locLon</data>".getBytes());
                        output.write("<data name=\"Height (m)\">locZ</data>".getBytes());
                        output.write("<data name=\"Velocity (m/s)\">locV</data>".getBytes());
                        output.write("<data name=\"Direction (°)\">locDir</data>".getBytes());
                        output.write("<data name=\"Horizontal Accuracy (m)\">locAccuracy</data>".getBytes());
                        output.write("<data name=\"Vertical Accuracy (m)\">locZAccuracy</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (mag) {
                        output.write("<set name=\"Magnetometer\">".getBytes());
                        output.write("<data name=\"Time (s)\">mag_time</data>".getBytes());
                        output.write("<data name=\"Magnetic field x (µT)\">magX</data>".getBytes());
                        output.write("<data name=\"Magnetic field y (µT)\">magY</data>".getBytes());
                        output.write("<data name=\"Magnetic field z (µT)\">magZ</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (pressure) {
                        output.write("<set name=\"Pressure\">".getBytes());
                        output.write("<data name=\"Time (s)\">pressure_time</data>".getBytes());
                        output.write("<data name=\"Pressure (hPa)\">pressure</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
                    if (prox) {
                        output.write("<set name=\"Proximity\">".getBytes());
                        output.write("<data name=\"Time (s)\">prox_time</data>".getBytes());
                        output.write("<data name=\"Distance (cm)\">prox</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
/*
                    if (temp) {
                        output.write("<set name=\"Temperature\">".getBytes());
                        output.write("<data name=\"Time (s)\">temp_time</data>".getBytes());
                        output.write("<data name=\"Temperature (°C)\">temp</data>".getBytes());
                        output.write("</set>".getBytes());
                    }
*/
                    output.write("</export>".getBytes());

                    //And finally, the closing tag
                    output.write("</phyphox>".getBytes());

                    output.close();

                    //Create an intent for this new file
                    Intent intent = new Intent(c, Experiment.class);
                    intent.putExtra(EXPERIMENT_XML, file);
                    intent.putExtra(EXPERIMENT_ISTEMP, false);
                    intent.putExtra(EXPERIMENT_ISASSET, false);
                    intent.setAction(Intent.ACTION_VIEW);

                    //Start the new experiment
                    c.startActivity(intent);
                } catch (Exception e) {
                    Log.e("newExperiment", "Could not create new experiment.", e);
                }
            }
        });
        neDialog.setNegativeButton(res.getText(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                //If the user aborts the dialog, we don't have to do anything
            }
        });

        //Finally, show the dialog
        neDialog.show();
    }

}
