package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.helper.RGB;

//GeometryElement draws a single static shape (file format 1.21, phyphox-docs views/drawing.md, "View-Element:
//geometry"): a rectangle, circle, line or ring segment on a transparent background, for gauge faces, needles and
//range bands. An area shape is filled with color and outlined with lineColor, each only when given; a line takes
//lineColor or falls back to color. Nothing in it reads a data container; a transform moves it as a whole.
public class GeometryElement extends ExpViewElement implements Serializable {

    public enum Shape {
        rectangle, circle, line, arc
    }

    public Shape shape = Shape.rectangle;
    public double aspectRatio = 1;
    public RGB color = null; //fill of an area shape, or the colour of a line without lineColor
    public RGB lineColor = null; //outline of an area shape, or the colour of a line
    public double lineWidth = 0.01; //as a fraction of the width
    //rectangle
    public double left = 0, top = 0, right = 1, bottom = 1, cornerRadius = 0;
    //circle, arc
    public double centerX = 0.5, centerY = 0.5, radius = 0.5, innerRadius = 0;
    public double startAngle = 0, sweepAngle = 6.2832;
    //line
    public double startX = 0, startY = 0.5, endX = 1, endY = 0.5;

    //label has no effect on a geometry
    public GeometryElement(String visibility, Resources res) {
        super("", visibility, (String) null, null, res);
    }

    @Override
    //Static: nothing to update from the buffers
    public String getUpdateMode() {
        return "none";
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        super.createView(ll, c, res, parent, experiment);
        rootView = new GeometryView(c, this, res);
        ll.addView(rootView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    //The shape on a Canvas; colours go through the light-theme adjustment like every colour attribute
    public static class GeometryView extends DrawingBoxView {
        private final GeometryElement element;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final RectF innerRect = new RectF();

        public GeometryView(Context context, GeometryElement element, Resources res) {
            super(context, element.aspectRatio);
            this.element = element;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.BUTT); //a line ends exactly at its points
            if (element.color != null)
                fill.setColor(element.color.autoLightColor(res).intColor());
            RGB strokeColor = element.lineColor != null ? element.lineColor : (element.shape == Shape.line ? element.color : null);
            if (strokeColor != null)
                stroke.setColor(strokeColor.autoLightColor(res).intColor());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0)
                return;
            stroke.setStrokeWidth(Math.max(len(element.lineWidth), 0f));
            boolean doFill = element.color != null && element.shape != Shape.line;
            boolean doStroke = element.shape == Shape.line ? (element.lineColor != null || element.color != null) && element.lineWidth > 0
                    : element.lineColor != null && element.lineWidth > 0;
            path.rewind();
            switch (element.shape) {
                case rectangle: {
                    rect.set(px(element.left), py(element.top), px(element.right), py(element.bottom));
                    rect.sort();
                    float r = Math.max(len(element.cornerRadius), 0f);
                    path.addRoundRect(rect, r, r, Path.Direction.CW);
                    break;
                }
                case circle: {
                    path.addCircle(px(element.centerX), py(element.centerY), Math.max(len(element.radius), 0f), Path.Direction.CW);
                    break;
                }
                case line: {
                    path.moveTo(px(element.startX), py(element.startY));
                    path.lineTo(px(element.endX), py(element.endY));
                    doFill = false;
                    break;
                }
                case arc: {
                    float cx = px(element.centerX), cy = py(element.centerY);
                    float outer = Math.max(len(element.radius), 0f), inner = Math.max(Math.min(len(element.innerRadius), outer), 0f);
                    rect.set(cx - outer, cy - outer, cx + outer, cy + outer);
                    innerRect.set(cx - inner, cy - inner, cx + inner, cy + inner);
                    if (isFullTurn(element.sweepAngle)) {
                        //a ring (or a disc): two circles, the hole cut out by the fill rule
                        path.setFillType(Path.FillType.EVEN_ODD);
                        path.addCircle(cx, cy, outer, Path.Direction.CW);
                        if (inner > 0)
                            path.addCircle(cx, cy, inner, Path.Direction.CCW);
                    } else {
                        //outer arc, radial edge, inner arc back (a pie slice when innerRadius is 0), closed
                        path.setFillType(Path.FillType.WINDING);
                        float start = arcDegrees(element.startAngle), sweep = (float) Math.toDegrees(element.sweepAngle);
                        path.arcTo(rect, start, sweep, true);
                        if (inner > 0)
                            path.arcTo(innerRect, start + sweep, -sweep, false);
                        else
                            path.lineTo(cx, cy);
                        path.close();
                    }
                    break;
                }
            }
            if (doFill)
                canvas.drawPath(path, fill);
            if (doStroke)
                canvas.drawPath(path, stroke);
        }
    }

    @Override
    //The remote interface draws the shape itself from the configuration (webinterface readme.md, "Drawing elements")
    protected String createViewHTML() {
        return "<div class=\"geometryElement\" id=\"element" + htmlID + "\"></div>";
    }

    @Override
    public String getWebConfig() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("shape", shape.name());
            cfg.put("aspectRatio", aspectRatio);
            cfg.put("color", color == null ? JSONObject.NULL : "#" + color.hexString());
            cfg.put("lineColor", lineColor == null ? JSONObject.NULL : "#" + lineColor.hexString());
            cfg.put("lineWidth", lineWidth);
            cfg.put("left", left);
            cfg.put("top", top);
            cfg.put("right", right);
            cfg.put("bottom", bottom);
            cfg.put("cornerRadius", cornerRadius);
            cfg.put("centerX", centerX);
            cfg.put("centerY", centerY);
            cfg.put("radius", radius);
            cfg.put("innerRadius", innerRadius);
            cfg.put("startAngle", startAngle);
            cfg.put("sweepAngle", sweepAngle);
            cfg.put("startX", startX);
            cfg.put("startY", startY);
            cfg.put("endX", endX);
            cfg.put("endY", endY);
            return cfg.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String getWebConfigKey() {
        return "geometry";
    }
}
