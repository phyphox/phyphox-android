package de.rwth_aachen.phyphox.Helper.baseColorDrawable;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.R;

//This is the base class of our experiment icons. It is basically just stuff drawn on a colored background.
//All icons are supposed to be used as squares!
public abstract class BaseColorDrawable extends Drawable {
    protected final Paint paintBG; //The paint for the background

    BaseColorDrawable(Context c) {
        //Background paint
        this.paintBG = new Paint();
        paintBG.setColor(ContextCompat.getColor(c, R.color.phyphox_primary));
        paintBG.setStyle(Paint.Style.FILL);
    }

    public void setBaseColor(RGB color) {
        paintBG.setColor(color.intColor());
    }

    @Override
    //Needs to be implemented.
    public void setAlpha(int alpha) {
        paintBG.setAlpha(alpha);
    }

    @Override
    //Needs to be implemented.
    public void setColorFilter(ColorFilter cf) {
        //Do not apply the filter. It is not needed for our icons and breaks with older Androids
    }

    @Override
    //Needs to be implemented.
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}

