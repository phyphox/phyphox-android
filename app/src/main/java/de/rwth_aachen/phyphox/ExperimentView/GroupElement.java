package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;

//GroupElement implements the view groups of file format 1.21 (phyphox-docs, views/groups.md): vertical,
//horizontal, grid and stack. A group holds other view elements and arranges them; TransformElement
//extends it for the transform wrapper inside a stack. label has no effect on a group, visibility hides
//the whole group, and groups are never maximized themselves - a leaf inside vertical/horizontal/grid
//can be, then the path of groups down to it is stretched to the full height (maximizePath).
public class GroupElement extends ExpViewElement implements Serializable {

    public enum Kind {
        vertical, horizontal, grid, stack, transform
    }

    public final Kind kind;
    protected final Vector<ExpViewElement> children = new Vector<>();

    //grid only
    private double maxWidth = 25; //in text line heights (info_element_font), like the separator's height
    private boolean fillLastRow = false;

    public GroupElement(Kind kind, String visibility, Resources res) {
        super("", visibility, (String) null, null, res);
        this.kind = kind;
    }

    public void setGrid(double maxWidth, boolean fillLastRow) {
        this.maxWidth = maxWidth;
        this.fillLastRow = fillLastRow;
    }

    public double getMaxWidth() {
        return maxWidth;
    }

    public boolean getFillLastRow() {
        return fillLastRow;
    }

    public void addChild(ExpViewElement child) {
        children.add(child);
        if (kind == Kind.stack || inStack)
            child.setInStack(true);
    }

    @Override
    public Vector<ExpViewElement> getChildren() {
        return children;
    }

    @Override
    public boolean contains(ExpViewElement element) {
        if (element == this)
            return true;
        for (ExpViewElement child : children)
            if (child.contains(element))
                return true;
        return false;
    }

    @Override
    public void setInStack(boolean inStack) {
        super.setInStack(inStack);
        for (ExpViewElement child : children)
            child.setInStack(inStack);
    }

    @Override
    //A group shows nothing itself
    public String getUpdateMode() {
        return "none";
    }

    @Override
    //The remote interface builds the container from the type and the nested elements (see phyphox-webinterface readme.md)
    protected String createViewHTML() {
        return "";
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        super.createView(ll, c, res, parent, experiment);

        ViewGroup container;
        switch (kind) {
            case vertical: {
                LinearLayout v = new LinearLayout(c);
                v.setOrientation(LinearLayout.VERTICAL);
                for (ExpViewElement child : children)
                    child.createView(v, c, res, parent, experiment);
                container = v;
                break;
            }
            case horizontal: {
                LinearLayout h = new LinearLayout(c);
                h.setOrientation(LinearLayout.HORIZONTAL);
                for (ExpViewElement child : children)
                    child.createView(h, c, res, parent, experiment);
                //Width 0 with the weight as layout_weight splits the row; a GONE child leaves its share to the others
                for (ExpViewElement child : children) {
                    if (child.rootView == null)
                        continue;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, existingHeight(child.rootView), (float) Math.max(child.weight, 0));
                    lp.gravity = Gravity.CENTER_VERTICAL;
                    child.rootView.setLayoutParams(lp);
                }
                container = h;
                break;
            }
            case grid: {
                GridGroupLayout g = new GridGroupLayout(c, (float) (maxWidth * res.getDimension(R.dimen.info_element_font)), fillLastRow);
                createChildrenInto(g, c, res, parent, experiment, null);
                container = g;
                break;
            }
            case stack:
            default: {
                StackLayout s = new StackLayout(c);
                createChildrenInto(s, c, res, parent, experiment, Gravity.CENTER_VERTICAL);
                container = s;
                break;
            }
        }

        rootView = container;
        ll.addView(rootView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    //The elements only know how to add themselves to a LinearLayout, so they are created in a scratch
    //layout and moved into the container with the params it needs
    protected void createChildrenInto(ViewGroup container, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment, Integer gravity) {
        LinearLayout scratch = new LinearLayout(c);
        scratch.setOrientation(LinearLayout.VERTICAL);
        for (ExpViewElement child : children) {
            child.createView(scratch, c, res, parent, experiment);
            if (child.rootView == null)
                continue;
            int height = existingHeight(child.rootView);
            ViewGroup.LayoutParams lp;
            if (gravity != null)
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, gravity);
            else
                lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            ViewGroup oldParent = (ViewGroup) child.rootView.getParent();
            if (oldParent != null)
                oldParent.removeView(child.rootView);
            container.addView(child.rootView, lp);
        }
    }

