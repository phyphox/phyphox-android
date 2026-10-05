package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.view.View;

//The box of a drawing element (geometry, scale; file format 1.21, phyphox-docs views/drawing.md, "Drawing
//coordinates"): the full width and width / aspectRatio tall. Positions are fractions of the box per axis, lengths
//fractions of the width, angles radians clockwise from twelve o'clock. Drawing outside the box is clipped by the
//View's own bounds.
public abstract class DrawingBoxView extends View {
    protected double aspectRatio = 1;

    public DrawingBoxView(Context context, double aspectRatio) {
        super(context);
        this.aspectRatio = aspectRatio > 0 ? aspectRatio : 1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) Math.round(width / aspectRatio);
        setMeasuredDimension(width, resolveSize(Math.max(height, 1), heightMeasureSpec));
    }

    //A horizontal position as a fraction of the width, in pixels
    protected float px(double xFraction) {
        return (float) (xFraction * getWidth());
    }

    //A vertical position as a fraction of the height, in pixels
    protected float py(double yFraction) {
        return (float) (yFraction * getHeight());
    }

    //A length as a fraction of the width, in pixels
    protected float len(double fraction) {
        return (float) (fraction * getWidth());
    }

    //Canvas.drawArc counts degrees clockwise from three o'clock; the file counts radians clockwise from twelve
    protected static float arcDegrees(double angle) {
        return (float) Math.toDegrees(angle) - 90f;
    }

    //A full turn or more is a closed circle (Path.arcTo and drawArc treat the sweep modulo 360°)
    protected static boolean isFullTurn(double sweepAngle) {
        return Math.abs(sweepAngle) >= 2 * Math.PI - 1e-6;
    }
}
