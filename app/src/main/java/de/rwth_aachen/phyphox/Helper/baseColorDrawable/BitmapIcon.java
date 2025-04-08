package de.rwth_aachen.phyphox.Helper.baseColorDrawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

//The class BitmapIcon is a drawable that displays a user-given PNG on top of an orange background
public class BitmapIcon extends BaseColorDrawable {

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
        int size = getBounds().width();
        int wSrc = icon.getWidth();
        int hSrc = icon.getHeight();
        canvas.drawRect(new Rect(0, 0, size, size), paintBG);
        canvas.drawBitmap(icon, new Rect(0, 0, wSrc, hSrc), new Rect(0, 0, size, size), paint);
    }
}
