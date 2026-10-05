package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphView;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.Unit;
import de.rwth_aachen.phyphox.UnitDialog;
import de.rwth_aachen.phyphox.Units;
import de.rwth_aachen.phyphox.helper.RGB;

//ScaleElement draws the axis of a gauge (file format 1.21, phyphox-docs views/drawing.md, "View-Element: scale"): a
//straight or circular baseline from min to max, major tics, minor tics between them, the values at the tics and a
//label with a unit. The geometry comes from attributes; min and max may be bound to data containers by input
//children (the last value replaces the attribute while it is finite). With a unit reference the values take part in
//the unit conversion like a graph axis: the positions of min and max stay, the tics are chosen automatically in the
//displayed unit, and a tap on the label opens the unit dialog - also inside a stack, as long as the scale is not
//wrapped in a transform (GroupElement.StackLayout offers the tap to its untransformed scales).
public class ScaleElement extends ExpViewElement implements Serializable {

    public enum Shape {
        linear, circular
    }

    public enum ValueOrientation {
        upright, tangential, radial
    }

    public Shape shape = Shape.linear;
    public double aspectRatio = 1;
    public double min = 0, max = 1; //in the experiment's unit
    private Unit unit = Unit.text("");
    private String displayUnitId = null; //the unit currently shown (session state), the experiment's unless switched
    public RGB color = null; //baseline, tics and text; null = the text colour of the theme
    public double size = 1; //text size relative to the default, like the value element's
    public double lineWidth = 0.005; //baseline and tics, as a fraction of the width; 0 draws no baseline (and no tics, which would have no width)
    public double ticStep = 0; //0: automatic, like a graph axis
    public double ticLength = 0.03; //signed: positive is outward on a circular scale, right of the direction of travel on a linear one
    public int minorTics = 0;
    public double minorTicLength = 0.015;
    public int valueEvery = 1; //the value at every n-th major tic counted from min; 0: no values
    public double valueDistance = 0.08;
    public int precision = -1; //decimals of the values; -1: as many as the step needs
    public ValueOrientation valueOrientation = ValueOrientation.upright;
    public double labelPositionX = 0.5, labelPositionY = 0.5;
    //linear
    public double startX = 0.1, startY = 0.5, endX = 0.9, endY = 0.5;
    //circular
    public double centerX = 0.5, centerY = 0.5, radius = 0.4, startAngle = -2.3562, sweepAngle = 4.7124;

    private String minInput = null, maxInput = null; //bound data containers, or null
    private double boundMin = Double.NaN, boundMax = Double.NaN; //their last values as read by onMayReadFromBuffers

    transient private Resources res;
    transient private ScaleView view = null;

    public ScaleElement(String label, String visibility, Resources res) {
        super(label, visibility, (String) null, null, res);
        this.res = res;
    }

    //Binds min or max to a data container; the base class registers the inputs, the remote interface polls them
    public void bind(boolean isMin, String bufferName) {
        if (isMin)
            minInput = bufferName;
        else
            maxInput = bufferName;
        if (inputs == null)
            inputs = new Vector<>();
        if (!inputs.contains(bufferName))
            inputs.add(bufferName);
    }

    public String getMinInput() {
        return minInput;
    }

    public String getMaxInput() {
        return maxInput;
    }

    public void setUnit(Unit unit) {
        this.unit = unit == null ? Unit.text("") : unit;
        this.displayUnitId = this.unit.id;
    }

    public Unit getUnit() {
        return unit;
    }

    public String getDisplayUnitId() {
        return displayUnitId;
    }

    //A referenced unit with a quantity can be shown in every other unit of that quantity (units.md)
    public boolean isConvertible() {
        return unit.id != null && Units.isConvertible(unit.id);
    }

    //Whether the scale shows a unit other than the experiment's
    public boolean isConverted() {
        return isConvertible() && displayUnitId != null && !displayUnitId.equals(unit.id);
    }

