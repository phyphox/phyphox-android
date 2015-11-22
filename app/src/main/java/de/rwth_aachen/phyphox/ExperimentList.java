package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.Vector;

//TODO clean-up and comment ExperimentList class

public class ExperimentList extends AppCompatActivity {

    public final static String EXPERIMENT_XML = "com.dicon.phyphox.EXPERIMENT_XML";
    public final static String EXPERIMENT_ISASSET = "com.dicon.phyphox.EXPERIMENT_ISASSET";
    public static final String PREFS_NAME = "phyphox";
    public CheckBox dontShowAgain;

    Resources res;

    public class TextIcon extends Drawable {

        private final String text;
        private final Paint paint;
        private final Paint paintBG;

        public TextIcon(String text, Context c) {

            this.text = text;

            this.paint = new Paint();
            paint.setColor(ContextCompat.getColor(c, R.color.main));
            paint.setTextSize(res.getDimension(R.dimen.expElementIconSize)*0.5f);
            paint.setAntiAlias(true);
            paint.setFakeBoldText(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);

            this.paintBG = new Paint();
            paintBG.setColor(ContextCompat.getColor(c, R.color.highlight));
            paintBG.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRect(new Rect(0, 0, (int)res.getDimension(R.dimen.expElementIconSize), (int)res.getDimension(R.dimen.expElementIconSize)), paintBG);
            canvas.drawText(text, (int)res.getDimension(R.dimen.expElementIconSize)/2, (int)res.getDimension(R.dimen.expElementIconSize)*2/3, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private class experimentItemAdapter extends BaseAdapter {
        final private Activity parentActivity;
        Vector<Drawable> icons = new Vector<>();
        Vector<String> titles = new Vector<>();
        Vector<String> infos = new Vector<>();
        Vector<String> xmlFiles = new Vector<>();
        Vector<Boolean> isAssetList = new Vector<>();

        public experimentItemAdapter(Activity parentActivity) {
            this.parentActivity = parentActivity;
        }

        public int getCount() {
            return icons.size();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        public void start(int position, View v) {
            Intent intent = new Intent(v.getContext(), Experiment.class);
            intent.putExtra(EXPERIMENT_XML, xmlFiles.get(position));
            intent.putExtra(EXPERIMENT_ISASSET, isAssetList.get(position));
            intent.setAction(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityOptions options = ActivityOptions.makeScaleUpAnimation(v, 0,
                        0, v.getWidth(), v.getHeight());
                v.getContext().startActivity(intent, options.toBundle());
            } else {
                v.getContext().startActivity(intent);
            }
        }

        public void addExperiment(Drawable icon, String title, String info, String xmlFile, boolean isAsset) {
            int i;
            for (i = 0; i < titles.size(); i++) {
                if (titles.get(i).compareTo(title) >= 0)
                    break;
            }
            icons.insertElementAt(icon, i);
            titles.insertElementAt(title, i);
            infos.insertElementAt(info, i);
            xmlFiles.insertElementAt(xmlFile, i);
            isAssetList.insertElementAt(isAsset, i);
            this.notifyDataSetChanged();
        }

        public class Holder {
            ImageView icon;
            TextView title;
            TextView info;
            ImageButton deleteBtn;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            Holder holder;
            if(convertView == null) {
                holder = new Holder();
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.experiment_item, null);
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        start(position, v);
                    }
                });
                holder.icon = (ImageView) convertView.findViewById(R.id.expIcon);
                holder.title = (TextView) convertView.findViewById(R.id.expTitle);
                holder.info = (TextView) convertView.findViewById(R.id.expInfo);
                holder.deleteBtn = (ImageButton) convertView.findViewById(R.id.deleteButton);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }
            holder.icon.setImageDrawable(icons.get(position));
            holder.title.setText(titles.get(position));
            holder.info.setText(infos.get(position));
            if (isAssetList.get(position))
                holder.deleteBtn.setVisibility(ImageView.GONE);
            else {
                holder.deleteBtn.setVisibility(ImageView.VISIBLE);
                holder.deleteBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage(res.getString(R.string.confirmDelete))
                                .setTitle(R.string.confirmDeleteTitle)
                                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        deleteFile(xmlFiles.get(position));
                                        loadExperimentList();
                                    }
                                })
                                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {

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

    private class category {
        private Context parentContext;
        public String name;
        private LinearLayout catLayout;
        private ExpandableHeightGridView experimentSubList;
        private TextView categoryHeadline;
        private experimentItemAdapter experiments;

        private class ExpandableHeightGridView extends GridView
        {

            boolean expanded = false;

            public ExpandableHeightGridView(Context context)
            {
                super(context);
            }

            public ExpandableHeightGridView(Context context, AttributeSet attrs)
            {
                super(context, attrs);
            }

            public ExpandableHeightGridView(Context context, AttributeSet attrs,
                                            int defStyle) {
                super(context, attrs, defStyle);
            }

            public boolean isExpanded()
            {
                return expanded;
            }

            @Override
            public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
            {
                // HACK! TAKE THAT ANDROID!
                if (isExpanded())
                {
                    // Calculate entire height by providing a very large height hint.
                    // View.MEASURED_SIZE_MASK represents the largest height possible.
                    int expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK,
                            MeasureSpec.AT_MOST);
                    super.onMeasure(widthMeasureSpec, expandSpec);

                    ViewGroup.LayoutParams params = getLayoutParams();
                    params.height = getMeasuredHeight();
                }
                else
                {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }

            public void setExpanded(boolean expanded) {
                this.expanded = expanded;
            }
        }

        public category(String name, LinearLayout parentLayout, Activity parentActivity) {
            this.name = name;
            parentContext = parentActivity;
            catLayout = new LinearLayout(parentContext);
            catLayout.setOrientation(LinearLayout.VERTICAL);
            catLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            categoryHeadline = new TextView(parentContext);
            categoryHeadline.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            categoryHeadline.setText(name);
            categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.headline_font));
            categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
            categoryHeadline.setBackgroundColor(ContextCompat.getColor(parentContext, R.color.highlight));
            categoryHeadline.setTextColor(ContextCompat.getColor(parentContext, R.color.main));
            categoryHeadline.setPadding(res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10, res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10);

            experimentSubList = new ExpandableHeightGridView(parentContext);
            experimentSubList.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            experimentSubList.setColumnWidth(res.getDimensionPixelOffset(R.dimen.expElementWidth));
            experimentSubList.setNumColumns(ExpandableHeightGridView.AUTO_FIT);
            experimentSubList.setStretchMode(ExpandableHeightGridView.STRETCH_COLUMN_WIDTH);
            experimentSubList.setExpanded(true);
            experimentSubList.setPadding(0, 0, 0, res.getDimensionPixelOffset(R.dimen.activity_vertical_margin));

            experiments = new experimentItemAdapter(parentActivity);
            experimentSubList.setAdapter(experiments);

            catLayout.addView(categoryHeadline);
            catLayout.addView(experimentSubList);

            parentLayout.addView(catLayout);
        }


