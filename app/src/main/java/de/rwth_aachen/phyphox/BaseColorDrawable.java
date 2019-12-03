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

//This is the base class of our experiment icons. It is basically just stuff drawn on a colored background.
//All icons are supposed to be used as squares!
abstract class BaseColorDrawable extends Drawable {
    protected final Paint paintBG; //The paint for the background

    BaseColorDrawable(Context c) {
        //Background paint
        this.paintBG = new Paint();
        paintBG.setColor(ContextCompat.getColor(c, R.color.highlight));
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

//The class TextIcon is a drawable that displays up to three characters in a rectangle as a
//substitution icon, used if an experiment does not have its own icon
class TextIcon extends BaseColorDrawable {

    private final String text; //The characters too be displayed
    private final Paint paint; //The paint for the characters

    //The constructor takes a context and the characters to display. It also sets up the paints
    public TextIcon(String text, Context c) {
        super(c);
        this.text = text; //Store the characters

        //Text-Paint
        this.paint = new Paint();
        paint.setColor(ContextCompat.getColor(c, R.color.main));
        paint.setTextSize(c.getResources().getDimension(R.dimen.expElementIconSize)*0.5f);
        paint.setAntiAlias(true);
        paint.setFakeBoldText(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void setBaseColor(int color) {
        super.setBaseColor(color);


        if (Helper.luminance(color) > 0.7)
            paint.setColor(0xff000000);
        else
            paint.setColor(0xffffffff);
    }

    @Override
    //Draw the icon
    public void draw(Canvas canvas) {
        //A rectangle and text on top. Quite simple.
        int w = canvas.getWidth();
        canvas.drawRect(new Rect(0, 0, w, w), paintBG);
        canvas.drawText(text, w/2, w*2/3, paint);
    }
}

//The class BitmapIcon is a drawable that displays a user-given PNG on top of an orange background
class BitmapIcon extends BaseColorDrawable {

    private final Paint paint; //The paint for the icon
    private final Bitmap icon;

    //The constructor takes a context and the characters to display. It also sets up the paints
    public BitmapIcon(Bitmap icon, Context c) {
        super(c);
        this.icon = icon;

        //Icon-Paint
        this.paint = new Paint();
        paint.setAntiAlias(true);
    }

    @Override
    //Draw the icon
    public void draw(Canvas canvas) {
        //A rectangle and text on top. Quite simple.
        int size = canvas.getWidth();
        int wSrc = icon.getWidth();
        int hSrc = icon.getHeight();
        canvas.drawRect(new Rect(0, 0, size, size), paintBG);
        canvas.drawBitmap(icon, new Rect(0, 0, wSrc, hSrc), new Rect(0, 0, size, size), paint);
    }
}

//The class VectorIcon is a drawable that displays a user-given SVG on top of an orange background
class VectorIcon extends BaseColorDrawable {

    private final SVG svg;

    //The constructor takes a context and the characters to display. It also sets up the paints
    public VectorIcon(SVG svg, Context c) {
        super(c);
        this.svg = svg;
    }

    @Override
    //Draw the icon
    public void draw(Canvas canvas) {
        //A rectangle and text on top. Quite simple.
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        svg.setDocumentWidth(w);
        svg.setDocumentHeight(h);
        canvas.drawRect(new Rect(0, 0, w, h), paintBG);
        svg.renderToCanvas(canvas);
    }
}
