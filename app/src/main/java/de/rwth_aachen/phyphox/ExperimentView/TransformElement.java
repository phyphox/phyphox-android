package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataInput;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;

//TransformElement wraps exactly one element of a stack and scales, rotates, moves or fades it under the
//control of data containers (file format 1.21, phyphox-docs views/groups.md, "View-Element: transform").
//Each property is bound by an input with a linear range map; a property without an input keeps its
//neutral value. The properties compose as scale, then rotation, then translation about the origin,
//which is how a View applies scaleX/Y, rotation and translationX/Y about its pivot. Lengths are
//fractions of the wrapped element's untransformed size, which is also what the stack lays out.
public class TransformElement extends GroupElement implements Serializable {

    public enum Property {
        scale, scaleX, scaleY, translateX, translateY, rotate, opacity
    }

    public static class Binding implements Serializable {
        public Property as;
        public DataInput input; //a buffer or a constant
        public double min = 0., max = 1., mapMin = 0., mapMax = 1.;
        public boolean clamp = false;

        //The mapped property value, or NaN for "keep the neutral value"
        double map() {
            double v = input.isBuffer ? (input.buffer.getFilledSize() == 0 ? Double.NaN : input.buffer.value) : input.getValue();
            if (!Double.isFinite(v) || min == max)
                return Double.NaN;
            double m = mapMin + (v - min) * (mapMax - mapMin) / (max - min);
            if (clamp)
                m = Math.max(Math.min(mapMin, mapMax), Math.min(Math.max(mapMin, mapMax), m));
            return m;
        }
    }

    private final Vector<Binding> bindings = new Vector<>();
    private final double originX, originY;

    transient private FrameLayout wrapper = null;
    transient private float[] applied = null; //scaleX, scaleY, rotation (deg), translateX, translateY (fractions), alpha

    public TransformElement(String visibility, Resources res, double originX, double originY) {
        super(Kind.transform, visibility, res);
        this.originX = originX;
        this.originY = originY;
    }

    public void addBinding(Binding binding) {
        bindings.add(binding);
    }

    public Vector<Binding> getBindings() {
        return bindings;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    //The wrapped element (null while the file is still being parsed)
    public ExpViewElement getChild() {
        return children.isEmpty() ? null : children.get(0);
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        //ExpViewElement.createView registers the visibility buffer; the property buffers are registered like it
        this.parent = parent;
        if (visibility != null) {
            visibilityBuffer = experiment.getBuffer(visibility);
            visibilityBuffer.register(this);
        }
        for (Binding b : bindings)
            if (b.input.isBuffer)
                b.input.buffer.register(this);
        needsUpdate = true;

        wrapper = new FrameLayout(c);
        createChildrenInto(wrapper, c, res, parent, experiment, Gravity.CENTER_VERTICAL);
        applied = null;
        //Pivot and translation scale with the laid-out size, so they are refreshed whenever it changes
        wrapper.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot)
                apply(true);
        });

        rootView = wrapper;
        ll.addView(rootView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        for (Binding b : bindings)
            if (b.input.isBuffer)
                b.input.buffer.unregister(this);
        for (ExpViewElement child : children)
            child.onFragmentStop(experiment);
        if (visibility != null && visibilityBuffer != null)
            visibilityBuffer.unregister(this);
        wrapper = null;
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (state == ExpView.State.hidden)
            return;
        apply(false);
    }

    //Evaluates every binding and sets the View properties of the wrapper; only when something changed unless forced
    private void apply(boolean force) {
        if (wrapper == null)
            return;
        double scale = 1., scaleX = 1., scaleY = 1., translateX = 0., translateY = 0., rotate = 0., opacity = 1.;
        for (Binding b : bindings) {
            double m = b.map();
            if (Double.isNaN(m))
                continue;
            switch (b.as) {
                case scale: scale = m; break;
                case scaleX: scaleX = m; break;
                case scaleY: scaleY = m; break;
                case translateX: translateX = m; break;
                case translateY: translateY = m; break;
                case rotate: rotate = m; break;
                case opacity: opacity = Math.max(0., Math.min(1., m)); break;
            }
        }
        float[] next = {(float) (scale * scaleX), (float) (scale * scaleY), (float) Math.toDegrees(rotate), (float) translateX, (float) translateY, (float) opacity};
        if (!force && applied != null && java.util.Arrays.equals(applied, next))
            return;
        applied = next;
        int w = wrapper.getWidth();
        int h = wrapper.getHeight();
        wrapper.setPivotX((float) (originX * w));
        wrapper.setPivotY((float) (originY * h));
        wrapper.setScaleX(next[0]);
        wrapper.setScaleY(next[1]);
        wrapper.setRotation(next[2]); //positive = clockwise on screen, as specified
        wrapper.setTranslationX(next[3] * w);
        wrapper.setTranslationY(next[4] * h);
        wrapper.setAlpha(next[5]);
    }

    //The values currently applied to the wrapper, for tests: scaleX, scaleY, rotation in degrees, translateX/Y as fractions, alpha
    public float[] appliedTransform() {
        return applied == null ? null : applied.clone();
    }

    @Override
    //A transform is never on the path to a maximized element (stacks do not maximize)
    public void maximizePath(ExpViewElement leaf) {
    }
}