        public void addExperiment(String exp, Drawable image, String description, final String xmlFile, boolean isAsset) {
            experiments.addExperiment(image, exp, description, xmlFile, isAsset);
        }

        public boolean hasName(String cat) {
            return cat.equals(name);
        }
    }

    private Vector<category> categories = new Vector<>();

    private void addExperiment(String exp, String cat, Drawable image, String description, String xmlFile, boolean isAsset) {
        for (category icat : categories) {
            if (icat.hasName(cat)) {
                icat.addExperiment(exp, image, description, xmlFile, isAsset);
                return;
            }
        }
        LinearLayout catList = (LinearLayout)findViewById(R.id.experimentList);
        categories.add(new category(cat, catList, this));
        categories.lastElement().addExperiment(exp, image, description, xmlFile, isAsset);
    }

    public static Bitmap decodeBase64(String input) {
        byte[] decodedByte = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
    }

    private void loadExperiment(InputStream input, String experimentXML, boolean isAsset) {
        boolean hidden = false;
        try {
            XmlPullParser xpp = Xml.newPullParser();
            xpp.setInput(input, "UTF-8");

            int eventType = xpp.getEventType();
            String title = "";
            String category = "";
            String icon = "";
            String description = "";
            while (eventType != XmlPullParser.END_DOCUMENT){
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        switch (xpp.getName()) {
                            case "title":
                                title = xpp.nextText();
                                break;
                            case "icon":
                                icon = xpp.nextText().trim();
                                break;
                            case "description":
                                description = xpp.nextText().trim();
                                break;
                            case "category":
                                if (xpp.getAttributeValue(null, "hidden") != null && xpp.getAttributeValue(null, "hidden").equals("true")) {
                                    hidden = true;
                                }
                                category = xpp.nextText();
                                break;
                        }
                        break;
                }
                eventType = xpp.next();
            }
            if (title.equals("")) {
                Toast.makeText(this, "Cannot add " + experimentXML + " as it misses a title.", Toast.LENGTH_LONG).show();
            } else if (category.equals("")) {
                Toast.makeText(this, "Cannot add " + experimentXML + " as it misses a category.", Toast.LENGTH_LONG).show();
            } else if (!hidden) {
                Drawable image;
                if (icon.equals(""))
                    image = new TextIcon(title.substring(0, 3), this);
                else if (icon.length() <= 3)
                    image = new TextIcon(icon, this);
                else
                    image = new BitmapDrawable(res, decodeBase64(icon));
                addExperiment(title, category, image, description, experimentXML, isAsset);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading " + experimentXML, Toast.LENGTH_LONG).show();
        }
    }

    private void loadExperimentList() {
        categories.clear();
        ((LinearLayout)findViewById(R.id.experimentList)).removeAllViews();
        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments");
            for (String experimentXML : experimentXMLs) {
                InputStream input = assetManager.open("experiments/" + experimentXML);
                loadExperiment(input, experimentXML, true);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

        try {
            File[] files = getFilesDir().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".phyphox");
                }
            });

            for (File file : files) {
                InputStream input = openFileInput(file.getName());
                loadExperiment(input, file.getName(), false);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadExperimentList();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_list);

        res = getResources();

        ContextThemeWrapper ctw = new ContextThemeWrapper( this, R.style.AppTheme);
        AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
        LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View warningLayout = adbInflater.inflate(R.layout.donotshowagain, null);
        dontShowAgain = (CheckBox) warningLayout.findViewById(R.id.donotshowagain);
        adb.setView(warningLayout);
        adb.setTitle(R.string.warning);
        adb.setMessage(R.string.damageWarning);
        adb.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Boolean skipWarning = false;
                if (dontShowAgain.isChecked())
                    skipWarning = true;
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("skipWarning", skipWarning);
                editor.apply();
            }
        });

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        Boolean skipWarning = settings.getBoolean("skipWarning", false);
        if (!skipWarning)
            adb.show();

        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder credits = new AlertDialog.Builder(ExperimentList.this);
                LayoutInflater creditsInflater = LayoutInflater.from(ExperimentList.this);
                View creditLayout = creditsInflater.inflate(R.layout.credits, null);
                TextView tv = (TextView) creditLayout.findViewById(R.id.creditNames);
                tv.setText(Html.fromHtml(res.getString(R.string.creditsNames)));
                TextView tvA = (TextView) creditLayout.findViewById(R.id.creditsApache);
                tvA.setText(Html.fromHtml(res.getString(R.string.creditsApache)));
                credits.setView(creditLayout);
                credits.setTitle(R.string.credits);
                credits.setPositiveButton(res.getText(R.string.close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                credits.show();
            }
        };

        ImageView creditsV = (ImageView) findViewById(R.id.credits);
        creditsV.setOnClickListener(ocl);

        final Context c = this;

        Button.OnClickListener neocl = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder neDialog = new AlertDialog.Builder(ExperimentList.this);
                LayoutInflater neInflater = LayoutInflater.from(ExperimentList.this);
                View neLayout = neInflater.inflate(R.layout.new_experiment, null);
                final EditText neTitle = (EditText) neLayout.findViewById(R.id.neTitle);
                final EditText neBuffer = (EditText) neLayout.findViewById(R.id.neBuffer);
                final SeekBar neRate = (SeekBar) neLayout.findViewById(R.id.neRate);
                final CheckBox neAccelerometer = (CheckBox) neLayout.findViewById(R.id.neAccelerometer);
                final CheckBox neGyroscope = (CheckBox) neLayout.findViewById(R.id.neGyroscope);
                final CheckBox neLight = (CheckBox) neLayout.findViewById(R.id.neLight);
                final CheckBox neLinearAcceleration = (CheckBox) neLayout.findViewById(R.id.neLinearAcceleration);
                final CheckBox neMagneticField = (CheckBox) neLayout.findViewById(R.id.neMagneticField);
                final CheckBox nePressure = (CheckBox) neLayout.findViewById(R.id.nePressure);
                neDialog.setView(neLayout);
                neDialog.setTitle(R.string.newExperiment);
                neDialog.setPositiveButton(res.getText(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String title = neTitle.getText().toString();
                        int bufferSize;
                        try {
                            bufferSize = Integer.valueOf(neBuffer.getText().toString());
                        } catch (Exception e) {
                            bufferSize = 0;
                        }
                        if (bufferSize <= 0 || bufferSize > 1e6) {
                            bufferSize = 500;
                            Toast.makeText(ExperimentList.this, "Invaid buffer size. Set to default of 500.", Toast.LENGTH_LONG).show();
                        }
                        String rate;
                        switch(neRate.getProgress()) {
                            case 0: rate = "ui";
                                break;
                            case 1: rate = "normal";
                                break;
                            case 2: rate = "game";
                                break;
                            default: rate = "fastest";
                                break;
                        }
                        boolean acc = neAccelerometer.isChecked();
                        boolean gyr = neGyroscope.isChecked();
                        boolean light = neLight.isChecked();
                        boolean lin = neLinearAcceleration.isChecked();
                        boolean mag = neMagneticField.isChecked();
                        boolean pressure = nePressure.isChecked();
                        if (!(acc || gyr || light || lin || mag || pressure)) {
                            acc = true;
                            Toast.makeText(ExperimentList.this, "No sensor selected. Adding accelerometer as default.", Toast.LENGTH_LONG).show();
                        }

                        String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";
                        try {
                            FileOutputStream output = c.openFileOutput(file, MODE_PRIVATE);
                            output.write("<phyphox version=\"1.0\">".getBytes());
                            output.write(("<title>"+title.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("&", "&amp;")+"</title>").getBytes());
                            output.write("<category>Raw Sensors</category>".getBytes());
                            output.write("<description>Get raw data from selected sensors.</description>".getBytes());
                            output.write("<input>".getBytes());
                            if (acc)
                                output.write(("<sensor type=\"accelerometer\" outputX=\"accX\" outputY=\"accY\" outputZ=\"accZ\" outputT=\"acc_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            if (gyr)
                                output.write(("<sensor type=\"gyroscope\" outputX=\"gyrX\" outputY=\"gyrY\" outputZ=\"gyrZ\" outputT=\"gyr_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            if (light)
                                output.write(("<sensor type=\"light\" outputX=\"light\" outputT=\"light_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            if (lin)
                                output.write(("<sensor type=\"linear_acceleration\" outputX=\"linX\" outputY=\"linY\" outputZ=\"linZ\" outputT=\"lin_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            if (mag)
                                output.write(("<sensor type=\"magnetic_field\" outputX=\"magX\" outputY=\"magY\" outputZ=\"magZ\" outputT=\"mag_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            if (pressure)
                                output.write(("<sensor type=\"pressure\" outputX=\"pressure\" outputT=\"pressure_time\" buffer=\"" + String.valueOf(bufferSize) + "\" rate=\"" + rate + "\" />").getBytes());
                            output.write("</input>".getBytes());
                            output.write("<views>".getBytes());
                            if (acc) {
                                output.write("<view name=\"Accelerometer\">".getBytes());
                                output.write(("<graph label=\"Acceleration X\" inputX=\"acc_time\" inputY=\"accX\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Acceleration Y\" inputX=\"acc_time\" inputY=\"accY\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Acceleration Z\" inputX=\"acc_time\" inputY=\"accZ\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            if (gyr) {
                                output.write("<view name=\"Gyroscope\">".getBytes());
                                output.write(("<graph label=\"Gyroscope X\" inputX=\"gyr_time\" inputY=\"gyrX\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Gyroscope Y\" inputX=\"gyr_time\" inputY=\"gyrY\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Gyroscope Z\" inputX=\"gyr_time\" inputY=\"gyrZ\" labelX=\"t (s)\" labelY=\"w (rad/s)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            if (light) {
                                output.write("<view name=\"Light\">".getBytes());
                                output.write(("<graph label=\"Illumination\" inputX=\"light_time\" inputY=\"light\" labelX=\"t (s)\" labelY=\"Ev (lx)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            if (lin) {
                                output.write("<view name=\"Linear Acceleration\">".getBytes());
                                output.write(("<graph label=\"Linear Acceleration X\" inputX=\"lin_time\" inputY=\"linX\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Linear Acceleration Y\" inputX=\"lin_time\" inputY=\"linY\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Linear Acceleration Z\" inputX=\"lin_time\" inputY=\"linZ\" labelX=\"t (s)\" labelY=\"a (m/s²)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            if (mag) {
                                output.write("<view name=\"Magnetometer\">".getBytes());
                                output.write(("<graph label=\"Magnetic field X\" inputX=\"mag_time\" inputY=\"magX\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Magnetic field Y\" inputX=\"mag_time\" inputY=\"magY\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\" />").getBytes());
                                output.write(("<graph label=\"Magnetic field Z\" inputX=\"mag_time\" inputY=\"magZ\" labelX=\"t (s)\" labelY=\"B (µT)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            if (pressure) {
                                output.write("<view name=\"Pressure\">".getBytes());
                                output.write(("<graph label=\"Pressure\" inputX=\"pressure_time\" inputY=\"pressure\" labelX=\"t (s)\" labelY=\"P (hPa)\" partialUpdate=\"true\" />").getBytes());
                                output.write("</view>".getBytes());
                            }
                            output.write("</views>".getBytes());
                            output.write("<export>".getBytes());
                            if (acc) {
                                output.write("<set name=\"Accelerometer\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"acc_time\" />".getBytes());
                                output.write("<data name=\"Acceleration x (m/s^2)\" source=\"accX\" />".getBytes());
                                output.write("<data name=\"Acceleration y (m/s^2)\" source=\"accY\" />".getBytes());
                                output.write("<data name=\"Acceleration z (m/s^2)\" source=\"accZ\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            if (gyr) {
                                output.write("<set name=\"Gyroscope\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"gyr_time\" />".getBytes());
                                output.write("<data name=\"Gyroscope x (rad/s)\" source=\"gyrX\" />".getBytes());
                                output.write("<data name=\"Gyroscope y (rad/s)\" source=\"gyrY\" />".getBytes());
                                output.write("<data name=\"Gyroscope z (rad/s)\" source=\"gyrZ\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            if (light) {
                                output.write("<set name=\"Light\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"light_time\" />".getBytes());
                                output.write("<data name=\"Illumination (lx)\" source=\"light\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            if (lin) {
                                output.write("<set name=\"Linear Acceleration\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"lin_time\" />".getBytes());
                                output.write("<data name=\"Linear Acceleration x (m/s^2)\" source=\"linX\" />".getBytes());
                                output.write("<data name=\"Linear Acceleration y (m/s^2)\" source=\"linY\" />".getBytes());
                                output.write("<data name=\"Linear Acceleration z (m/s^2)\" source=\"linZ\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            if (mag) {
                                output.write("<set name=\"Magnetometer\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"mag_time\" />".getBytes());
                                output.write("<data name=\"Magnetic field x (µT)\" source=\"magX\" />".getBytes());
                                output.write("<data name=\"Magnetic field y (µT)\" source=\"magY\" />".getBytes());
                                output.write("<data name=\"Magnetic field z (µT)\" source=\"magZ\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            if (pressure) {
                                output.write("<set name=\"Pressure\">".getBytes());
                                output.write("<data name=\"Time (s)\" source=\"pressure_time\" />".getBytes());
                                output.write("<data name=\"Pressure (hPa)\" source=\"pressure\" />".getBytes());
                                output.write("</set>".getBytes());
                            }
                            output.write("</export>".getBytes());
                            output.write("</phyphox>".getBytes());

                            output.close();

                            Intent intent = new Intent(c, Experiment.class);
                            intent.putExtra(EXPERIMENT_XML, file);
                            intent.putExtra(EXPERIMENT_ISASSET, false);
                            intent.setAction(Intent.ACTION_VIEW);
                            c.startActivity(intent);
                        } catch (Exception e) {
                            Log.e("newExperiment", "Could not create new experiment.", e);
                        }
                    }
                });
                neDialog.setNegativeButton(res.getText(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                neDialog.show();
            }
        };

        Button newExperimentB = (Button) findViewById(R.id.newExperiment);
        newExperimentB.setOnClickListener(neocl);
    }

}
