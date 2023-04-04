package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.caverock.androidsvg.SVG;

import de.rwth_aachen.phyphox.Helper.Helper;

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

    public void setBaseColor(int color) {
        paintBG.setColor(color);
    }

    @Override
    //Needs to be implemented.
    public void setAlpha(int alpha) {
        paintBG.setAlpha(alpha);
    }

    @Override
    //Needs to be implemented.
    public void setColorFilter(ColorFilter cf) {
        paintBG.setColorFilter(cf);
    }

    @Override
    //Needs to be implemented.
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}

