package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.core.content.ContextCompat;

import de.rwth_aachen.phyphox.Helper.Helper;

//The class TextIcon is a drawable that displays up to three characters in a rectangle as a
//substitution icon, used if an experiment does not have its own icon
public class TextIcon extends BaseColorDrawable {

    private final String text; //The characters too be displayed
    private final Paint paint; //The paint for the characters

    //The constructor takes a context and the characters to display. It also sets up the paints
    public TextIcon(String text, Context c) {
        super(c);
        this.text = text; //Store the characters

        //Text-Paint
        this.paint = new Paint();
        paint.setColor(ContextCompat.getColor(c, R.color.phyphox_white_100));
        paint.setTextSize(c.getResources().getDimension(R.dimen.expElementIconSize) * 0.5f);
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
        canvas.drawText(text, w / 2, w * 2 / 3, paint);
    }
}