    //What the unit dialog does: the values in another unit of the same quantity, the geometry stays
    public void setDisplayUnit(String id) {
        if (!isConvertible() || !Units.sameQuantity(unit.id, id))
            return;
        displayUnitId = id;
        if (view != null)
            view.invalidate();
    }

    @Override
    public void applyUnitSystem(Units.Setting setting) {
        if (isConvertible())
            setDisplayUnit(Units.forSetting(unit.id, setting));
    }

    //The symbol in the label: the display unit's, or the experiment's text
    public String displayUnitSymbol() {
        if (isConverted())
            return Units.symbol(res, displayUnitId);
        return unit.symbol(res);
    }

    //"label (unit)", or either part alone
    public String labelText() {
        String symbol = displayUnitSymbol();
        if (!hasLabel())
            return symbol;
        if (symbol.isEmpty())
            return label;
        return label + " (" + symbol + ")";
    }

    //A position in the display unit (scale and offset, so a temperature converts with its offset)
    private double toDisplay(double v) {
        return isConverted() ? Units.convert(v, unit.id, displayUnitId) : v;
    }

    private double fromDisplay(double v) {
        return isConverted() ? Units.convert(v, displayUnitId, unit.id) : v;
    }

    //The range: the bound container's last value while it is finite, the attribute otherwise
    public double effectiveMin() {
        return Double.isFinite(boundMin) ? boundMin : min;
    }

    public double effectiveMax() {
        return Double.isFinite(boundMax) ? boundMax : max;
    }

    //The length of the baseline in pixels for a box of the given width
    public double baselineLength(double widthPx) {
        if (shape == Shape.circular)
            return Math.abs(sweepAngle) * Math.abs(radius) * widthPx;
        double dx = (endX - startX) * widthPx, dy = (endY - startY) * widthPx / aspectRatio;
        return Math.hypot(dx, dy);
    }

    //The text size in pixels: the app's text size scaled by size
    public float textSize() {
        return (float) (labelSize * size);
    }

    //A tic of the laid-out scale: its value in the experiment's unit, its position along the baseline as a fraction
    //from min (0) to max (1), whether it is a major tic and the value text shown at it (null for none)
    public static class Tic {
        public final double value;
        public final double fraction;
        public final boolean major;
        public final String text;

        Tic(double value, double fraction, boolean major, String text) {
            this.value = value;
            this.fraction = fraction;
            this.major = major;
            this.text = text;
        }
    }

    //How many decimals a step needs to be written exactly: 10 -> 0, 0.25 -> 2, 0.1 -> 1 (at most 10)
    static int decimalsFor(double step) {
        if (!(step > 0))
            return 0;
        for (int d = 0; d < 10; d++) {
            double scaled = step * Math.pow(10, d);
            if (Math.abs(scaled - Math.rint(scaled)) < 1e-6 * Math.max(1, scaled))
                return d;
        }
        return 10;
    }

    //The number of major tics the automatic step aims at: one per five text heights of baseline, at least two
    static int maxTicsFor(double baselinePx, double textPx) {
        if (!(textPx > 0))
            return 2;
        return Math.max(2, (int) Math.floor(baselinePx / (5 * textPx)));
    }

    private static String formatValue(double shown, int decimals) {
        if (Math.abs(shown) < Math.pow(10, -decimals) / 2)
            shown = 0; //no "-0"
        return String.format("%." + decimals + "f", shown);
    }

    //The tics for a box of the given width (drawing.md, "Tics"): in the experiment's unit major tics sit at
    //min + k * ticStep up to max, or at the nice multiples a graph axis of this length would choose when ticStep is 0;
    //while another unit is shown the nice multiples are chosen in that unit and placed at the matching positions.
    //minorTics minor tics divide each step, also between min and the first major tic and after the last one. The
    //values are formatted with as many decimals as the step needs or with precision (converted by the precision rule).
    public List<Tic> computeTics(double widthPx) {
        List<Tic> result = new ArrayList<>();
        double lo = effectiveMin(), hi = effectiveMax();
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo == hi)
            return result;
        double dir = hi > lo ? 1 : -1;
        boolean converted = isConverted();

