package de.rwth_aachen.phyphox.Experiments;

import static de.rwth_aachen.phyphox.GlobalConfig.phyphoxCat;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;

//The category class wraps all experiment entries and their views of a category, including the
//grid view and the category headline
public class ExperimentsInCategory {
    final private Context parentContext; //Needed to create views
    final public String name; //Category name (headline)
    final private LinearLayout catLayout; //This is the base layout of the category, which will contain the headline and the gridView showing all the experiments
    final private TextView categoryHeadline; //The TextView to display the headline
    final private ExpandableHeightGridView experimentSubList; //The gridView holding experiment items. (See implementation below for the custom flavor "ExpandableHeightGridView")
    final private ExperimentItemAdapter experiments; //Instance of the adapter to fill the gridView (implementation above)
    final private Map<Integer, Integer> colorCount = new HashMap<>();



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
                parentContext.getResources().getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-parentContext.getResources().getDimensionPixelOffset(R.dimen.expElementMargin),
                0,
                parentContext.getResources().getDimensionPixelOffset(R.dimen.activity_horizontal_margin)-parentContext.getResources().getDimensionPixelOffset(R.dimen.expElementMargin),
                parentContext.getResources().getDimensionPixelOffset(R.dimen.activity_vertical_margin)
        );
        catLayout.setLayoutParams(lllp);

        //Create the headline text view
        categoryHeadline = new TextView(parentContext);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
//            layout.setMargins(context.getDimensionPixelOffset(R.dimen.expElementMargin), 0, context.getDimensionPixelOffset(R.dimen.expElementMargin), context.getDimensionPixelOffset(R.dimen.expElementMargin));
        categoryHeadline.setLayoutParams(layout);
        categoryHeadline.setText(name.equals(phyphoxCat) ? parentContext.getResources().getString(R.string.categoryPhyphoxOrg) : name);
        categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, parentContext.getResources().getDimension(R.dimen.headline_font));
        categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        categoryHeadline.setPadding(parentContext.getResources().getDimensionPixelOffset(R.dimen.headline_font) / 2, parentContext.getResources().getDimensionPixelOffset(R.dimen.headline_font) / 10, parentContext.getResources().getDimensionPixelOffset(R.dimen.headline_font) / 2, parentContext.getResources().getDimensionPixelOffset(R.dimen.headline_font) / 10);

        //Create the gridView for the experiment items
        experimentSubList = new ExpandableHeightGridView(parentContext);
        experimentSubList.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        experimentSubList.setColumnWidth(parentContext.getResources().getDimensionPixelOffset(R.dimen.expElementWidth));
        experimentSubList.setNumColumns(ExpandableHeightGridView.AUTO_FIT);
        experimentSubList.setStretchMode(ExpandableHeightGridView.STRETCH_COLUMN_WIDTH);
        experimentSubList.setExpanded(true);

        //Create the adapter and give it to the gridView
        experiments = new ExperimentItemAdapter(parentActivity, name);
        experimentSubList.setAdapter(experiments);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            catLayout.setElevation(parentContext.getResources().getDimensionPixelOffset(R.dimen.expElementElevation));
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

    //Wrapper to add an experiment to this category. This just hands it over to the adapter and updates the category color.
    public void addExperiment(ExperimentDataModel experimentInfo) {
        experiments.addExperiment(experimentInfo);
        Integer n = colorCount.get(experimentInfo.getColor());
        if (n == null)
            colorCount.put(experimentInfo.getColor(), 1);
        else
            colorCount.put(experimentInfo.getColor(), n+1);
        int max = 0;
        int catColor = 0;
        for (Map.Entry<Integer,Integer> entry : colorCount.entrySet()) {
            if (entry.getValue() > max) {
                catColor = entry.getKey();
                max = entry.getValue();
            }
        }
        categoryHeadline.setBackgroundColor(catColor);
        if (Helper.luminance(catColor) > 0.7)
            categoryHeadline.setTextColor(0xff000000);
        else
            categoryHeadline.setTextColor(0xffffffff);
    }

    //Helper to check if the name of this category matches a given string
    public boolean hasName(String cat) {
        return cat.equals(name);
    }
}
