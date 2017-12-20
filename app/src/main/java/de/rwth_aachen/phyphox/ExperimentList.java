package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.ActivityOptions;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

//ExperimentList implements the activity which lists all experiments to the user. This is the start
//activity for this app if it is launched without an intent.

public class ExperimentList extends AppCompatActivity {

    //Strings which define extra information for intents starting an experiment from local files
    public final static String EXPERIMENT_XML = "com.dicon.phyphox.EXPERIMENT_XML";
    public final static String EXPERIMENT_ISASSET = "com.dicon.phyphox.EXPERIMENT_ISASSET";
    public final static String EXPERIMENT_SENSORREADY = "com.dicon.phyphox.EXPERIMENT_SENSORREADY";
    public final static String EXPERIMENT_UNAVAILABLESENSOR = "com.dicon.phyphox.EXPERIMENT_UNAVAILABLESENSOR";

    //String constant to identify our preferences
    public static final String PREFS_NAME = "phyphox";

    //A resource reference for easy access
    private Resources res;

    private Vector<category> categories = new Vector<>(); //The list of categories. The category class (see below) holds a category and all its experiment items

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
    private class experimentItemAdapter extends BaseAdapter {
        final private Activity parentActivity; //Reference to the main activity for the alertDialog when deleting files

        //Experiment data
        Vector<Drawable> icons = new Vector<>(); //List of icons for each experiment
        Vector<String> titles = new Vector<>(); //List of titles for each experiment
        Vector<String> infos = new Vector<>(); //List of short descriptions for each experiment
        Vector<String> xmlFiles = new Vector<>(); //List of xmlFile name for each experiment (has to be provided in the intent if the user wants to load this)
        Vector<Boolean> isAssetList = new Vector<>(); //List of booleans for each experiment, which track whether the file is an asset or stored loacally (has to be provided in the intent if the user wants to load this)
        Vector<Integer> unavailableSensorList = new Vector<>(); //List of strings for each experiment, which give the name of the unavailable sensor if sensorReady is false

        //The constructor takes the activity reference. That's all.
        public experimentItemAdapter(Activity parentActivity) {
            this.parentActivity = parentActivity;
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
            intent.putExtra(EXPERIMENT_ISASSET, isAssetList.get(position));
            intent.putExtra(EXPERIMENT_UNAVAILABLESENSOR, unavailableSensorList.get(position));
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
        public void addExperiment(Drawable icon, String title, String info, String xmlFile, boolean isAsset, Integer unavailableSensor) {
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
            isAssetList.insertElementAt(isAsset, i);
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
            if (isAssetList.get(position))
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
    private class category {
        private Context parentContext; //Needed to create views
        public String name; //Category name (headline)
        private LinearLayout catLayout; //This is the base layout of the category, which will contain the headline and the gridView showing all the experiments
        private TextView categoryHeadline; //The TextView to display the headline
        private ExpandableHeightGridView experimentSubList; //The gridView holding experiment items. (See implementation below for the custom flavor "ExpandableHeightGridView")
        private experimentItemAdapter experiments; //Instance of the adapter to fill the gridView (implementation above)

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
        public category(String name, Activity parentActivity) {
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
            experiments = new experimentItemAdapter(parentActivity);
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

        public void addToParent(LinearLayout parentLayout) {
            //Add the layout to the layout designated by the caller
            parentLayout.addView(catLayout);
        }

        //Wrapper to add an experiment to this category. This just hands it over to the adapter.
        public void addExperiment(String exp, Drawable image, String description, final String xmlFile, boolean isAsset, Integer unavailableSensor) {
            experiments.addExperiment(image, exp, description, xmlFile, isAsset, unavailableSensor);
        }

        //Helper to check if the name of this category matches a given string
        public boolean hasName(String cat) {
            return cat.equals(name);
        }
    }

    class categoryComparator implements Comparator<category> {
        public int compare(category a, category b) {
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
    //experimentItemAdapter.addExperiment(...) is called by category.addExperiment(...), which in
    //turn will be called here.
    //This addExperiment(...) is called for each experiment found. It checks if the experiment's
    // category already exists and adds it to this category or creates a category for the experiment
    private void addExperiment(String exp, String cat, Drawable image, String description, String xmlFile, boolean isAsset, Integer unavailableSensor) {
        //Check all categories for the category of the new experiment
        for (category icat : categories) {
            if (icat.hasName(cat)) {
                //Found it. Add the experiment and return
                icat.addExperiment(exp, image, description, xmlFile, isAsset, unavailableSensor);
                return;
            }
        }
        //Category does not yet exist. Create it and add the experiment
        categories.add(new category(cat, this));
        categories.lastElement().addExperiment(exp, image, description, xmlFile, isAsset, unavailableSensor);
    }

    //Decode the experiment icon (base64) and return a bitmap
    public static Bitmap decodeBase64(String input) throws IllegalArgumentException {
        byte[] decodedByte = Base64.decode(input, 0); //Decode the base64 data to binary
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length); //Interpret the binary data and return the bitmap
    }

    //Minimalistic loading function. This only retrieves the data necessary to list the experiment.
    private void loadExperimentInfo(InputStream input, String experimentXML, boolean isAsset) {
        XmlPullParser xpp;
        try { //A lot of stuff can go wrong here. Let's catch any xml problem.
            //Prepare the PullParser
            xpp = Xml.newPullParser();
            xpp.setInput(input, "UTF-8");
        } catch (XmlPullParserException e) {
            Toast.makeText(this, "Cannot open " + experimentXML + " as it misses a title.", Toast.LENGTH_LONG).show();
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
                                if (!Bluetooth.isSupported(this)) {
                                    unavailableSensor = R.string.bluetooth;
                                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                    unavailableSensor = R.string.bluetooth; // TODO ?
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
            addExperiment(title, category, image, description, experimentXML, isAsset, unavailableSensor);

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
                loadExperimentInfo(input, file.getName(), false);
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
                InputStream input = assetManager.open("experiments/" + experimentXML);
                loadExperimentInfo(input, experimentXML, true);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

        Collections.sort(categories, new categoryComparator());

        for (category cat : categories) {
            cat.addToParent(catList);
        }
    }

    @Override
    //If we return to this activity we want to reload the experiment list as other activities may
    //have changed it
    protected void onResume() {
        super.onResume();
        loadExperimentList();
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
        final Context c = this; //Context needs to be accessed in the onClickListener
        Button.OnClickListener neocl = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                newExperimentDialog(c);
            }
        };

        final FloatingActionButton newExperimentB = (FloatingActionButton) findViewById(R.id.newExperiment);
        newExperimentB.setOnClickListener(neocl);

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
