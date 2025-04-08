package de.rwth_aachen.phyphox.Helper.baseColorDrawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.caverock.androidsvg.SVG;

//The class VectorIcon is a drawable that displays a user-given SVG on top of an orange background
public class VectorIcon extends BaseColorDrawable {

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
