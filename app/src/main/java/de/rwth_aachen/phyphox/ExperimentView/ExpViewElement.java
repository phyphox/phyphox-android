package de.rwth_aachen.phyphox.ExperimentView;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.BufferNotification;
import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;

//Abstract ExpViewElement class defining the interface for any element of an experiment view
public abstract class ExpViewElement implements Serializable, BufferNotification {
    public String label; //Each element has a label. Usually naming the data shown
    public String visibility; //Elements can have visibility buffer name, which be shown and hidden as per the buffer value.
    public float labelSize; //Size of the label
    protected String valueOutput; //User input will be directed to this output, so the experiment can write it to a dataBuffer
    public Vector<String> inputs;
    protected Vector<String> outputs;
    protected boolean needsUpdate = true;

    protected int htmlID; //This holds a unique id, so the element can be referenced in the webinterface via an HTML ID

    public double weight = 1.0; //Share of the row inside a horizontal group (file format 1.21, groups.md); read nowhere else
    public boolean verticalLayout = false; //Label above the control instead of left of it (file format 1.21, value/edit/toggle/dropdown/slider)
    protected boolean inStack = false; //Inside a stack the element is not interactive (no maximize, zoom or picks) and draws no opaque background

    transient public View rootView; //Holds the root view of the element
    transient protected ExpViewFragment parent = null;

    public ExpView.State state = ExpView.State.normal;

    DataBuffer visibilityBuffer = null;
    //Constructor takes the label, any buffer name that should be used an a reference to the resources
    public ExpViewElement(String label, String visibility,  String valueOutput, Vector<String> inputs, Resources res) {
        this.label = label == null ? "" : label; //Empty string as on iOS: the remote interface writes every label into its view list
        this.visibility = visibility;
        this.labelSize = res.getDimension(R.dimen.label_font);
        this.valueOutput = valueOutput;
        this.inputs = inputs;

        //If not set otherwise, set the input buffer to be identical to the output buffer
        //This allows to receive the old user-set value after the view has changed
        if (this.inputs == null && this.valueOutput != null) {
            this.inputs = new Vector<>();
            this.inputs.add(this.getValueOutput());
        }
    }

    // Same as the above Constructor, only change is that it accepts output vector
    public ExpViewElement(String label, String visibility ,Vector<String> valueOutputs, Vector<String> inputs, Resources res) {
        this.label = label == null ? "" : label;
        this.visibility = visibility;
        this.labelSize = res.getDimension(R.dimen.label_font);
        this.outputs = valueOutputs;
        this.inputs = inputs;

        //If not set otherwise, set the input buffer to be identical to the output buffer
        //This allows to receive the old user-set value after the view has changed
        if (this.inputs == null && this.outputs != null) {
            this.inputs = new Vector<>();
            this.inputs.addAll(this.getValueOutputs());
        }
    }

    //Since file format 1.21 the label may be left out: the caption and its space are omitted and the control takes the row
    public boolean hasLabel() {
        return label != null && !label.isEmpty();
    }

