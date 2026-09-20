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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
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

        try (InputStream is = Helper.openResource(c, experiment.resourceFolder, src)) {
            Bitmap bmp = is == null ? null : BitmapFactory.decodeStream(is);
            if (bmp != null)
                drawable = new BitmapDrawable(bmp);
            else
                Log.e("imageView", "Failed to open image: " + src);
        } catch (Exception e) {
            Log.e("imageView", "Failed to open image: " + src);
        }
        if (drawable != null) {
            applyFilter(Helper.isDarkTheme(res) ? darkFilter : lightFilter);

            ImageView iv = new ImageView(c);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            iv.setLayoutParams(lllp);

            iv.setImageDrawable(drawable);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setScaleX(scale);
            iv.setScaleY(scale);

            rootView = iv;
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
        return "<div class=\"imageElement\" id=\"element" + htmlID + "\"><img style=\"width: " + (100.0*scale) + "% \" class=\"lightFilter_" + lightFilter.toString() + " darkFilter_" + darkFilter.toString() + "\" src=\"res?src=" + src + "\"></p></div>";
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
    }
}