    //Elements with a fixed height (separator) keep it; everything else wraps
    private static int existingHeight(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height > 0)
            return lp.height;
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    @Override
    public void destroyView() {
        for (ExpViewElement child : children)
            child.destroyView();
        super.destroyView();
    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        for (ExpViewElement child : children)
            child.onFragmentStop(experiment);
        super.onFragmentStop(experiment);
    }

    @Override
    //Groups are not maximized on their own; ExpViewFragment calls maximizePath for the group holding the leaf
    public void maximize() {
    }

    @Override
    public void maximizePath(ExpViewElement leaf) {
        if (state == ExpView.State.hidden || !contains(leaf))
            return;
        state = ExpView.State.maximized;
        if (rootView != null) {
            rootView.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams lp = rootView.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                rootView.setLayoutParams(lp);
            }
        }
        for (ExpViewElement child : children) {
            if (child.contains(leaf))
                child.maximizePath(leaf);
            else
                child.hide();
        }
    }

    @Override
    public void restore() {
        super.restore();
        if (rootView != null) {
            ViewGroup.LayoutParams lp = rootView.getLayoutParams();
            if (lp != null && lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                rootView.setLayoutParams(lp);
            }
        }
        for (ExpViewElement child : children)
            child.restore();
    }

    //A stack is not interactive: touches on its children are intercepted here and left to the page (scrolling)
    public static class StackLayout extends FrameLayout {
        public StackLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }

    //Rows of equal columns: the smallest column count that keeps a column at or below maxWidth
    //(groups.md, "View-Element: grid"). Rows are as tall as their tallest child, shorter children
    //are centred vertically. With fillLastRow an incomplete last row is split among its children.
    public static class GridGroupLayout extends ViewGroup {
        private final float maxWidthPx;
        private final boolean fillLastRow;
        private final List<int[]> frames = new ArrayList<>(); //per visible child: left, top, right, bottom

        public GridGroupLayout(Context context, float maxWidthPx, boolean fillLastRow) {
            super(context);
            this.maxWidthPx = maxWidthPx;
            this.fillLastRow = fillLastRow;
        }

        public int columnsFor(int width) {
            if (maxWidthPx <= 0)
                return 1;
            return Math.max(1, (int) Math.ceil(width / maxWidthPx - 1e-6));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);

            List<View> visible = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++)
                if (getChildAt(i).getVisibility() != GONE)
                    visible.add(getChildAt(i));

            frames.clear();

            //Exclusive mode: the one remaining child (on the path to a maximized leaf) fills the group
            if (visible.size() == 1 && heightMode != MeasureSpec.UNSPECIFIED && visible.get(0).getLayoutParams().height == LayoutParams.MATCH_PARENT) {
                visible.get(0).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
                frames.add(new int[]{0, 0, width, heightSize});
                setMeasuredDimension(width, heightSize);
                return;
            }

            int columns = columnsFor(width);
            int top = 0;
            for (int start = 0; start < visible.size(); start += columns) {
                int count = Math.min(columns, visible.size() - start);
                int cols = fillLastRow ? count : columns;
                int rowHeight = 0;
                int left = 0;
                for (int i = 0; i < count; i++) {
                    View child = visible.get(start + i);
                    int right = (i + 1) * width / cols;
                    int childWidth = right - left;
                    int lpHeight = child.getLayoutParams().height;
                    int childHeightSpec = lpHeight > 0 ? MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                    child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), childHeightSpec);
                    rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                    frames.add(new int[]{left, top, right, 0});
                    left = right;
                }
                for (int i = 0; i < count; i++) {
                    int[] frame = frames.get(start + i);
                    int childHeight = visible.get(start + i).getMeasuredHeight();
                    frame[1] = top + (rowHeight - childHeight) / 2;
                    frame[3] = frame[1] + childHeight;
                }
                top += rowHeight;
            }
            setMeasuredDimension(width, resolveSize(top, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int n = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE)
                    continue;
                if (n >= frames.size())
                    break;
                int[] frame = frames.get(n++);
                child.layout(frame[0], frame[1], frame[2], frame[3]);
            }
        }
    }
}
