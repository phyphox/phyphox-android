package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import de.rwth_aachen.phyphox.helper.RGB;

public class MarkerOverlayView extends View {

    class LineAnnotation {
        String label;
        float xy;
        boolean vertical;
        int color;
        LineAnnotation(String label, float xy, boolean vertical, int color) {
            this.label = label;
            this.xy = xy;
            this.vertical = vertical;
            this.color = color;
        }
    }

    Paint paint;
    Paint paintPP;
    Paint paintLineAnnotation;
    Paint paintLineAnnotationText;
    float lineAnnotationTextSize = 10.0f;
    float lineAnnotationTextMargin = 2.0f;
    Point[] line = null;
    Point[] points = null;
    RectF passepartout = null;
    LineAnnotation[] lineAnnotations = null;
    RectF clip = null;
    GraphSetup graphSetup = null;
    boolean autoColor = true;

    private void init(Context ctx) {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.f);
        paint.setAntiAlias(true);

        if (autoColor)
            paint.setColor((new RGB(0xffffff)).autoLightColor(ctx.getResources()).intColor());
        else
            paint.setColor((new RGB(0xffffff)).intColor());

        paintPP = new Paint();
        paintPP.setStyle(Paint.Style.FILL);
        paintPP.setColor(0x80000000);

        paintLineAnnotation = new Paint();
        paintLineAnnotation.setStyle(Paint.Style.STROKE);
        paintLineAnnotation.setStrokeWidth(2.f);
        paintLineAnnotationText = new Paint();
        paintLineAnnotationText.setStyle(Paint.Style.FILL);
        lineAnnotationTextSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12,
                getResources().getDisplayMetrics()
        );
        lineAnnotationTextMargin = 0.2f * lineAnnotationTextSize;
        paintLineAnnotationText.setTextSize(lineAnnotationTextSize);

    }
    public MarkerOverlayView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    //This constructor is loaded when the view is created from XML, which is called from the camera view
    public MarkerOverlayView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        autoColor = false;
        init(ctx);
    }

    public void update(Point[] line, Point[] points) {
        this.line = line;
        this.points = points;
        invalidate();
    }

    public void setPassepartout(RectF rect) {
        this.passepartout = rect;
    }

    public void setLineAnnotations(LineAnnotation[] lineAnnotations) {
        this.lineAnnotations = lineAnnotations;
        if (autoColor) {
            for (LineAnnotation lineAnnotation : lineAnnotations) {
                lineAnnotation.color = ((new RGB(lineAnnotation.color)).autoLightColor(getResources()).intColor());
            }
        }
        invalidate();
    }

    public void setClipRect(RectF rect) {
        this.clip = rect;
    }

    public void setGraphSetup(GraphSetup graphSetup) {
        this.graphSetup = graphSetup;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.save();
        if (graphSetup != null)
            canvas.clipRect(graphSetup.plotBoundL, graphSetup.plotBoundT, graphSetup.plotBoundL + graphSetup.plotBoundW, graphSetup.plotBoundT + graphSetup.plotBoundH);
        else if (clip != null)
            canvas.clipRect(clip);
        if (passepartout != null) {
            canvas.drawRect(0, 0, passepartout.left, getBottom(), paintPP);
            canvas.drawRect(passepartout.left, 0, passepartout.right, passepartout.top, paintPP);
            canvas.drawRect(passepartout.left, passepartout.bottom, passepartout.right, getBottom(), paintPP);
            canvas.drawRect(passepartout.right, 0, getRight(), getBottom(), paintPP);
        }
        if (lineAnnotations != null) {
            for (LineAnnotation la : lineAnnotations) {
                paintLineAnnotation.setColor(la.color);
                paintLineAnnotationText.setColor(la.color);
                if (la.vertical) {
                    canvas.drawLine(la.xy, graphSetup.plotBoundT, la.xy, graphSetup.plotBoundT + graphSetup.plotBoundH, paintLineAnnotation);
                    float textX = la.xy;
                    float textY = graphSetup.plotBoundT + graphSetup.plotBoundH;
                    canvas.rotate(-90.0f, textX, textY);
                    canvas.drawText(la.label, textX+lineAnnotationTextMargin, textY-lineAnnotationTextMargin, paintLineAnnotationText);
                    canvas.rotate(90.0f, textX, textY);
                } else {
                    canvas.drawLine(graphSetup.plotBoundL, la.xy, graphSetup.plotBoundL + graphSetup.plotBoundW, la.xy, paintLineAnnotation);
                    canvas.drawText(la.label, graphSetup.plotBoundL+lineAnnotationTextMargin, la.xy-lineAnnotationTextMargin, paintLineAnnotationText);
                }
            }
        }
        if (line != null && line.length > 1) {
            for (int i = 0; i < line.length-1; i++)
                canvas.drawLine(line[i].x, line[i].y, line[i+1].x, line[i+1].y, paint);
        }
        if (points != null) {
            for (int i = 0; i < points.length; i++) {
                canvas.drawCircle(points[i].x, points[i].y, 20, paint);
            }
        }
        canvas.restore();
    }

}
