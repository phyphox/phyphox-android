package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.helper.Helper;

//ImageElement displays an image
public class ImageElement extends ExpViewElement implements Serializable {

    public enum ImageFilter {
        none, invert
    }

    private String src;
    private String lightSrc = null; //Shown instead of src in light or dark mode (file format 1.21), null if not given
    private String darkSrc = null;
    public Drawable drawable;
    private float scale = 1.0f;
    private ImageFilter darkFilter = ImageFilter.none;
    private ImageFilter lightFilter = ImageFilter.none;

    //Label is not used
    public ImageElement(String valueOutput, String visibility, Vector<String> inputs, Resources res, String src) {
        super("", visibility, valueOutput, inputs, res);
        this.src = src;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setModeSources(String lightSrc, String darkSrc) {
        this.lightSrc = lightSrc;
        this.darkSrc = darkSrc;
    }

    //No fallback to src if the file of the mode cannot be opened (rules.yml, image-mode-src)
    public String srcForMode(boolean dark) {
        String modeSrc = dark ? darkSrc : lightSrc;
        return modeSrc != null ? modeSrc : src;
    }

    public void setFilters(ImageFilter darkFilter, ImageFilter lightFilter) {
        this.darkFilter = darkFilter;
        this.lightFilter = lightFilter;
    }

    @Override
    //This does not display anything. Do not update.
    public String getUpdateMode() {
        return "none";
    }

    @Override
    //Append the Android views we need to the linear layout
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
        super.createView(ll, c, res, parent, experiment);

        boolean dark = Helper.isDarkTheme(res);
        String shown = srcForMode(dark);
        drawable = null;
        try (InputStream is = Helper.openResource(c, experiment.resourceFolder, shown)) {
            Bitmap bmp = is == null ? null : BitmapFactory.decodeStream(is);
            if (bmp != null)
                drawable = new BitmapDrawable(bmp);
            else
                Log.e("imageView", "Failed to open image: " + shown);
        } catch (Exception e) {
            Log.e("imageView", "Failed to open image: " + shown);
        }
        if (drawable != null) {
            applyFilter(dark ? darkFilter : lightFilter);

            ImageView iv = new ImageView(c);
            iv.setImageDrawable(drawable);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);

            //scale is the width relative to the available width (views.yml: "half the width of the view") and the
            //element's box follows the image, as on iOS and in the web interface - no blank space around a scaled image
            ScaledFrame frame = new ScaledFrame(c, scale);
            frame.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            rootView = frame;
        } else {
            TextView tv = new TextView(c);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lllp);

            tv.setText("Image not available");
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, Typeface.BOLD);
            rootView = tv;
        }

        //Add it to the linear layout
        ll.addView(rootView);
    }

    //Gives its one child scale times the available width, centred, and takes the child's height
    public static class ScaledFrame extends FrameLayout {
        private final float scale;

        public ScaledFrame(Context context, float scale) {
            super(context);
            this.scale = scale;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int childWidth = Math.max(1, Math.round(width * scale));
            int height = 0;
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = child.getMeasuredHeight();
            }
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (getChildCount() == 0)
                return;
            View child = getChildAt(0);
            int left = (getMeasuredWidth() - child.getMeasuredWidth()) / 2;
            child.layout(left, 0, left + child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    final ColorMatrixColorFilter colorFilterNone = new ColorMatrixColorFilter(
        new float[]{
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 1, 0
        }
    );

    final ColorMatrixColorFilter colorFilterInvert = new ColorMatrixColorFilter(
        new float[]{
            -1, 0, 0, 0, 255,
            0, -1, 0, 0, 255,
            0, 0, -1, 0, 255,
            0, 0, 0, 1, 0
        }
    );

    protected void applyFilter(ImageFilter filter) {
        switch (filter) {
            case none:
                drawable.setColorFilter(colorFilterNone);
                break;
            case invert:
                drawable.setColorFilter(colorFilterInvert);
                break;
        }
    }

    @Override
    //Create the HTML version of this view:
    //<div>
    //  <img ... />
    //</div>
    protected String createViewHTML(){
        //The web interface picks the file by its own bright mode from the data attributes (webinterface readme, "Image elements")
        String modes = "";
        if (lightSrc != null || darkSrc != null)
            modes = " data-light-src=\"" + resURL(srcForMode(false)) + "\" data-dark-src=\"" + resURL(srcForMode(true)) + "\"";
        return "<div class=\"imageElement\" id=\"element" + htmlID + "\"><img style=\"width: " + (100.0*scale) + "%\" class=\"lightFilter_" + lightFilter.toString() + " darkFilter_" + darkFilter.toString() + "\" src=\"" + resURL(src) + "\"" + modes + "></div>";
    }

    //The resource name is a query value of /res (RemoteServer.handleRes decodes it before the lookup)
    private static String resURL(String name) {
        try {
            return "res?src=" + URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "res?src=" + name;
        }
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
    }
}