        List<double[]> majors = new ArrayList<>(); //{value in the experiment's unit, shown number, index k}
        int decimals;
        double step; //between major tics, in the experiment's unit, positive
        int every;
        if (!converted && ticStep > 0) {
            int n = (int) Math.floor(Math.abs(hi - lo) / ticStep + 1e-9);
            for (int k = 0; k <= n; k++) {
                double v = lo + dir * k * ticStep;
                majors.add(new double[]{v, v, k});
            }
            decimals = precision >= 0 ? precision : decimalsFor(ticStep);
            step = ticStep;
            every = valueEvery;
        } else {
            double a = toDisplay(lo), b = toDisplay(hi);
            double dLo = Math.min(a, b), dHi = Math.max(a, b);
            int maxTics = maxTicsFor(baselineLength(widthPx), textSize());
            double[] stepAndPrecision = GraphView.linearTicStep(dHi - dLo, maxTics);
            double dStep = stepAndPrecision[0];
            double first = Math.ceil(dLo / dStep - 1e-9) * dStep;
            for (int k = 0; ; k++) {
                double d = first + k * dStep;
                if (d > dHi + dStep * 1e-9)
                    break;
                majors.add(new double[]{fromDisplay(d), d, k});
            }
            double factor = converted ? Units.scale(unit.id, displayUnitId) : 1;
            decimals = precision >= 0 ? (converted ? Units.precision(precision, factor) : precision) : Math.max((int) stepAndPrecision[1], 0);
            step = dStep / factor;
            //an explicit valueEvery rhythm describes the experiment's unit; a converted scale labels every tic
            every = converted ? (valueEvery > 0 ? 1 : 0) : valueEvery;
        }

        double span = hi - lo;
        for (double[] m : majors) {
            boolean showValue = every > 0 && ((int) m[2]) % every == 0;
            result.add(new Tic(m[0], (m[0] - lo) / span, true, showValue ? formatValue(m[1], decimals) : null));
        }

