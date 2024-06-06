package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import de.rwth_aachen.phyphox.Helper.RGB;

public class MarkerOverlayView extends View {

    Paint paint;
    Paint paintPP;
    Point[] line = null;
    Point[] points = null;
    RectF passepartout = null;
    RectF clip = null;
    GraphSetup graphSetup = null;

    private void init(Context ctx) {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.f);
        paint.setAntiAlias(true);
        paint.setColor((new RGB(0xffffff)).autoLightColor(ctx.getResources()).intColor());

        paintPP = new Paint();
        paintPP.setStyle(Paint.Style.FILL);
        paintPP.setColor(0x80000000);
    }
    public MarkerOverlayView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public MarkerOverlayView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
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
