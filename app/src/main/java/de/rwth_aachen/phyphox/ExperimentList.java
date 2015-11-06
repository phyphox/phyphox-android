package de.rwth_aachen.phyphox;

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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Base64;
import android.util.TypedValue;
import android.util.Xml;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class ExperimentList extends AppCompatActivity {

    public final static String EXPERIMENT_XML = "com.dicon.phyphox.EXPERIMENT_XML";
    public static final String PREFS_NAME = "phyphox";
    public CheckBox dontShowAgain;

    Resources res;

    private class category {
        private Context parentContext;
        public String name;
        private ScrollView catView;
        private LinearLayout catLayout;
        private Vector<Button> experimentButtons = new Vector<>();
        private TextView categoryHeadline;

        public category(String name, LinearLayout parentLayout, Context c) {
            this.name = name;
            parentContext = c;
            catView = new ScrollView(parentContext);
            catView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            catLayout = new LinearLayout(parentContext);
            catLayout.setOrientation(LinearLayout.VERTICAL);
            catLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            catLayout.setPadding(0, res.getDimensionPixelOffset(R.dimen.activity_vertical_margin), res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin), res.getDimensionPixelOffset(R.dimen.activity_vertical_margin));

            categoryHeadline = new TextView(parentContext);
            categoryHeadline.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            categoryHeadline.setText(name);
            categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.headline_font));
            categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
            categoryHeadline.setBackgroundColor(ContextCompat.getColor(parentContext, R.color.highlight));
            categoryHeadline.setTextColor(ContextCompat.getColor(parentContext, R.color.main));
            categoryHeadline.setPadding(res.getDimensionPixelOffset(R.dimen.headline_font)/2, res.getDimensionPixelOffset(R.dimen.headline_font)/10, res.getDimensionPixelOffset(R.dimen.headline_font)/2, res.getDimensionPixelOffset(R.dimen.headline_font)/10);


            catLayout.addView(categoryHeadline);

            catView.addView(catLayout);

            parentLayout.addView(catView);
        }


        public void addExperiment(String exp, Bitmap image, final String xmlFile) {
            experimentButtons.add(new Button(parentContext));
            experimentButtons.lastElement().setText(exp);
            experimentButtons.lastElement().setTextColor(ContextCompat.getColor(parentContext, R.color.main));
            experimentButtons.lastElement().setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.experiment_icon_font));

            Drawable [] d = new Drawable[2];
            d[0] = ContextCompat.getDrawable(parentContext, R.drawable.experiment_border);
            d[1] = new BitmapDrawable(res, image);
            LayerDrawable ld = new LayerDrawable(d);
            ld.setLayerInset(1, res.getDimensionPixelOffset(R.dimen.expElementBorderWidth), res.getDimensionPixelOffset(R.dimen.expElementBorderWidth), res.getDimensionPixelOffset(R.dimen.expElementBorderWidth), res.getDimensionPixelOffset(R.dimen.expElementBorderWidth));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                experimentButtons.lastElement().setBackground(ld);
            } else {
                experimentButtons.lastElement().setBackgroundDrawable(ld);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                experimentButtons.lastElement().setAllCaps(false);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(res.getDimensionPixelOffset(R.dimen.expElementWidth), res.getDimensionPixelOffset(R.dimen.expElementHeight));
            lp.setMargins(0, res.getDimensionPixelOffset(R.dimen.activity_vertical_margin), 0, 0);
            experimentButtons.lastElement().setLayoutParams(lp);

            experimentButtons.lastElement().setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), Experiment.class);
                    intent.putExtra(EXPERIMENT_XML, xmlFile);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        ActivityOptions options = ActivityOptions.makeScaleUpAnimation(v, 0,
                                0, v.getWidth(), v.getHeight());
                        v.getContext().startActivity(intent, options.toBundle());
                    } else {
                        v.getContext().startActivity(intent);
                    }
                }
            });

            catLayout.addView(experimentButtons.lastElement());
        }

        public boolean hasName(String cat) {
            return cat.equals(name);
        }
    }

    private Vector<category> categories = new Vector<>();

    private void addExperiment(String exp, String cat, Bitmap image, String xmlFile) {
        for (category icat : categories) {
            if (icat.hasName(cat)) {
                icat.addExperiment(exp, image, xmlFile);
                return;
            }
        }
        LinearLayout catList = (LinearLayout)findViewById(R.id.categoryList);
        categories.add(new category(cat, catList, this));
        categories.lastElement().addExperiment(exp, image, xmlFile);
    }

    public static Bitmap decodeBase64(String input) {
        byte[] decodedByte = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment_list);

        res = getResources();

        try {
            AssetManager assetManager = getAssets();
            final String[] experimentXMLs = assetManager.list("experiments");
            for (String experimentXML : experimentXMLs) {
                boolean hidden = false;
                try {
                    InputStream input = assetManager.open("experiments/" + experimentXML);
                    XmlPullParser xpp = Xml.newPullParser();
                    xpp.setInput(input, "UTF-8");

                    int eventType = xpp.getEventType();
                    String title = "";
                    String category = "";
                    String icon = "";
                    while (eventType != XmlPullParser.END_DOCUMENT){
                        switch (eventType) {
                            case XmlPullParser.START_TAG:
                                switch (xpp.getName()) {
                                    case "title":
                                        title = xpp.nextText();
                                        break;
                                    case "icon":
                                        icon = xpp.nextText();
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
                        Bitmap image;
                        if (icon.equals(""))
                            image = null;
                        else
                            image = decodeBase64(icon);
                        addExperiment(title, category, image, experimentXML);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error loading " + experimentXML, Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error: Could not load internal experiment list.", Toast.LENGTH_LONG).show();
        }

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
    }

}
