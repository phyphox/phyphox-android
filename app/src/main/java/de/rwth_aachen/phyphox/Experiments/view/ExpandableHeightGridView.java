package de.rwth_aachen.phyphox.Experiments.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.GridView;

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
public class ExpandableHeightGridView extends GridView {

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
