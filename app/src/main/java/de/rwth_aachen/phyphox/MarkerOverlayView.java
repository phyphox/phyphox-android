package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.View;

public class MarkerOverlayView extends View {

    Paint paint;
    Point[] line = null;
    Point[] points = null;
    GraphSetup graphSetup = null;

    MarkerOverlayView(Context ctx) {
        super(ctx);
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.f);
        paint.setAntiAlias(true);
        paint.setColor(0xffffffff);
    }

    public void update(Point[] line, Point[] points) {
        this.line = line;
        this.points = points;
        invalidate();
    }

    public void setGraphSetup(GraphSetup graphSetup) {
        this.graphSetup = graphSetup;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (graphSetup != null)
            canvas.clipRect(graphSetup.plotBoundL, graphSetup.plotBoundT, graphSetup.plotBoundL + graphSetup.plotBoundW, graphSetup.plotBoundT + graphSetup.plotBoundH);
        if (line != null && line.length > 1) {
            for (int i = 0; i < line.length-1; i++)
                canvas.drawLine(line[i].x, line[i].y, line[i+1].x, line[i+1].y, paint);
        }
        if (points != null) {
            for (int i = 0; i < points.length; i++)
                canvas.drawCircle(points[i].x, points[i].y, 20, paint);
        }
        canvas.restore();
    }

}