        if (minorTics > 0 && !majors.isEmpty() && step > 0) {
            double sub = dir * step / (minorTics + 1);
            double base = majors.get(0)[0];
            double jA = (lo - base) / sub, jB = (hi - base) / sub;
            int jFrom = (int) Math.ceil(Math.min(jA, jB) - 1e-9), jTo = (int) Math.floor(Math.max(jA, jB) + 1e-9);
            for (int j = jFrom; j <= jTo; j++) {
                if (j % (minorTics + 1) == 0)
                    continue;
                double v = base + j * sub;
                double f = (v - lo) / span;
                if (f < -1e-9 || f > 1 + 1e-9)
                    continue;
                result.add(new Tic(v, f, false, null));
            }
        }
        return result;
    }

    @Override
    //The bound containers are read like a single value
    public String getUpdateMode() {
        return inputs == null || inputs.isEmpty() ? "none" : "single";
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        super.createView(ll, c, res, parent, experiment);
        this.res = res;
        readBound(experiment);
        view = new ScaleView(c, this, res);
        rootView = view;
        ll.addView(rootView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        super.onFragmentStop(experiment);
        view = null;
    }

    private static double lastValue(PhyphoxExperiment experiment, String name) {
        if (name == null || experiment == null)
            return Double.NaN;
        DataBuffer buffer = experiment.getBuffer(name);
        if (buffer == null || buffer.getFilledSize() == 0)
            return Double.NaN;
        return buffer.value;
    }

    //Reads the bound containers; true if the range changed
    private boolean readBound(PhyphoxExperiment experiment) {
        double m = lastValue(experiment, minInput), M = lastValue(experiment, maxInput);
        boolean changed = Double.compare(m, boundMin) != 0 || Double.compare(M, boundMax) != 0;
        boundMin = m;
        boundMax = M;
        return changed;
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (state == ExpView.State.hidden)
            return;
        if (readBound(experiment) && view != null)
            view.invalidate();
    }

    //Opens the unit dialog for the label, as a tap on it does
    public void openUnitDialog(Context context) {
        if (!isConvertible())
            return;
        UnitDialog.show(context, unit.id, displayUnitId, this::setDisplayUnit);
    }

    //The scale on a Canvas. Its onTouchEvent takes a tap on the label (and nothing else) when the scale is not in a
    //stack; in a stack the StackLayout intercepts every touch and asks hitsLabel/openUnitDialog itself.
    public static class ScaleView extends DrawingBoxView {
        private final ScaleElement element;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private RectF labelRect = null; //where the label was drawn last, with a slop; null without a label
        private boolean tapOnLabel = false;
        private float downX, downY;
        private final int touchSlop;

        public ScaleView(Context context, ScaleElement element, Resources res) {
            super(context, element.aspectRatio);
            this.element = element;
            RGB rgb = element.color != null ? element.color : new RGB(res.getColor(R.color.phyphox_white_100));
            int color = rgb.autoLightColor(res).intColor();
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.BUTT);
            linePaint.setColor(color);
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(color);
            textPaint.setTextAlign(Paint.Align.CENTER);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        //The point on the baseline at the fraction f from min to max, its outward/right-hand unit normal and the
        //direction of travel there as a screen angle in degrees (clockwise from the x axis): {x, y, nx, ny, travel}
        private float[] baselineAt(double f) {
            if (element.shape == Shape.circular) {
                double theta = element.startAngle + f * element.sweepAngle;
                float cx = px(element.centerX), cy = py(element.centerY), r = len(element.radius);
                float nx = (float) Math.sin(theta), ny = (float) -Math.cos(theta);
                double travel = Math.toDegrees(theta) + (element.sweepAngle < 0 ? 180 : 0);
                return new float[]{cx + r * nx, cy + r * ny, nx, ny, (float) travel};
            }
            float sx = px(element.startX), sy = py(element.startY), ex = px(element.endX), ey = py(element.endY);
            float dx = ex - sx, dy = ey - sy;
            double d = Math.hypot(dx, dy);
            float nx = d > 0 ? (float) (-dy / d) : 0, ny = d > 0 ? (float) (dx / d) : 1;
            return new float[]{sx + (float) f * dx, sy + (float) f * dy, nx, ny, (float) Math.toDegrees(Math.atan2(dy, dx))};
        }

        //Text centred on (x, y), turned by degrees about its centre
        private void drawCentredText(Canvas canvas, String text, float x, float y, float degrees) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.save();
            canvas.translate(x, y);
            if (degrees != 0)
                canvas.rotate(degrees);
            canvas.drawText(text, 0, -(fm.ascent + fm.descent) / 2, textPaint);
            canvas.restore();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0)
                return;
            textPaint.setTextSize(element.textSize());
            float lineWidth = len(element.lineWidth);
            boolean lines = lineWidth > 0;
            linePaint.setStrokeWidth(lineWidth);

            if (lines) {
                if (element.shape == Shape.circular) {
                    float cx = px(element.centerX), cy = py(element.centerY), r = len(element.radius);
                    if (isFullTurn(element.sweepAngle))
                        canvas.drawCircle(cx, cy, r, linePaint);
                    else {
                        arcRect.set(cx - r, cy - r, cx + r, cy + r);
                        canvas.drawArc(arcRect, arcDegrees(element.startAngle), (float) Math.toDegrees(element.sweepAngle), false, linePaint);
                    }
                } else
                    canvas.drawLine(px(element.startX), py(element.startY), px(element.endX), py(element.endY), linePaint);
            }

            for (Tic tic : element.computeTics(w)) {
                float[] p = baselineAt(tic.fraction);
                double length = tic.major ? element.ticLength : element.minorTicLength;
                if (lines && length != 0)
                    canvas.drawLine(p[0], p[1], p[0] + p[2] * len(length), p[1] + p[3] * len(length), linePaint);
                if (tic.text != null) {
                    float degrees = 0;
                    if (element.valueOrientation == ValueOrientation.tangential)
                        degrees = p[4];
                    else if (element.valueOrientation == ValueOrientation.radial)
                        degrees = (float) Math.toDegrees(Math.atan2(p[3], p[2]));
                    drawCentredText(canvas, tic.text, p[0] + p[2] * len(element.valueDistance), p[1] + p[3] * len(element.valueDistance), degrees);
                }
            }

            String label = element.labelText();
            if (label.isEmpty()) {
                labelRect = null;
                return;
            }
            float lx = px(element.labelPositionX), ly = py(element.labelPositionY);
            drawCentredText(canvas, label, lx, ly, 0);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float halfWidth = textPaint.measureText(label) / 2, halfHeight = (fm.descent - fm.ascent) / 2;
            float slop = element.textSize() / 2;
            labelRect = new RectF(lx - halfWidth - slop, ly - halfHeight - slop, lx + halfWidth + slop, ly + halfHeight + slop);
        }

        //Whether the point (in this view's coordinates) is on the label of a convertible scale
        public boolean hitsLabel(float x, float y) {
            return element.isConvertible() && labelRect != null && labelRect.contains(x, y);
        }

        public void openUnitDialog() {
            element.openUnitDialog(getContext());
        }

        public ScaleElement getElement() {
            return element;
        }

        //The label's rectangle as drawn last, for the tests
        public RectF labelRect() {
            return labelRect == null ? null : new RectF(labelRect);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tapOnLabel = hitsLabel(event.getX(), event.getY());
                    downX = event.getX();
                    downY = event.getY();
                    return tapOnLabel; //everything but the label is left to the page
                case MotionEvent.ACTION_MOVE:
                    if (tapOnLabel && Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop)
                        tapOnLabel = false;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (tapOnLabel) {
                        tapOnLabel = false;
                        openUnitDialog();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    tapOnLabel = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    //The remote interface draws the scale itself from the configuration (webinterface readme.md, "Drawing elements")
    protected String createViewHTML() {
        return "<div class=\"scaleElement\" id=\"element" + htmlID + "\"></div>";
    }

    @Override
    public String getWebConfig() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("shape", shape.name());
            cfg.put("aspectRatio", aspectRatio);
            cfg.put("min", min);
            cfg.put("max", max);
            cfg.put("minInput", minInput == null ? JSONObject.NULL : minInput);
            cfg.put("maxInput", maxInput == null ? JSONObject.NULL : maxInput);
            cfg.put("unit", unit.toJson());
            cfg.put("color", color == null ? JSONObject.NULL : "#" + color.hexString());
            cfg.put("size", size);
            cfg.put("lineWidth", lineWidth);
            cfg.put("ticStep", ticStep);
            cfg.put("ticLength", ticLength);
            cfg.put("minorTics", minorTics);
            cfg.put("minorTicLength", minorTicLength);
            cfg.put("valueEvery", valueEvery);
            cfg.put("valueDistance", valueDistance);
            cfg.put("precision", precision < 0 ? JSONObject.NULL : precision);
            cfg.put("valueOrientation", valueOrientation.name());
            cfg.put("labelPositionX", labelPositionX);
            cfg.put("labelPositionY", labelPositionY);
            cfg.put("startX", startX);
            cfg.put("startY", startY);
            cfg.put("endX", endX);
            cfg.put("endY", endY);
            cfg.put("centerX", centerX);
            cfg.put("centerY", centerY);
            cfg.put("radius", radius);
            cfg.put("startAngle", startAngle);
            cfg.put("sweepAngle", sweepAngle);
            return cfg.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String getWebConfigKey() {
        return "scale";
    }
}
