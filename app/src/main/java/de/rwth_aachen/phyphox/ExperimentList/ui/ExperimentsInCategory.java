package de.rwth_aachen.phyphox.ExperimentList.ui;

import static de.rwth_aachen.phyphox.ExperimentList.model.Const.phyphoxCat;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExperimentList.datasource.ExperimentRepository;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

//The category class wraps all experiment entries and their views of a category, including the
//grid view and the category headline
public class ExperimentsInCategory {
    final private Context parentContext; //Needed to create views
    final private LinearLayout catLayout; //This is the base layout of the category, which will contain the headline and the gridView showing all the experiments
    final private TextView categoryHeadline; //The TextView to display the headline
    final private ExperimentsInCategory.ExpandableHeightGridView experimentSubList; //The gridView holding experiment items. (See implementation below for the custom flavor "ExpandableHeightGridView")
    final ExperimentItemAdapter experimentItemAdapter; //Instance of the adapter to fill the gridView (implementation above)
    private final Resources res;

    final public String name; //Category name (headline)
    final public Map<RGB, Integer> colorCount = new HashMap<>();

    public Vector<ExperimentShortInfo> retrieveExperiments() {
         return experimentItemAdapter.experimentShortInfos;
    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        experimentItemAdapter.setPreselectedBluetoothAddress(preselectedBluetoothAddress);
    }

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
                // Calculate entire height by providing a very large height startMenuItem.
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

    Activity parentActivity;


    //Constructor for the category class, takes a category name, the layout into which it should
    // place its views and the calling activity (mostly to display the dialog in the onClick
    // listener of the delete button for each element - maybe this should be restructured).
    public ExperimentsInCategory(String name, Activity parentActivity, ExperimentRepository experimentRepository) {

        this.name = name;
        this.parentActivity = parentActivity;
        this.parentContext = parentActivity;


        //Create the base linear layout to hold title and list
        this.catLayout = new LinearLayout(parentContext);
        catLayout.setOrientation(LinearLayout.VERTICAL);

        this.res = parentActivity.getResources();

        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lllp.setMargins(
                res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin) - res.getDimensionPixelOffset(R.dimen.expElementMargin),
                0,
                res.getDimensionPixelOffset(R.dimen.activity_horizontal_margin) - res.getDimensionPixelOffset(R.dimen.expElementMargin),
                res.getDimensionPixelOffset(R.dimen.activity_vertical_margin)
        );
        catLayout.setLayoutParams(lllp);

        //Create the headline text view
        categoryHeadline = new TextView(parentContext);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
//            layout.setMargins(context.getDimensionPixelOffset(R.dimen.expElementMargin), 0, context.getDimensionPixelOffset(R.dimen.expElementMargin), context.getDimensionPixelOffset(R.dimen.expElementMargin));
        categoryHeadline.setLayoutParams(layout);
        categoryHeadline.setText(name.equals(phyphoxCat) ? res.getString(R.string.categoryPhyphoxOrg) : name);
        categoryHeadline.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.headline_font));
        categoryHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        categoryHeadline.setPadding(res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10, res.getDimensionPixelOffset(R.dimen.headline_font) / 2, res.getDimensionPixelOffset(R.dimen.headline_font) / 10);

        //Create the gridView for the experiment items
        experimentSubList = new ExperimentsInCategory.ExpandableHeightGridView(parentContext);
        experimentSubList.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        experimentSubList.setColumnWidth(res.getDimensionPixelOffset(R.dimen.expElementWidth));
        experimentSubList.setNumColumns(ExperimentsInCategory.ExpandableHeightGridView.AUTO_FIT);
        experimentSubList.setStretchMode(ExperimentsInCategory.ExpandableHeightGridView.STRETCH_COLUMN_WIDTH);
        experimentSubList.setExpanded(true);

        //Create the adapter and give it to the gridView
        experimentItemAdapter = new ExperimentItemAdapter(parentActivity, name, experimentRepository);
        experimentSubList.setAdapter(experimentItemAdapter);

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

    public void getParentScrollViewPosition() {
        //We want to show current availability of experiments requiring cameras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cm = (CameraManager) parentContext.getSystemService(Context.CAMERA_SERVICE);
            CameraHelper.updateCameraList(cm);
        }

        //Save scroll position to restore this later
        ScrollView sv = parentActivity.findViewById(R.id.experimentScroller);
        int scrollY = sv.getScrollY();

        sv.scrollTo(0, scrollY);
    }

    LinearLayout catList;

    public void addToParent(LinearLayout catList) {
        //Add the layout to the layout designated by the caller
        catList.addView(catLayout);
    }

    //Helper to check if the name of this category matches a given string
    public boolean hasName(String cat) {
        return cat.equals(name);
    }

    //Wrapper to add an experiment to this category. This just hands it over to the adapter and updates the category color.
    public void addExperiment(ExperimentShortInfo shortInfo) {
        experimentItemAdapter.addExperiment(shortInfo);
        Integer n = colorCount.get(shortInfo.color);
        if (n == null)
            colorCount.put(shortInfo.color, 1);
        else
            colorCount.put(shortInfo.color, n + 1);
        int max = 0;
        RGB catColor = new RGB(0);
        if (hasName(phyphoxCat)) {
            catColor = (new RGB(0xffffff)).autoLightColor(res);
        } else {
            for (Map.Entry<RGB, Integer> entry : colorCount.entrySet()) {
                if (entry.getValue() > max) {
                    catColor = entry.getKey();
                    max = entry.getValue();
                }
            }
        }
        categoryHeadline.setBackgroundColor(catColor.intColor());
        categoryHeadline.setTextColor(catColor.overlayTextColor().intColor());
    }
}