    //Puts a label and its control into the row (a horizontal LinearLayout): label in the left half and control in
    //the right half by default, label above the control with verticalLayout (both full width, left-aligned), or the
    //control alone across the row when there is no label. The control's own layout params are expected to hold the
    //left-half weight of the default case (groups.md, "Labels in narrow columns").
    protected void arrangeLabelAndControl(LinearLayout row, TextView labelView, View control) {
        if (!hasLabel()) {
            control.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            control.setPadding(0, control.getPaddingTop(), control.getPaddingRight(), control.getPaddingBottom());
            row.addView(control);
            return;
        }
        if (verticalLayout) {
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.START);
            labelView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            labelView.setPadding(0, labelView.getPaddingTop(), 0, labelView.getPaddingBottom());
            control.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            control.setPadding(0, control.getPaddingTop(), control.getPaddingRight(), control.getPaddingBottom());
            row.addView(labelView);
            row.addView(control);
            return;
        }
        row.addView(labelView);
        row.addView(control);
    }

    //The label span of the web markup, empty without a label; the page lets the control take the row then
    protected String labelHTML() {
        return hasLabel() ? "<span class=\"label\">" + label + "</span>" : "";
    }

    //The CSS classes that go with the label handling of the web markup
    protected String labelLayoutClass() {
        return hasLabel() && verticalLayout ? " verticalLayout" : "";
    }

    //The children of a view group (vertical, horizontal, grid, stack, transform), null for a leaf element
    public Vector<ExpViewElement> getChildren() {
        return null;
    }

    //Whether this element is, or contains, the given element
    public boolean contains(ExpViewElement element) {
        return element == this;
    }

    //Marks an element that sits inside a stack (directly or wrapped in a transform); groups pass it on to their children
    public void setInStack(boolean inStack) {
        this.inStack = inStack;
    }

    //Called when one of the input buffers is updated
    public void notifyUpdate(boolean clear, boolean reset) {
        if (reset) {
            clear();
        }
        needsUpdate = true;
    }

    //Interface to change the label size
    protected void setLabelSize(float size) {
        this.labelSize = size;
    }

    //Abstract function to force child classes to implement createView
    //This will take a linear layout, which should be filled by this function
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        this.parent = parent;
        if (inputs != null) {
            for (String buffer : inputs) {
                if (buffer != null)
                    experiment.getBuffer(buffer).register(this);
            }
        }
        if (valueOutput != null) {
            experiment.getBuffer(valueOutput).register(this);
        }

        if(outputs != null){
            for (String buffer : outputs) {
                if (buffer != null)
                    experiment.getBuffer(buffer).register(this);
            }
        }

        if(visibility != null){
            this.visibilityBuffer = experiment.getBuffer(visibility);
            this.visibilityBuffer.register(this);
        }

        needsUpdate = true;
    }

    public void destroyView() {
    }

    public void onFragmentStop(PhyphoxExperiment experiment) {
        if (inputs != null) {
            for (String buffer : inputs) {
                if (buffer != null)
                    experiment.getBuffer(buffer).unregister(this);
            }
        }

        if (outputs != null) {
            for (String buffer : outputs) {
                if (buffer != null)
                    experiment.getBuffer(buffer).unregister(this);
            }
        }

        if(this.visibility != null){
            this.visibilityBuffer.unregister(this);
        }
    }

    //Abstract function to force child classes to implement createViewHTML
    //This will return HTML code representing the element
    protected abstract String createViewHTML();

    //This function should be called from the outside. It will take the unique HTML id and store
    //it before calling createViewHTML to create the actual HTML markup. This way createViewHTML
    //can use the ID, which only has to be set up once.
    public String getViewHTML(int id) {
        this.htmlID = id;
        return createViewHTML();
    }

    //getUpdateMode is a helper for the webinterface. It returns a string explaining how the
    //element should be updated. This helps to keep network load at bay. The string will be
    // interpreted in JavaScript and currently supports:
    //  single      the element takes a single value
    //  full        the element always needs a full array
    //  partial     the element takes an array, but new values are only appended, so the element
    //              only needs those elements of its array, that have not already been
    //              transferred
    //  input       the element is a single value input element and will write to buffers in
    //              onMayWriteToBuffers
    public abstract String getUpdateMode();

    //This function returns a JavaScript function. The argument of this function will receive
    //an array that contains fresh data to be shown to the user.
    public String setDataHTML() {
        return "function(x) {}";
    }

    protected boolean isFocused(){
        return false;
    }

    //dataComplete will be called after all set-function have been called. This signifies that
    //the element has a full dataset and may update
    public void dataComplete() {

    }

    //This function returns a JavaScript function. it will be called when all data-set-functions
    //have been called and the element may be updated
    public String dataCompleteHTML() {
        return "function() {}";
    }

    //Elements that the remote interface builds itself from a configuration (currently the graph)
    //return it here as a JSON object string; it is embedded as "graph" in the view layout.
    public String getWebGraphConfig() {
        return null;
    }

    //This returns the key name of the output dataBuffer. Called by the main loop to figure out
    //where to store user input
    protected String getValueOutput() {
        return this.valueOutput;
    }

    protected Vector<String> getValueOutputs() {
        return this.outputs;
    }

    //This is called when the analysis process is finished and the element is allowed to write to the buffers
    //Seeds the default into an EMPTY buffer or replaces a trailing NaN, unclamped, as iOS does. Must not
    //depend on the widget. Returns whether this counts as user input: seeding does, replacing a NaN does not.
    protected boolean applyDefault(PhyphoxExperiment experiment, String name, double value) {
        if (name == null)
            return false;
        DataBuffer buffer = experiment.getBuffer(name);
        if (buffer == null)
            return false;
        if (buffer.getFilledSize() == 0) {
            buffer.append(value);
            return true;
        }
        if (Double.isNaN(buffer.value)) {
            buffer.clear(false);
            buffer.append(value);
        }
        return false;
    }

    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        return false;
    }

    //This is called when the analysis process is finished and the element is allowed to read to the buffers
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        // When the view is in exclusive mode, the state is hidden to others then the caller.
        // In this case, we don't need to read the buffer to control its visibility as they are already in the hidden state.
        if(state == ExpView.State.hidden){
            return;
        }
        updateViewElementVisibility();

    }

    //This is called when the time reference for the experiment has been updated (i.e. start or stop)
    public void onTimeReferenceUpdate(ExperimentTimeReference experimentTimeReference) {
    }

    //This is called when the element should be triggered (i.e. button press triggered by the remote interface)
    public void trigger() {
    }

    //This is called, when the data for the view has been reset
    protected void clear() {

    }

    public void hide() {
        state = ExpView.State.hidden;
        if (rootView != null) {
            rootView.setVisibility(GONE);
        }
    }

    public void restore() {
        state = ExpView.State.normal;
        if (rootView != null) {
            rootView.setVisibility(VISIBLE);
            // All views were hidden in exclusive mode except the caller, so we need to update
            // the view as per the buffer value after it is restored..
            updateViewElementVisibility();
        }
    }

    //Exclusive mode for the given leaf: a leaf maximizes itself, a group (GroupElement) stretches to the
    //full height and hides the children that do not lead to the leaf
    public void maximizePath(ExpViewElement leaf) {
        if (leaf == this)
            maximize();
    }

    public void maximize() {
        if(state == ExpView.State.hidden) return;

        state = ExpView.State.maximized;
        if (rootView != null) {
            rootView.setVisibility(VISIBLE);
        }
    }

    //The input buffer's current value, or def if it has none or holds NaN, which no input control can show.
    //createView runs again when the pager resumes the fragment, so a control starts from the buffer.
    protected double bufferValueOrDefault(PhyphoxExperiment experiment, double def) {
        if (experiment == null || inputs.size() == 0)
            return def;
        DataBuffer buffer = experiment.getBuffer(inputs.get(0));
        if (buffer == null || buffer.getFilledSize() == 0 || Double.isNaN(buffer.value))
            return def;
        return buffer.value;
    }

    //Leave exclusive mode on the user's request; elements may intercept this to ask first (see GraphElement)
    public void requestLeaveExclusive() {
        if (parent != null)
            parent.leaveExclusive();
    }

    public void onViewSelected(boolean parentViewIsVisible) {

    }

    private void updateViewElementVisibility(){
        if(rootView == null){
            return;
        }
        if(visibilityBuffer == null){
            return;
        }
        if (Double.isNaN(visibilityBuffer.value) || visibilityBuffer.value <= 0) {
            if (state == ExpView.State.maximized) {
                //This prevents from leaving the user with an entirely empty UI, when an element might be maximized while it becomes hidden.
                parent.leaveExclusive();
            }
            rootView.setVisibility(GONE);
        } else {
            rootView.setVisibility(VISIBLE);
        }
    }

}
