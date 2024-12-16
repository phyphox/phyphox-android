package de.rwth_aachen.phyphox;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.DigitsKeyListener;
import android.text.style.MetricAffectingSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentContainerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Vector;

import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.camera.CameraPreviewFragment;
import de.rwth_aachen.phyphox.camera.Scrollable;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.camera.depth.DepthPreview;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel;
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls;

// expView implements experiment views, which are collections of displays and graphs that form a
// specific way to show the results of an element.

// Each view consists of one or more expViewElements, which is a base class of an element that shows
// dataBuffer data, like a simple textDisplay showing a single value or a more complex graph.
// Hence, these elements (for example graphElement or valueElement) inherit from the abstract
// expViewElement class. expViewElements may even take data from the user and report it back to a
// dataBuffer to create interactive experiments (editElement).

//Example:
//A pendulum experiment may consist of three expViews, showing (1) raw data, (2) an autocorrelation
//analysis and (3) the result values. The raw data expView would consist of three graphElements to
//show x, y and z data. The autocorrelation would consist of a graph element showing the
//autocorrelation and a valueElement showing the time of the first maximum. The result values
//finally only consist of two values showing the results of the analysis: A frequency and a period.


public class ExpView implements Serializable{

    public static enum State {
        hidden, normal, maximized;
    }

    //Remember? We are in the expView class.
    //An experiment view has a name and holds a bunch of expViewElement instances
    public String name;
    public Vector<expViewElement> elements = new Vector<>();

    //Abstract expViewElement class defining the interface for any element of an experiment view
    public abstract class expViewElement implements Serializable, BufferNotification {
        protected String label; //Each element has a label. Usually naming the data shown
        protected float labelSize; //Size of the label
        protected String valueOutput; //User input will be directed to this output, so the experiment can write it to a dataBuffer
        protected Vector<String> inputs;
        protected boolean needsUpdate = true;

        protected int htmlID; //This holds a unique id, so the element can be referenced in the webinterface via an HTML ID

        transient protected View rootView; //Holds the root view of the element

        public State state = State.normal;

        //Constructor takes the label, any buffer name that should be used an a reference to the resources
        protected expViewElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            this.label = label;
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
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
            if (inputs != null) {
                for (String buffer : inputs) {
                    if (buffer != null)
                        experiment.getBuffer(buffer).register(this);
                }
            }
            if (valueOutput != null) {
                experiment.getBuffer(valueOutput).register(this);
            }
            needsUpdate = true;
        }

        protected void destroyView() {
        }

        protected void onFragmentStop(PhyphoxExperiment experiment) {
            if (inputs != null) {
                for (String buffer : inputs) {
                    if (buffer != null)
                        experiment.getBuffer(buffer).unregister(this);
                }
            }
        }

        //Abstract function to force child classes to implement createViewHTML
        //This will return HTML code representing the element
        protected abstract String createViewHTML();

        //This function should be called from the outside. It will take the unique HTML id and store
        //it before calling createViewHTML to create the actual HTML markup. This way createViewHTML
        //can use the ID, which only has to be set up once.
        protected String getViewHTML(int id) {
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
        //  input       the element is a single value input element and will also return a single
        //              value
        protected abstract String getUpdateMode();

        //getValue is the function that retrieves a value from an input element so the main process
        //can append it to the output buffer
        protected double getValue() {
            return 0.;
        }

        //This function returns a JavaScript function. The argument of this function will receive
        //an array that contains fresh data to be shown to the user.
        protected String setDataHTML() {
            return "function(x) {}";
        }

        //dataComplete will be called after all set-function have been called. This signifies that
        //the element has a full dataset and may update
        protected void dataComplete() {

        }

        //This function returns a JavaScript function. it will be called when all data-set-functions
        //have been called and the element may be updated
        protected String dataCompleteHTML() {
            return "function() {}";
        }

        //This returns the key name of the output dataBuffer. Called by the main loop to figure out
        //where to store user input
        protected String getValueOutput() {
            return this.valueOutput;
        }

        //This is called when the analysis process is finished and the element is allowed to write to the buffers
        protected boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
            return false;
        }

        //This is called when the analysis process is finished and the element is allowed to write to the buffers
        protected void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        }

        //This is called when the time reference for the experiment has been updated (i.e. start or stop)
        protected void onTimeReferenceUpdate(ExperimentTimeReference experimentTimeReference) {
        }

        //This is called when the element should be triggered (i.e. button press triggered by the remote interface)
        protected void trigger() {
        }

        //This is called, when the data for the view has been reset
        protected void clear() {

        }

        protected void hide() {
            state = State.hidden;
            if (rootView != null) {
                rootView.setVisibility(View.GONE);
            }
        }

        protected void restore() {
            state = State.normal;
            if (rootView != null) {
                rootView.setVisibility(VISIBLE);
            }
        }

        protected void maximize() {
            state = State.maximized;
            if (rootView != null) {
                rootView.setVisibility(VISIBLE);
            }
        }

        protected void onViewSelected(boolean parentViewIsVisible) {

        }

    }

    //valueElement implements a simple text display for a single value with an unit and a given
    //format.
    public class valueElement extends expViewElement implements Serializable {
        transient private TextView tv = null;
        private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
        private double size;
        private boolean scientificNotation; //Show scientific notation instead of fixed point (1e-3 instead of 0.001)
        private int precision; //The number of significant digits
        private String formatter; //This formatter is created when scientificNotation and precision are set
        private String unit; //A string to display as unit
        private RGB color;

        protected class Mapping {
            Double min = Double.NEGATIVE_INFINITY;
            Double max = Double.POSITIVE_INFINITY;
            String str;

            protected Mapping(String str) {
                this.str = str;
            }
        }

        protected Vector<Mapping> mappings = new Vector<>();

        protected void addMapping(Mapping mapping) {
            this.mappings.add(mapping);
        }

        //Used to change size within TextView
        private class MiddleRelativeSizeSpan extends MetricAffectingSpan {
            private final float mProportion;

            public MiddleRelativeSizeSpan(float proportion) {
                mProportion = proportion;
            }

            public float getSizeChange() {
                return mProportion;
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                updateAnyState(ds);
            }

            @Override
            public void updateMeasureState(TextPaint ds) {
                updateAnyState(ds);
            }

            private void updateAnyState(TextPaint ds) {
                Rect bounds = new Rect();
                ds.getTextBounds("1A", 0, 2, bounds);
                int shift = bounds.top - bounds.bottom;
                ds.setTextSize(ds.getTextSize() * mProportion);
                ds.getTextBounds("1A", 0, 2, bounds);
                shift += bounds.bottom - bounds.top;
                ds.baselineShift += Math.round(shift/2.);
            }
        }

        //Constructor takes the same arguments as the expViewElement constructor
        //It sets a precision of 2 with fixed point notation as default and creates the formatter
        valueElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.scientificNotation = false;
            this.precision = 2;
            updateFormatter();
            this.unit = "";
            this.factor = 1.;
            this.size = 1.;
            this.color = new RGB(res.getColor(R.color.phyphox_white_50_black_50));
        }

        //Create the formatter for the notation and precision: for example  %.2e or %.2f
        protected void updateFormatter() {
            if (scientificNotation)
                formatter = "%."+precision+"e";
            else
                formatter = "%."+precision+"f";
        }

        //Interface to set scientific notation
        protected void setScientificNotation(boolean sn) {
            this.scientificNotation = sn;
            updateFormatter();
        }

        //Interface to set precision
        protected void setPrecision(int p) {
            this.precision = p;
            updateFormatter();
        }

        protected void setSize(double size) {
            this.size = size;
        }

        protected void setColor(RGB c) {
            this.color = c;
        }

        //Interface to set conversion factor. The element will show inputValue times this factor
        protected void setFactor(double factor) {
            this.factor = factor;
        }

        //Interface to set the unit string
        protected void setUnit(String unit) {
            //If there is a unit we will save the space in this string as well...
            if (unit == null || unit.equals(""))
                this.unit = "";
            else
                this.unit = " "+unit;
        }

        @Override
        //This is a single value. So the updateMode is "single"
        protected String getUpdateMode() {
            return "single";
        }

        @Override
        //Append the Android vews we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            //Create a row consisting of label and value
            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            //Create the label as textView
            TextView labelView = new TextView(c);
            labelView.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //left half should be label
            labelView.setText(this.label);
            labelView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); //Align right to the center of the row
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setPadding(0, 0, (int) labelSize / 2, 0);
            labelView.setTextColor(color.autoLightColor(res).intColor());

            //Create the value (and unit) as textView
            tv = new TextView(c);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //right half should be value+unit
            tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize*(float)size); //Align left to the center of the row
            tv.setPadding((int) labelSize / 2, 0, 0, 0);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(color.autoLightColor(res).intColor());


            //Add label and value to the row
            row.addView(labelView);
            row.addView(tv);

            //Add the row to the linear layout
            rootView = row;
            rootView.setFocusableInTouchMode(true);
            ll.addView(rootView);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <span>Label</span><span>Value</span>
        //</div>
        protected String createViewHTML(){
            String c = String.format("%08x", color.intColor()).substring(2);
            return "<div style=\"font-size:"+this.labelSize/.4+"%;color:#"+c+"\" class=\"valueElement adjustableColor\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\">"+this.label+"</span>" +
                    "<span class=\"value\"><span class=\"valueNumber\" style=\"font-size:" + (this.size*100.) + "%\"></span> <span class=\"valueUnit\">"+ this.unit + "</span></span>" +
                    "</div>";
        }

        @Override
        //We just have to send calculated value and the unit to the textView
        protected void onMayReadFromBuffers(PhyphoxExperiment experiment) {
            if (!needsUpdate)
                return;
            needsUpdate = false;

            double x = experiment.getBuffer(inputs.get(0)).value;
            if (tv != null) {
                String vStr = "";
                String uStr = "";
                if (Double.isNaN(x)) {
                    vStr = "-";
                    uStr = "";
                } else {
                    for (Mapping map : mappings)  {
                        if (x >= map.min && x <= map.max) {
                            vStr = map.str;
                            break;
                        }
                    }
                    if (vStr.isEmpty()) {
                        vStr = String.format(this.formatter, x * this.factor);
                        uStr = this.unit;
                    }
                }
                String out = vStr+uStr;

                if (size != 1.0) {
                    SpannableString sStr = new SpannableString(out);
                    sStr.setSpan(new MiddleRelativeSizeSpan(1.f/(float)size), vStr.length(), out.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(sStr);
                } else {
                    tv.setText(out);
                }
            }
        }

        @Override
        //In Javascript we just have to set the content of the value <span> to the value using jquery
        protected String setDataHTML() {
            StringBuilder sb = new StringBuilder();

            String bufferName = inputs.get(0).replace("\"", "\\\"");

            sb.append("function (data) {");
            sb.append("     if (!data.hasOwnProperty(\""+bufferName+"\"))");
            sb.append("         return;");
            sb.append(      "var x = data[\""+bufferName+"\"][\"data\"][data[\"" + bufferName + "\"][\"data\"].length-1];");
            sb.append(      "var v = null;");

            sb.append(      "if (isNaN(x) || x == null) { v = \"-\" }");
            for (Mapping map : mappings) {
                String str = map.str.replace("<","&lt;").replace(">","&gt;").replace("\"","\\\"");
                if (!map.max.isInfinite() && !map.min.isInfinite()) {
                    sb.append("else if (x >= " + map.min + " && x <= " + map.max + ") {v = \"" + str + "\";}");
                } else if (!map.max.isInfinite()) {
                    sb.append("else if (x <= " + map.max + ") {v = \"" + str + "\";}");
                } else if (!map.min.isInfinite()) {
                    sb.append("else if (x >= " + map.min + ") {v = \"" + str + "\";}");
                } else {
                    sb.append("else if (true) {v = \"" + str + "\";}");
                }
            }

            sb.append("     var valueElement = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"value\")[0];");
            sb.append("     var valueNumber = valueElement.getElementsByClassName(\"valueNumber\")[0];");
            sb.append("     var valueUnit = valueElement.getElementsByClassName(\"valueUnit\")[0];");
            sb.append("     if (v == null) {");
            sb.append("         v = (x*"+factor+").to"+(scientificNotation ? "Exponential" : "Fixed")+"("+precision+");");
            sb.append("         valueUnit.textContent = \""+ this.unit + "\";");
            sb.append("     } else {");
            sb.append("         valueUnit.textContent = \"\";");
            sb.append("     }");
            sb.append("     valueNumber.textContent = v;");
            sb.append("}");
            return sb.toString();
        }
    }

    //infoElement implements a simple static text display, which gives additional info to the user
    public class infoElement extends expViewElement implements Serializable {

        private RGB color;
        private int gravity = Gravity.START;
        private int typeface = Typeface.NORMAL;
        private float size = 1.0f;

        //Constructor takes the same arguments as the expViewElement constructor
        infoElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.color = new RGB(res.getColor(R.color.phyphox_white_100));
        }

        protected void setColor(RGB c) {
            this.color = c;
        }

        public void setFormatting(boolean bold, boolean italic, int gravity, float size) {
            this.gravity = gravity;
            if (bold && italic)
                typeface = Typeface.BOLD_ITALIC;
            else if (bold)
                typeface = Typeface.BOLD;
            else if (italic)
                typeface = Typeface.ITALIC;
            else
                typeface = Typeface.NORMAL;
            this.size = size;
        }

        @Override
        //This does not display anything. Do not update.
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        //Append the Android views we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            //Create the text as textView
            TextView textView = new TextView(c);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
//            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, context.getDimension(R.dimen.info_element_margin), context.getDisplayMetrics());
//            lllp.setMargins(0, margin, 0, margin);
            textView.setLayoutParams(lllp);
            textView.setText(this.label);
            textView.setGravity(gravity);
            textView.setTypeface(null, typeface);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.info_element_font) * size);

            textView.setTextColor(color.autoLightColor(res).intColor());

            rootView = textView;
            rootView.setFocusableInTouchMode(true);

            //Add it to the linear layout
            ll.addView(rootView);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <p>text</p>
        //</div>
        protected String createViewHTML(){
            String c = String.format("%08x", color.intColor()).substring(2);
            return "<div style=\"" +
                        "font-size:"+this.labelSize*size/.4*0.85+"%;" +
                        "color:#"+c+";" +
                        "font-weight:"+((typeface & Typeface.BOLD) > 0 ? "bold" : "normal")+";" +
                        "font-style:"+((typeface & Typeface.ITALIC) > 0 ? "italic" : "normal")+";" +
                        "text-align:"+(gravity == Gravity.END ? "end" : (gravity == Gravity.CENTER ? "center" : "start"))+";" +
                        "\" class=\"infoElement adjustableColor\" id=\"element"+htmlID+"\">" +
                    "<p>"+this.label+"</p>" +
                    "</div>";
        }

    }

    //separatorElement implements a simple spacing, optionally showing line
    public class separatorElement extends expViewElement implements Serializable {
        private RGB color = new RGB(0);

        private float height = 0.1f;

        //Label is not used
        separatorElement(String valueOutput, Vector<String> inputs, Resources res) {
            super("", valueOutput, inputs, res);
        }

        public void setColor(RGB c) {
            this.color = c;
        }

        public void setHeight(float h) {
            this.height = h;
        }

        @Override
        //This does not display anything. Do not update.
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        //Append the Android views we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            //Create the text as textView
            rootView = new View(c);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(res.getDimension(R.dimen.info_element_font)*height));
            rootView.setLayoutParams(lllp);
            rootView.setBackgroundColor(color.autoLightColor(res).intColor());

            //Add it to the linear layout
            ll.addView(rootView);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <p>text</p>
        //</div>
        protected String createViewHTML(){
            String c = String.format("%08x", color.intColor()).substring(2);
            return "<div style=\"font-size:"+this.labelSize/.4+"%;background: #"+c+";height: "+height+"em\" class=\"separatorElement adjustableColor\" id=\"element"+htmlID+"\">" +
                    "</div>";
        }

    }

    //editElement implements a simple edit box which takes a single value from the user
    public class editElement extends expViewElement implements Serializable {
        transient EditText et = null;
        private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
        private String unit; //A string to display as unit
        private double defaultValue; //This value is filled into the dataBuffer before the user enters a custom value
        private double currentValue = Double.NaN; //This value is filled into the dataBuffer before the user enters a custom value
        private boolean signed = true; //Is the user allowed to give negative values?
        private boolean decimal = true; //Is the user allowed to give non-integer values?
        private Double min = Double.NEGATIVE_INFINITY;
        private Double max = Double.POSITIVE_INFINITY;
        private boolean focused = false; //Is the element currently focused? (Updates should be blocked while the element has focus and the user is working on its content)

        private boolean triggered = true;
        private boolean editable = true;

        public String label;

        private PhyphoxExperiment phyphoxExperiment;

        private LinearLayout root_ll;
        private Context c;


        //No special constructor. Just some defaults.
        editElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.label = label;
            this.unit = "";
            this.factor = 1.;
        }

        //Interface to set the conversion factor
        protected void setFactor(double factor) {
            this.factor = factor;
        }

        //Interface to set a default value
        protected void setDefaultValue(double v) {
            this.defaultValue = v;
        }

        //Interface to set the unit string
        protected void setUnit(String unit) {
            if (unit == null || unit.equals(""))
                this.unit = "";
            else
                this.unit = unit;
        }

        //Interface to allow signed values
        protected void setSigned(boolean signed) {
            this.signed = signed;
        }

        //Interface to allow non-integer values
        protected void setDecimal(boolean decimal) {
            this.decimal = decimal;
        }

        //Interface to set limits
        protected void setLimits(double min, double max) {
            this.min = min;
            this.max = max;
        }

        protected void setEditable(boolean editable){
            this.editable = editable;
        }

        @Override
        //This is an input, so the updateMode should be "input"
        protected String getUpdateMode() {
            return "input";
        }

        @Override
        //Create the view in Android and append it to the linear layout
        protected void createView(LinearLayout ll, final Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
            super.createView(ll, c, res, parent, experiment);
            phyphoxExperiment = experiment;
            root_ll = ll;
            this.c = c;

            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setVerticalGravity(Gravity.CENTER_VERTICAL);

            //Create the label in the left half of the row
            TextView labelView = new TextView(c);
            labelView.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //Left half of the whole row
            labelView.setText(this.label);
            labelView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setPadding(0, 0, (int) labelSize / 2, 0);

            //Create a horizontal linear layout, which seperates the right half into the edit field
            //and a textView to show the unit next to the user input
            LinearLayout valueUnit = new LinearLayout(c);
            valueUnit.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //right half of the whole row
            valueUnit.setOrientation(LinearLayout.HORIZONTAL);
            valueUnit.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            //The edit box
            et = new AppCompatEditText(c) {
                @Override
                public boolean onKeyPreIme(int keyCode, KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        et.clearFocus();
                    } else if (keyCode == KeyEvent.KEYCODE_MENU) {

                    }
                    return super.onKeyPreIme(keyCode, event);
                }
            };
            et.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.7f)); //Most of the right half
            et.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);

            et.setTypeface(null, Typeface.BOLD);

            //Construct the inputType flags from our own state
            int inputType = InputType.TYPE_CLASS_NUMBER;
            StringBuilder allowedDigits = new StringBuilder();
            allowedDigits.append("0123456789");
            if (signed) {
                inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                allowedDigits.append("-");
            }
            if (decimal) {
                inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                allowedDigits.append("-.,Ee"); //Note: This is not perfect, but we get into trouble if numbers are so small that they need to be represented in scientific notation (1e-6). But then again, this is not really about securing anything...
            }
            et.setInputType(inputType);
            if (decimal) {
                et.setKeyListener(DigitsKeyListener.getInstance(allowedDigits.toString()));
                et.addTextChangedListener(new DecimalTextWatcher());
            }
            if(!editable){
                et.setInputType(InputType.TYPE_NULL);
                et.setBackgroundColor(res.getColor(R.color.cardview_dark_background));
            }


            //Start with NaN
            et.setText("NaN");

            //The unit next to the edit box
            TextView unitView = new TextView(c);
            unitView.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.3f)); //Smaller part of the right half
            unitView.setText(this.unit);
            unitView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            unitView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            unitView.setPadding(0, 0, (int) labelSize / 2, 0);
            unitView.setTypeface(null, Typeface.BOLD);

            //Add edit box and unit to the horizontal linear layout that makes up the right half of the row
            valueUnit.addView(et);
            valueUnit.addView(unitView);

            //Add label and the horizontal linear layout (edit box and unit) to the row
            row.addView(labelView);
            row.addView(valueUnit);

            rootView = row;
            rootView.setFocusableInTouchMode(true);

            //Add the row to the main linear layout passed to this function
            root_ll.addView(rootView);

            //Add a listener to the edit box to keep track of the focus
            et.setOnFocusChangeListener((v, hasFocus) -> {
                focused = hasFocus;
                if (!hasFocus) {
                    setValue(getValue()); //Write back the value actually used...
                    triggered = true;
                }
            });

            et.setOnEditorActionListener((textView, i, keyEvent) -> {
                et.clearFocus();
                return true;
            });

        }

        @Override
        //Create the HTML markup for this element
        //<div>
        //  <span>Label</span> <input /> <span>unit</span>
        //</div>
        //Note that the input is send from here as well as the AJAX-request is placed in the
        //onchange-listener in the markup
        protected String createViewHTML(){
            //Construct value restrictions in HTML5
            String restrictions = "";
            if (!signed && min < 0)
                restrictions += "min=\"0\" ";
            else if (!min.isInfinite())
                restrictions += "min=\""+(min*factor)+"\" ";
            if (!max.isInfinite())
                restrictions += "max=\""+(max*factor)+"\" ";
            if (!decimal)
                restrictions += "step=\"1\" ";

            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"editElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\">"+this.label+"</span>" +
                    "<input onchange=\"ajax('control?cmd=set&buffer="+valueOutput+"&value='+this.value/"+ factor + ")\" type=\"number\" class=\"value\" " + restrictions + " />" +
                    "<span class=\"unit\">"+this.unit+"</span>" +
                    "</div>";
        }

        @Override
        //Get the value from the edit box (Note, that we have to divide by the factor to achieve a
        //use that is consistent with that of the valueElement
        protected double getValue() {
            if (et == null || focused)
                return currentValue;
            try {
                currentValue = Double.valueOf(et.getText().toString().replace(",", "."))/factor;
                if (!signed && currentValue < 0.0) {
                    currentValue = Math.abs(currentValue); //Another safety net as we cannot entirely rule out the minus sign in decimal notation because of possible scientific representation
                }
                if (currentValue < min) {
                    currentValue = min;
                }
                if (currentValue > max) {
                    currentValue = max;
                }
            } catch (Exception e) {
                return currentValue;
            }
            return currentValue;
        }

        void setValue(double v) {
            if (!focused) {
                if (Double.isNaN(v)) //If the buffer holds NaN, resort to the default value (probably the user has not entered anything yet)
                    currentValue = defaultValue;
                else
                    currentValue = v;
                if (et != null) {
                    if (decimal)
                        et.setText(String.valueOf(currentValue * factor));
                    else
                        et.setText(String.format(Locale.US, "%.0f", currentValue * factor));
                }
            }
        }

        @Override
        //If triggered, write the data to the output buffers
        //Always return zero as the analysis process does not receive the values directly
        protected boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
            if (!triggered)
                return false;
            triggered = false;
            experiment.getBuffer(inputs.get(0)).clear(false);
            experiment.getBuffer(inputs.get(0)).append(getValue());
            return true;
        }

        @Override
        //Set the value if the element is not focused
        protected void onMayReadFromBuffers(PhyphoxExperiment experiment) {
            //Enter value from buffer if it has not been changed by the user
            //This ensures, that the old value is restored if the view has to be created after the views have been switched.
            double v = experiment.getBuffer(inputs.get(0)).value;
            setValue(v);
        }

        @Override
        protected void clear() {
            triggered = true;
        }

        @Override
        //The javascript function which updates the content of the input as it is updated on the phone
        protected String setDataHTML() {
            String bufferName = inputs.get(0).replace("\"", "\\\"");
            return "function (data) {" +
                    "var valueElement = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"value\")[0];" +
                    "if (!data.hasOwnProperty(\""+bufferName+"\"))" +
                    "    return;" +
                    "var x = data[\""+bufferName+"\"][\"data\"][data[\"" + bufferName + "\"][\"data\"].length-1];" +
                    "if (valueElement !== document.activeElement)" +
                    "   valueElement.value = (x*"+factor+")" +
                    "}";
        }
    }

    //buttonElement implements a simple button which writes values from inputs to outputs when triggered
    public class buttonElement extends expViewElement implements Serializable, NetworkService.RequestCallback {
        private Vector<DataInput> inputs = null;
        private Vector<DataOutput> outputs = null;
        private Vector<String> triggers = null;
        private List<NetworkConnection> networkConnections = null;
        private boolean triggered = false;
        private ExpViewFragment parent;

        //No special constructor.
        buttonElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
        }

        protected void setIO(Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }

        protected void setTriggers(Vector<String> triggers) {
            this.triggers = triggers;
        }

        @Override
        //This is not automatically updated, but triggered by the user, so it's "none"
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        //Create the view in Android and append it to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            this.parent =parent;

            networkConnections = experiment.networkConnections;

            //The button
            Button b = new Button(c);

            LinearLayout.LayoutParams vglp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
//            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, context.getDimension(R.dimen.info_element_margin), context.getDisplayMetrics());
//            vglp.setMargins(0, margin, 0, 0);
            vglp.gravity = Gravity.CENTER;

            b.setLayoutParams(vglp);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            b.setText(this.label);

            //Add the button to the main linear layout passed to this function
            rootView = b;
            ll.addView(rootView);

            //Add a listener to the button to get the trigger
            b.setOnClickListener(new View.OnClickListener() {
                 @Override
                 public void onClick(View view) {
                     trigger();
                 }
            });

        }

        @Override
        protected void trigger() {
            triggered = true;
            for (String t : triggers) {
                for (NetworkConnection nc : networkConnections) {
                    if (nc.id.equals(t)) {
                        List<NetworkService.RequestCallback> requestCallbacks = new ArrayList<>();
                        requestCallbacks.add(this);
                        nc.execute(requestCallbacks);
                        ((Button)rootView).setEnabled(false);
                        ((Button)rootView).setAlpha(0.5f);
                    }
                }
            }
        }

        public void requestFinished(NetworkService.ServiceResult result) {
            if (parent == null)
                return;
            parent.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((Button)rootView).setEnabled(true);
                    ((Button)rootView).setAlpha(1f);
                }
            });
        }

        @Override
        //If triggered, write the data to the output buffers
        //Always return zero as the analysis process does not receive the values directly
        protected boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
            if (!triggered)
                return false;
            triggered = false;
            if (inputs == null || outputs == null)
                return false;
            for (int i = 0; i < inputs.size(); i++) {
                if  (i >= outputs.size())
                    continue;
                if (outputs.get(i).buffer == null)
                    continue;
                outputs.get(i).clear(false);
                if (inputs.get(i).isBuffer && inputs.get(i).buffer != null)
                    outputs.get(i).append(inputs.get(i).getArray(), inputs.get(i).getFilledSize());
                else if (!inputs.get(i).isEmpty)
                    outputs.get(i).append(inputs.get(i).getValue());
            }
            return true;
        }

        @Override
        //Create the HTML markup for this element
        //<div>
        //  <span>Label</span> <input /> <span>unit</span>
        //</div>
        //Note that the input is send from here as well as the AJAX-request is placed in the
        //onchange-listener in the markup
        protected String createViewHTML(){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"buttonElement\" id=\"element"+htmlID+"\">" +
                    "<button onclick=\"ajax('control?cmd=trigger&element="+htmlID+"');\">" + this.label +"</button>" +
                    "</div>";
        }
    }

    //graphElement implements a graph that displays y vs. x arrays from the dataBuffer
    //This class mostly wraps the graphView, which (being rather complex) is implemented in its own
    //class. See graphView.java...
    public class graphElement extends expViewElement implements Serializable {
        private final graphElement self;
        transient private ExpViewFragment parent = null;
        transient private GraphView gv = null;
        transient private InteractiveGraphView interactiveGV = null;
        private double aspectRatio; //The aspect ratio defines the height of the graph view based on its width (aspectRatio=width/height)
        transient private FloatBufferRepresentation[] dataX; //The x data to be displayed
        transient private FloatBufferRepresentation[] dataY; //The y data to be displayed
        transient private List<ExperimentTimeReferenceSet>[] timeReferencesX;
        transient private List<ExperimentTimeReferenceSet>[] timeReferencesY;
        private double dataMinX, dataMaxX, dataMinY, dataMaxY, dataMinZ, dataMaxZ;

        private boolean isExclusive = false;
        private int margin;

        private Vector<GraphView.Style> style = new Vector<>(); //Show lines instead of points?
        private Vector<Integer> mapWidth = new Vector<>();
        private Vector<Integer> colorScale = new Vector<>();
        private int historyLength = 1; //If set to n > 1 the graph will also show the last n sets in a different color
        private int nCurves = 1;
        private String labelX = null; //Label for the x-axis
        private String labelY = null; //Label for the y-axis
        private String labelZ = null; //Label for the z-axis
        private String unitX = null; //Label for the x-axis
        private String unitY = null; //Label for the y-axis
        private String unitZ = null; //Label for the z-axis
        private String unitYX = null; //Unit for slope (i.e. y/x)
        private boolean partialUpdate = false; //Allow partialUpdate of newly added data points instead of transfering the whole dataset each time (web-interface)
        private boolean timeOnX = false; //x-axis is time axis?
        private boolean timeOnY = false; //y-axis is time axis?
        private boolean absoluteTime = false; //Use system time as default?
        private boolean linearTime = false; //time data is not given in experiment time (which pauses with the experiment) but as seconds since 1970 (ignoring pauses)
        private boolean hideTimeMarkers = false; //Do not show the red markers that indicate times while the phyphox experiment was not running.
        private boolean logX = false; //logarithmic scale for the x-axis?
        private boolean logY = false; //logarithmic scale for the y-axis?
        private boolean logZ = false; //logarithmic scale for the z-axis?
        private boolean suppressScientificNotation = false;
        private int xPrecision = -1;
        private int yPrecision = -1;
        private int zPrecision = -1;
        private Vector<Double> lineWidth = new Vector<>();
        private Vector<RGB> color = new Vector<>();

        private String gridColor;

        GraphView.scaleMode scaleMinX = GraphView.scaleMode.auto;
        GraphView.scaleMode scaleMaxX = GraphView.scaleMode.auto;
        GraphView.scaleMode scaleMinY = GraphView.scaleMode.auto;
        GraphView.scaleMode scaleMaxY = GraphView.scaleMode.auto;
        GraphView.scaleMode scaleMinZ = GraphView.scaleMode.auto;
        GraphView.scaleMode scaleMaxZ = GraphView.scaleMode.auto;

        double minX = 0.;
        double maxX = 0.;
        double minY = 0.;
        double maxY = 0.;
        double minZ = 0.;
        double maxZ = 0.;

        boolean followX = false;

        GraphView.ZoomState zoomState = null;

        final String warningText;

        //Quite usual constructor...
        graphElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.self = this;

            margin = res.getDimensionPixelSize(R.dimen.activity_vertical_margin);

            aspectRatio = 2.5;
            gridColor = String.format("%08x", res.getColor(R.color.phyphox_white_50_black_50)).substring(2);
            nCurves = (inputs.size()+1)/2;

            for (int i = 0; i < nCurves; i++) {
                color.add(new RGB(res.getColor(R.color.phyphox_primary)));
                lineWidth.add(1.0);
                style.add(GraphView.Style.lines);
                mapWidth.add(0);
                dataX = new FloatBufferRepresentation[nCurves];
                dataY = new FloatBufferRepresentation[nCurves];
                timeReferencesX =  new ArrayList[nCurves];
                timeReferencesY =  new ArrayList[nCurves];
            }

            warningText = res.getString(R.string.remoteColorMapWarning).replace("'", "\\'");
        }

        //Interface to change the height of the graph
        protected void setAspectRatio(double aspectRatio) {
            this.aspectRatio = aspectRatio;
        }

        protected void setLineWidth(double lineWidth, int i) {
            this.lineWidth.set(i, lineWidth);
            if (gv != null)
                gv.setLineWidth(lineWidth, i);
        }

        protected void setLineWidth(double lineWidth) {
            for (int i = 0; i < nCurves || i < historyLength; i++)
                setLineWidth(lineWidth, i);
        }

        protected void setColor(RGB color, int i, Resources res) {
            this.color.set(i, color);
            if (gv != null)
                gv.setColor(color.autoLightColor(res).intColor(), i);
        }

        protected void setColor(RGB color, Resources res) {
            for (int i = 0; i < nCurves || i < historyLength; i++) {
                setColor(color, i, res);
            }
        }

        protected void refreshColors(Resources res) {
            for (int i = 0; i < nCurves || i < historyLength; i++)
                gv.setColor(this.color.get(i).autoLightColor(res).intColor(), i);
        }

        protected void setStyle(GraphView.Style style, int i) {
            this.style.set(i, style);
            if (gv != null)
                gv.setStyle(style, i);
        }

        //Interface to switch between points and lines
        protected void setStyle(GraphView.Style style) {
            for (int i = 0; i < nCurves || i < historyLength; i++)
                setStyle(style, i);
        }

        protected void setColorScale(Vector<Integer> scale) {
            this.colorScale = scale;
            if (gv != null)
                gv.setColorScale(scale);
        }

        protected void setMapWidth(int width, int i) {
            this.mapWidth.set(i, width);
            if (gv != null)
                gv.setMapWidth(width, i);
        }

        protected void setMapWidth(int width) {
            for (int i = 0; i < nCurves || i < historyLength; i++)
                setMapWidth(width, i);
        }

        public void setScaleModeX(GraphView.scaleMode minMode, double minV, GraphView.scaleMode maxMode, double maxV) {
            this.scaleMinX = minMode;
            this.scaleMaxX = maxMode;
            this.minX = minV;
            this.maxX = maxV;
            if (gv != null)
                gv.setScaleModeX(minMode, minV, maxMode, maxV);
        }

        public void setScaleModeY(GraphView.scaleMode minMode, double minV, GraphView.scaleMode maxMode, double maxV) {
            this.scaleMinY = minMode;
            this.scaleMaxY = maxMode;
            this.minY = minV;
            this.maxY = maxV;
            if (gv != null)
                gv.setScaleModeY(minMode, minV, maxMode, maxV);
        }

        public void setScaleModeZ(GraphView.scaleMode minMode, double minV, GraphView.scaleMode maxMode, double maxV) {
            this.scaleMinZ = minMode;
            this.scaleMaxZ = maxMode;
            this.minZ = minV;
            this.maxZ = maxV;
            if (gv != null)
                gv.setScaleModeZ(minMode, minV, maxMode, maxV);
        }

        public void setFollowX(boolean followX) {
            this.followX = followX;
            if (followX) {
                this.scaleMinX = GraphView.scaleMode.fixed;
                this.scaleMaxX = GraphView.scaleMode.fixed;
                this.partialUpdate = true;
            }
        }

        //Interface to set a history length
        protected void setHistoryLength(int hl) {
            this.historyLength = hl;
            if (gv != null)
                gv.setHistoryLength(hl);
            if (hl > 1) {
                dataX = new FloatBufferRepresentation[1];
                dataY = new FloatBufferRepresentation[1];
            }
        }

        //Interface to set the axis labels.
        protected void setLabel(String labelX, String labelY, String labelZ, String unitX, String unitY, String unitZ, String unitYX) {
            this.labelX = labelX;
            this.labelY = labelY;
            this.labelZ = labelZ;
            this.unitX = unitX;
            this.unitY = unitY;
            this.unitZ = unitZ;
            this.unitYX = unitYX;
            if (gv != null)
                gv.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);
        }

        protected void setTimeAxes(boolean timeOnX, boolean timeOnY, boolean absoluteTime, boolean linearTime, boolean hideTimeMarkers) {
            this.timeOnX = timeOnX;
            this.timeOnY = timeOnY;
            this.absoluteTime = absoluteTime;
            this.linearTime = linearTime;
            this.hideTimeMarkers = hideTimeMarkers;
        }

        protected void setSuppressScientificNotation(boolean suppressScientificNotation) {
            this.suppressScientificNotation = suppressScientificNotation;
        }

        //Interface to set log scales
        protected void setLogScale(boolean logX, boolean logY, boolean logZ) {
            this.logX = logX;
            this.logY = logY;
            this.logZ = logZ;
        }

        protected void setPrecision(int xPrecision, int yPrecision, int zPrecision) {
            this.xPrecision = xPrecision;
            this.yPrecision = yPrecision;
            this.zPrecision = zPrecision;
        }

        //Interface to set partial updates vs. full updates of the data sets
        protected void setPartialUpdate(boolean pu) {
            this.partialUpdate = pu;
            if (gv != null)
                gv.graphSetup.incrementalX = pu;
        }

        @Override
        //The update mode is "partial" or "full" as this element uses arrays. The experiment may
        //decide if partial updates are sufficient
        protected String getUpdateMode() {
            if (partialUpdate) {
                if (style.get(0) == GraphView.Style.mapXY)
                    return "partialXYZ";
                else
                    return "partial";
            } else
                return "full";
        }

        @Override
        //Create the actual view in Android
        protected void createView(LinearLayout ll, Context c, Resources res, final ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            this.parent = parent;

            Context ctx = c;
            Activity act = null;
            while (ctx instanceof ContextWrapper) {
                if (ctx instanceof Activity) {
                    act = (Activity) ctx;
                }
                ctx = ((ContextWrapper)ctx).getBaseContext();
            }

            //Create the graphView
            interactiveGV = new InteractiveGraphView(c);
            gv = interactiveGV.graphView;
            if (zoomState != null)
                gv.zoomState = zoomState;
            else
                zoomState = gv.zoomState;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            interactiveGV.setLayoutParams(lp);
            interactiveGV.setLabel(this.label);

            if (act instanceof Experiment) {
                DataExport dataExport = new DataExport(experiment);

                DataExport.ExportSet set = dataExport.new ExportSet(this.label);
                for (int i = 0; i < inputs.size(); i+=2) {
                    if (i+1 < inputs.size() && inputs.get(i+1) != null)
                        set.addSource(this.labelX + (i > 1 ? " " + (i / 2 + 1) : "") + (unitX != null && !unitX.isEmpty() ? " (" + unitX +")" : ""), inputs.get(i+1));

                    if (style.get(i/2) == GraphView.Style.mapZ)
                        set.addSource((this.labelZ != null ? this.labelZ : "z") + (unitZ != null && !unitZ.isEmpty() ? " (" + unitZ + ")" : ""), inputs.get(i));
                    else
                        set.addSource(this.labelY + (i > 1 ? " " + (i / 2 + 1) : "") + (unitY != null && !unitY.isEmpty() ? " (" + unitY + ")" : ""), inputs.get(i));
                }
                dataExport.addSet(set);

                interactiveGV.assignDataExporter(dataExport);
            }

            setTimeReferences(experiment.experimentTimeReference);

            //Send our parameters to the graphView isntance
            if (historyLength > 1)
                gv.setHistoryLength(historyLength);
            else
                gv.setCurves(nCurves);

            for (int i = 0; i < nCurves; i++) {
                gv.setStyle(style.get(i), i);
                gv.setMapWidth(mapWidth.get(i), i);
                gv.setLineWidth(lineWidth.get(i), i);
                gv.setColor(color.get(i).autoLightColor(res).intColor(), i);
            }
            gv.graphSetup.incrementalX = partialUpdate;
            gv.setAspectRatio(aspectRatio);
            gv.setColorScale(colorScale);
            gv.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
            gv.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
            gv.setScaleModeZ(scaleMinZ, minZ, scaleMaxZ, maxZ);
            gv.setFollowX(followX);
            gv.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);
            gv.setTimeAxes(timeOnX, timeOnY);
            gv.setSuppressScientificNotation(suppressScientificNotation);
            gv.setAbsoluteTime(absoluteTime);
            gv.setLinearTime(linearTime);
            gv.setHideTimeMarkers(hideTimeMarkers);
            gv.setLogScale(logX, logY, logZ);
            interactiveGV.allowLogX = logX;
            interactiveGV.allowLogY = logY;
            gv.setPrecision(xPrecision, yPrecision, zPrecision);

            interactiveGV.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (self.parent != null) {
                        if (isExclusive) {
                            interactiveGV.leaveDialog(self.parent, inputs.size() > 1 ? inputs.get(1) : null, inputs.size() > 0 ? inputs.get(0) : null, unitX, unitY);
                        } else {
                            interactiveGV.requestFocus();
                            self.parent.requestExclusive(self);
                        }
                    }
                }
            });

            //Add the wrapper layout to the linear layout given to this function
            rootView = interactiveGV;
            rootView.setFocusableInTouchMode(false);
            ll.addView(rootView);

        }

        @Override
        public void onFragmentStop(PhyphoxExperiment experiment) {
            super.onFragmentStop(experiment);

            if (interactiveGV != null)
                interactiveGV.stop();
            gv = null;
            interactiveGV = null;
        }

        @Override
        //Create the HTML markup. We use the flot library to plot in JavaScript, so there is not
        //as much to do here as one might expect
        //<div>
        //<span>Label</span>
        //<div>graph</div>
        //</div>
        protected String createViewHTML(){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"graphElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span>" +
                    (this.style.get(0) == GraphView.Style.mapXY ? "<div class=\"warningIcon\" onclick=\"alert('"+warningText+"')\"></div>" : "")+
                    "<div class=\"graphBox\"><div class=\"graphRatio\" style=\"padding-top: "+100.0/this.aspectRatio+"%\"></div><div class=\"graph\"><canvas></canvas></div></div>" +
                    "</div>";
        }

        @Override
        protected void onMayReadFromBuffers(PhyphoxExperiment experiment) {
            if (!needsUpdate)
                return;
            needsUpdate = false;

            for (int i = 0; i < inputs.size(); i+=2) {
                if (inputs.size() > i+1) {
                    DataBuffer x = experiment.getBuffer(inputs.get(i+1));
                    if (x != null) {
                        if (timeOnX)
                            timeReferencesX[i/2] = x.getExperimentTimeReferenceSets(linearTime);
                        if (style.get(i/2) == GraphView.Style.hbars)
                            dataX[i/2] = x.getFloatBufferBarValue();
                        else if (style.get(i/2) == GraphView.Style.vbars)
                            dataX[i/2] = x.getFloatBufferBarAxis(lineWidth.get(i/2));
                        else
                            dataX[i/2] = x.getFloatBuffer();
                        if (style.get(i/2) != GraphView.Style.mapZ) {
                            if (i == 0) {
                                dataMinX = x.getMin();
                                dataMaxX = x.getMax();
                            } else {
                                dataMinX = Math.min(dataMinX, x.getMin());
                                dataMaxX = Math.max(dataMaxX, x.getMax());
                            }
                        }
                    } else {
                        dataX[i/2] = null;
                    }
                }

                DataBuffer y = experiment.getBuffer(inputs.get(i));
                if (y != null) {
                    if (timeOnY)
                        timeReferencesY[i/2] = y.getExperimentTimeReferenceSets(linearTime);
                    if (style.get(i/2) == GraphView.Style.hbars)
                        dataY[i/2] = y.getFloatBufferBarAxis(lineWidth.get(i/2));
                    else if (style.get(i/2) == GraphView.Style.vbars)
                        dataY[i/2] = y.getFloatBufferBarValue();
                    else
                        dataY[i/2] = y.getFloatBuffer();
                    if (style.get(i/2) != GraphView.Style.mapZ) {
                        if (i == 0) {
                            dataMinY = y.getMin();
                            dataMaxY = y.getMax();
                        } else {
                            dataMinY = Math.min(dataMinY, y.getMin());
                            dataMaxY = Math.max(dataMaxY, y.getMax());
                        }
                    } else {
                        dataMinZ = y.getMin();
                        dataMaxZ = y.getMax();
                    }
                } else {
                    dataY[i/2] = null;
                }
            }
        }

        private void setTimeReferences(ExperimentTimeReference experimentTimeReference) {
            if (gv != null) {
                List<Double> starts = new ArrayList<>();
                List<Double> stops = new ArrayList<>();
                double trStop = Double.NaN;
                long trStopSystemTime = 0;
                List<Double> systemTimeReferenceGap = new ArrayList<>();
                long totalTimeReferenceGap = 0;
                for (ExperimentTimeReference.TimeMapping tm : experimentTimeReference.timeMappings) {
                    if (tm.event == ExperimentTimeReference.TimeMappingEvent.START) {
                        starts.add(tm.experimentTime);
                        stops.add(trStop);
                        if (!Double.isNaN(trStop))
                            totalTimeReferenceGap += tm.systemTime - trStopSystemTime;
                        systemTimeReferenceGap.add(totalTimeReferenceGap*0.001);
                        trStop = Double.NaN;
                    } else if (tm.event == ExperimentTimeReference.TimeMappingEvent.PAUSE) {
                        trStop = tm.experimentTime;
                        trStopSystemTime = tm.systemTime;
                    }
                }
                if (!Double.isNaN(trStop)) {
                    starts.add(Double.NaN);
                    stops.add(trStop);
                }
                gv.setTimeRanges(starts, stops, systemTimeReferenceGap);
            }
        }

        @Override
        protected void onTimeReferenceUpdate(ExperimentTimeReference experimentTimeReference) {
            setTimeReferences(experimentTimeReference);
        }

        @Override
        //Return a javascript function which stores the x data array for later use
        protected String setDataHTML() {
            StringBuilder sb = new StringBuilder();
            sb.append("function (data) {");
            sb.append("     elementData[" + htmlID + "][\"datasets\"] = [];");
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null)
                    continue;
                sb.append("if (!data.hasOwnProperty(\""+inputs.get(i).replace("\"", "\\\"")+"\"))");
                sb.append("    return;");
                sb.append("elementData["+htmlID+"][\"datasets\"]["+i+"] = data[\""+inputs.get(i).replace("\"", "\\\"")+"\"];");
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        //Data complete, let's send it to the graphView
        //Also clear the data afterwards to avoid sending it multiple times if it is not updated for
        //some reason
        protected void dataComplete() {
            super.dataComplete();

            if (gv == null)
                return;
            if (dataY[0] != null) {
                if (dataX[0] != null) {
                    gv.addGraphData(dataY, dataMinY, dataMaxY, dataX, dataMinX, dataMaxX, dataMinZ, dataMaxZ, timeReferencesX, timeReferencesY);
                    dataX[0] = null;
                } else
                    gv.addGraphData(dataY, dataMinY, dataMaxY);
                dataY[0] = null;
            }
        }

        @Override
        //This looks pretty ugly and indeed needs a clean-up...
        //This function returns a javascript function which updates the flot chart.
        //So we have to set-up some JSON objects to define the graph, put it into the JavaScript
        //function (which has to setup some JSON itself) and return the whole nightmare. There
        //certainly is a way to beautify this, but it's not too obvious...
        protected String dataCompleteHTML() {
            String rescale = "";
            String scaleX = "";
            if (scaleMinX == GraphView.scaleMode.fixed && !Double.isNaN(minX))
                scaleX += "\"min\":" + minX + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.min = minX;";
            if (scaleMaxX == GraphView.scaleMode.fixed && !Double.isNaN(maxX))
                scaleX += "\"max\":" + maxX + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.max = maxX;";
            String scaleY = "";
            if (scaleMinY == GraphView.scaleMode.fixed && !Double.isNaN(minY))
                scaleY += "\"min\":" + minY + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.yAxes[0].ticks.min = minY;";
            if (scaleMaxY == GraphView.scaleMode.fixed && !Double.isNaN(maxY))
                scaleY += "\"max\":" + maxY + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.yAxes[0].ticks.max = maxY;";

            String scaleZ = "";
            String colorScale = "[";
            if (this.style.get(0) == GraphView.Style.mapXY) {
                if (scaleMinZ == GraphView.scaleMode.fixed && !Double.isNaN(minZ))
                    scaleZ += "minZ = " + minZ + ";";
                if (scaleMaxZ == GraphView.scaleMode.fixed && !Double.isNaN(maxZ))
                    scaleZ += "maxZ = " + maxZ + ";";
                scaleZ += "elementData["+htmlID+"][\"graph\"].logZ = " + (this.logZ ? "true" : "false") + ";";
                scaleZ += "elementData["+htmlID+"][\"graph\"].minZ = minZ;";
                scaleZ += "elementData["+htmlID+"][\"graph\"].maxZ = maxZ;";

                boolean first = true;
                for (Integer color : this.gv.graphSetup.colorScale) {
                    if (first)
                        first = false;
                    else
                        colorScale += ",";
                    colorScale += (color & 0xffffffffL);
                }
            }
            colorScale += "]";

            final String type = this.style.get(0) == GraphView.Style.mapXY ? "colormap" : "scatter";

            String styleDetection = "switch (i/2) {";
            String graphSetup = "[";
            for (int i = 0; i < inputs.size(); i+=2) {

                graphSetup +=   "{"+
                                    "type: \"" + type +"\"," +
                                    "showLine: "+ (style.get(i/2) == GraphView.Style.dots || style.get(i/2) == GraphView.Style.mapXY ? "false" : "true") +"," +
                                    "fill: "+(style.get(i/2) == GraphView.Style.vbars || style.get(i/2) == GraphView.Style.hbars ? "\"origin\"" : "false")+"," +
                                    "pointRadius: "+ (style.get(i/2) == GraphView.Style.dots ? 2.0*lineWidth.get(i/2) : 0) +"*scaleFactor," +
                                    "pointHitRadius: "+ (4.0*lineWidth.get(i/2)) +"*scaleFactor," +
                                    "pointHoverRadius: "+ (4.0*lineWidth.get(i/2)) +"*scaleFactor," +
                                    "lineTension: 0," +
                                    "borderCapStyle: \"butt\"," +
                                    "borderJoinStyle: \"round\"," +
                                    "spanGaps: false," +
                                    "borderColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2).intColor()).substring(2) + "\")," +
                                    "backgroundColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2).intColor()).substring(2) + "\")," +
                                    "borderWidth: " + (style.get(i/2) == GraphView.Style.vbars || style.get(i/2) == GraphView.Style.hbars ? 0.0 : lineWidth.get(i/2)) +
                                    "*scaleFactor," +
                                    "xAxisID: \"xaxis\"," +
                                    "yAxisID: \"yaxis\"" +
                                "},";

                styleDetection += "case " + (i/2) + ": type = \"" + style.get(i/2) + "\"; lineWidth = " + lineWidth.get(i / 2) + "*scaleFactor; break;";
            }
            styleDetection += "}";
            graphSetup += "],";

            return "function () {" +
                        "if (elementData["+htmlID+"][\"datasets\"].length < 1)" +
                            "return;" +
                        "var changed = false;" +
                        "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i++) {" +
                            "if (elementData["+htmlID+"][\"datasets\"][i][\"changed\"])" +
                                "changed = true;" +
                        "}" +
                        "if (!changed)" +
                            "return;" +
                        "var d = [];" +
                        "var minX = Number.POSITIVE_INFINITY; " +
                        "var maxX = Number.NEGATIVE_INFINITY; " +
                        "var minY = Number.POSITIVE_INFINITY; " +
                        "var maxY = Number.NEGATIVE_INFINITY; " +
                        "var minZ = Number.POSITIVE_INFINITY; " +
                        "var maxZ = Number.NEGATIVE_INFINITY; " +
                        "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i+=2) {" +
                            "d[i/2] = [];" +
                            "var xIndexed = ((i+1 >= elementData["+htmlID+"][\"datasets\"].length) || elementData["+htmlID+"][\"datasets\"][i+1][\"data\"].length == 0);" +
                            "var type;" +
                            "var lineWidth;" +
                            styleDetection +
                            "if (type == \""+ GraphView.Style.mapZ+"\" || (type == \""+ GraphView.Style.mapXY+"\" && elementData["+htmlID+"][\"datasets\"].length < i+2)) {" +
                                "continue;" +
                            "}" +
                            "var lastX = false;" +
                            "var lastY = false;" +
                            "var nElements = elementData["+htmlID+"][\"datasets\"][i][\"data\"].length;" +
                            "if (!xIndexed)" +
                            "   nElements = Math.min(nElements, elementData[" + htmlID + "][\"datasets\"][i+1][\"data\"].length);" +
                            "if (type == \""+ GraphView.Style.mapXY+"\")" +
                                "nElements = Math.min(nElements, elementData[" + htmlID + "][\"datasets\"][i+2][\"data\"].length);" +
                            "for (j = 0; j < nElements; j++) {" +
                                "var x = xIndexed ? j : elementData["+htmlID+"][\"datasets\"][i+1][\"data\"][j];"+
                                "var y = elementData[" + htmlID + "][\"datasets\"][i][\"data\"][j];" +
                                "if (x < minX)" +
                                "    minX = x;" +
                                "if (x > maxX)" +
                                "    maxX = x;" +
                                "if (y < minY)" +
                                "    minY = y;" +
                                "if (y > maxY)" +
                                "    maxY = y;" +
                                "if (type == \""+ GraphView.Style.vbars+"\") {" +
                                    "if (lastX !== false && lastY !== false) {"+
                                        "var offset = (x-lastX)*(1.0-lineWidth)/2.;" +
                                        "d[i/2][j*3+0] = {x: lastX+offset, y: lastY};" +
                                        "d[i/2][j*3+1] = {x: x-offset, y: lastY};" +
                                        "d[i/2][j*3+2] = {x: NaN, y: NaN};" +
                                    "}"+
                                "} else if (type == \""+ GraphView.Style.hbars+"\") {" +
                                    "if (lastX !== false && lastY !== false) {"+
                                        "var offset = (y-lastX)*(1.0-lineWidth)/2.;" +
                                        "d[i/2][j*3+0] = {x: lastX, y: lastY+offset};" +
                                        "d[i/2][j*3+1] = {x: lastX, y: y-offset};" +
                                        "d[i/2][j*3+2] = {x: NaN, y: NaN};" +
                                    "}"+
                                "} else if (type == \""+ GraphView.Style.mapXY+"\") {" +
                                    "var z = elementData[" + htmlID + "][\"datasets\"][i+2][\"data\"][j];" +
                                    "if (z < minZ)" +
                                    "    minZ = z;" +
                                    "if (z > maxZ)" +
                                    "    maxZ = z;" +
                                    "d[i/2][j] = {x: x, y: y, z: z};" +
                                "} else {" +
                                    "d[i/2][j] = {x: x, y: y};" +
                                "}" +
                                "lastX = x;" +
                                "lastY = y;" +
                            "}" +

                        "}" +
                        "if (minX > maxX) {" +
                            "minX = 0;" +
                            "maxX = 1;" +
                        "}" +
                        "if (minY > maxY) {" +
                            "minY = 0;" +
                            "maxY = 1;" +
                        "}" +
                        "if (minZ > maxZ) {" +
                            "minZ = 0;" +
                            "maxZ = 1;" +
                        "}" +

                        "if (!elementData["+htmlID+"][\"graph\"]) {" +
                            "var ctx = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"graph\")[0].getElementsByTagName(\"canvas\")[0];" +
                            "elementData["+htmlID+"][\"graph\"] = new Chart(ctx, {" +
                                "type: \"" + type + "\"," +
                                "mapwidth: "+this.mapWidth.get(0)+"," +
                                "colorscale: " + colorScale + "," +
                                "data: {datasets: "+
                                    graphSetup +
                                "}," +
                                "options: {" +
                                    "responsive: true, " +
                                    "maintainAspectRatio: false, " +
                                    "animation: false," +
                                    "legend: false," +
                                    "tooltips: {" +
                                    "    titleFontSize: 15*scaleFactor," +
                                    "    bodyFontSize: 15*scaleFactor," +
                                    "    mode: \"nearest\"," +
                                    "    intersect: " + (this.style.get(0) == GraphView.Style.mapXY ? "false" : "true") + "," +
                                        "callbacks: {" +
                                        "   title: function() {}," +
                                        "   label: function(tooltipItem, data) {" +
                                        "       var lines = [];" +
                                        "       lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].x + \""+this.unitX + "\");" +
                                        "       lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].y + \""+this.unitY + "\");" +
                                        (this.style.get(0) == GraphView.Style.mapXY ? "lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].z + \""+this.unitZ + "\");" : "") +
                                        "       return lines;" +
                                        "   }" +
                                        "}" +
                                    "}," +
                                    "hover: {" +
                                    "    mode: \"nearest\"," +
                                    "    intersect: " + (this.style.get(0) == GraphView.Style.mapXY ? "false" : "true") + "," +
                                    "}, " +
                                    "scales: {" +
                                        "xAxes: [{" +
                                            "id: \"xaxis\"," +
                                            "type: \""+(logX && !(this.style.get(0) == GraphView.Style.mapXY) ? "logarithmic" : "linear")+"\"," +
                                            "position: \"bottom\"," +
                                            "gridLines: {" +
                                                "color: adjustableColor(\"#"+gridColor+"\")," +
                                                "zeroLineColor: adjustableColor(\"#"+gridColor+"\")," +
                                                "tickMarkLength: 0," +
                                            "}," +
                                            "scaleLabel: {" +
                                                "display: true," +
                                                "labelString: \""+this.labelX+(this.unitX != null && !this.unitX.isEmpty() ? " (" + this.unitX + ")" : "")+"\"," +
                                                "fontColor: adjustableColor(\"#ffffff\")," +
                                                "fontSize: 15*scaleFactor," +
                                                "padding: 0, "+
                                            "}," +
                                            "ticks: {" +
                                                "fontColor: adjustableColor(\"#ffffff\")," +
                                                "fontSize: 15*scaleFactor," +
                                                "padding: 3*scaleFactor, "+
                                                "autoSkip: true," +
                                                "maxTicksLimit: 10," +
                                                "maxRotation: 0," +
                                                scaleX+
                                            "}," +
                                            "afterBuildTicks: filterEdgeTicks" +
                                        "}]," +
                                        "yAxes: [{" +
                                            "id: \"yaxis\"," +
                                            "type: \""+(logX && !(this.style.get(0) == GraphView.Style.mapXY) ? "logarithmic" : "linear")+"\"," +
                                            "position: \"bottom\"," +
                                            "gridLines: {" +
                                                "color: adjustableColor(\"#"+gridColor+"\")," +
                                                "zeroLineColor: adjustableColor(\"#"+gridColor+"\")," +
                                                "tickMarkLength: 0," +
                                            "}," +
                                            "scaleLabel: {" +
                                                "display: true," +
                                                "labelString: \""+this.labelY+(this.unitY != null && !this.unitY.isEmpty() ? " (" + this.unitY + ")" : "")+"\"," +
                                                "fontColor: adjustableColor(\"#ffffff\")," +
                                                "fontSize: 15*scaleFactor," +
                                                "padding: 3*scaleFactor, "+
                                            "}," +
                                            "ticks: {" +
                                                "fontColor: adjustableColor(\"#ffffff\")," +
                                                "fontSize: 15*scaleFactor," +
                                                "padding: 3*scaleFactor, "+
                                                "autoSkip: true," +
                                                "maxTicksLimit: 7," +
                                                scaleY+
                                            "}," +
                                            "afterBuildTicks: filterEdgeTicks" +
                                    "   }]," +
                                    "}" +
                                "}" +
                            "});" +
                        "}" +
                        "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i+=2) {" +
                            "elementData["+htmlID+"][\"graph\"].data.datasets[i/2].data = d[i/2];" +
                        "}" +
                        scaleZ +
                        rescale +
                        "elementData["+htmlID+"][\"graph\"].update();" +
                    "}";
        }

        @Override
        protected void clear() {

        }

        @Override
        protected void restore() {
            super.restore();
            if (rootView != null && interactiveGV != null && parent != null) {
                isExclusive = false;

                interactiveGV.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                interactiveGV.requestLayout();

                interactiveGV.setInteractive(false);
            }
        }

        @Override
        protected void maximize() {
            super.maximize();
            if (rootView != null && interactiveGV != null && parent != null) {
                isExclusive = true;

                interactiveGV.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                interactiveGV.requestLayout();

                interactiveGV.setInteractive(true);
            }
        }

        //Apply zoom to all graphs on the current page.
        //If min or max are NaN, they are reset
        //Follow is only allowed for x-axis if partialUpdate is set
        //If unit AND buffer are null, the zoom is applied to the same axis on all graphs
        //If unit is set, it is applied to all axes with the same unit on all graphs
        //If buffer is set, it is applied to all axes with the same buffer on all graphs
        public void applyZoom(double min, double max, boolean follow, String unit, String buffer, boolean yAxis, boolean absoluteTime) {
            if (unit != null) {
                if (unitX.equals(unit)) {
                    zoomState.minX = min;
                    zoomState.maxX = max;
                    zoomState.follows = follow;
                    if (timeOnX)
                        gv.setAbsoluteTime(absoluteTime);
                }
                if (unitY.equals(unit)) {
                    zoomState.minY = min;
                    zoomState.maxY = max;
                    if (timeOnY)
                        gv.setAbsoluteTime(absoluteTime);
                }
            } else if (buffer != null) {
                for (int i = 0; i < inputs.size(); i++) {
                    if (inputs.get(i) != null && inputs.get(i).equals(buffer)) {
                        if (i % 2 == 1) {
                            zoomState.minX = min;
                            zoomState.maxX = max;
                            zoomState.follows = follow;
                            if (timeOnX)
                                gv.setAbsoluteTime(absoluteTime);
                        } else {
                            zoomState.minY = min;
                            zoomState.maxY = max;
                            if (timeOnY)
                                gv.setAbsoluteTime(absoluteTime);
                        }
                    }
                }
            } else {
                if (!yAxis) {
                    zoomState.minX = min;
                    zoomState.maxX = max;
                    zoomState.follows = follow;
                    if (timeOnX)
                        gv.setAbsoluteTime(absoluteTime);
                } else {
                    zoomState.minY = min;
                    zoomState.maxY = max;
                    if (timeOnY)
                        gv.setAbsoluteTime(absoluteTime);
                }
            }
            gv.zoomState = zoomState;
            gv.rescale();
            gv.invalidate();
        }
    }

    //depthGUI implements a camera preview and interface to customize the data acquisition of the
    // depth sensor (LiDAR/ToF)
    public class depthGuiElement extends expViewElement implements Serializable {
        private final depthGuiElement self;
        transient private ExpViewFragment parent = null;
        transient private DepthPreview cv = null;
        transient ImageView collapseImage = null;
        transient ImageView expandImage = null;
        transient Spinner modeControl = null;
        transient Spinner cameraSelection = null;

        private double aspectRatio;

        private boolean isExclusive = false;
        private int margin, elMargin;
        final String warningText;

        //Quite usual constructor...
        depthGuiElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.self = this;

            margin = res.getDimensionPixelSize(R.dimen.graph_label_start_margin);
            elMargin = res.getDimensionPixelSize(R.dimen.expElementMargin);

            aspectRatio = 2.5;

            warningText = res.getString(R.string.remoteDepthGUIWarning).replace("'", "\\'");
        }

        //Interface to change the height of the graph
        protected void setAspectRatio(double aspectRatio) {
            this.aspectRatio = aspectRatio;
        }

        @Override
        protected String getUpdateMode() {
             return "none";
        }

        @Override
        //Create the actual view in Android
        protected void createView(LinearLayout ll, Context c, Resources res, final ExpViewFragment parent, PhyphoxExperiment experiment){
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                return;

            super.createView(ll, c, res, parent, experiment);
            this.parent = parent;

            Context ctx = c;
            Activity act = null;
            while (ctx instanceof ContextWrapper) {
                if (ctx instanceof Activity) {
                    act = (Activity) ctx;
                }
                ctx = ((ContextWrapper)ctx).getBaseContext();
            }

            //Create a row consisting of label and value
            LinearLayout layout = new LinearLayout(c);
            layout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);

            RelativeLayout titleLine = new RelativeLayout(c);
            titleLine.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            layout.addView(titleLine);

            expandImage = new ImageView(c);
            expandImage.setId(ViewCompat.generateViewId());
            expandImage.setImageResource(R.drawable.ic_expand_arrow);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(elMargin, elMargin, elMargin, elMargin);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            expandImage.setLayoutParams(lp);
            titleLine.addView(expandImage);

            collapseImage = new ImageView(c);
            collapseImage.setImageResource(R.drawable.ic_collapse_arrow);
            collapseImage.setLayoutParams(lp);
            collapseImage.setVisibility(INVISIBLE);
            titleLine.addView(collapseImage);

            //Create the label as textView
            TextView labelView = new TextView(c);
            lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(margin, 0, 0, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.RIGHT_OF, expandImage.getId());
            labelView.setLayoutParams(lp);
            labelView.setText(this.label);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            titleLine.addView(labelView);

            //Create the preview view
            cv = new DepthPreview(c);
            cv.attachDepthInput(experiment.depthInput);
            cv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            cv.setAspectRatio(aspectRatio);
            layout.addView(cv);

            //Mode Controls
            modeControl = new Spinner(c, Spinner.MODE_DIALOG);
            modeControl.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            modeControl.setVisibility(View.GONE);
            class ModeItem {
                public final DepthInput.DepthExtractionMode key;
                public final String value;
                ModeItem(DepthInput.DepthExtractionMode key, String value) {
                    this.key = key;
                    this.value = value;
                }
                @Override
                public String toString() {
                    return value;
                }
            }
            ArrayList<ModeItem> modeOptions = new ArrayList<>();
            int selection = 0;
            for (DepthInput.DepthExtractionMode mode : DepthInput.DepthExtractionMode.values()) {
                int id = parent.getResources().getIdentifier("depthAggregationMode" + mode.name().substring(0, 1).toUpperCase() + mode.name().substring(1), "string", act.getPackageName());
                modeOptions.add(new ModeItem(mode, res.getString(id)));
            }
            modeControl.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, modeOptions));
            for (int i = 0; i < modeOptions.size(); i++) {
                if (modeOptions.get(i).key == experiment.depthInput.getExtractionMode())
                    modeControl.setSelection(i);
            }
            modeControl.setPromptId(R.string.depthAggregationMode);
            layout.addView(modeControl);
            modeControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    cv.setExtractionMode(modeOptions.get(position).key);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            //Camera selection controls
            cameraSelection = new Spinner(c);
            cameraSelection.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            cameraSelection.setVisibility(View.GONE);
            class CameraItem {
                public final String key, value;
                CameraItem(String key, String value) {
                    this.key = key;
                    this.value = value;
                }
                @Override
                public String toString() {
                    return value;
                }
            }
            ArrayList<CameraItem> camOptions = new ArrayList<>();
            String backCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_BACK);
            if (backCam != null)
                camOptions.add(new CameraItem(backCam, res.getString(R.string.cameraBackFacing)));
            String frontCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_FRONT);
            if (frontCam != null)
                camOptions.add(new CameraItem(frontCam, res.getString(R.string.cameraFrontFacing)));
            String extCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_EXTERNAL);
            if (extCam != null)
                camOptions.add(new CameraItem(extCam, res.getString(R.string.cameraExternal)));
            cameraSelection.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, camOptions));
            for (int i = 0; i < camOptions.size(); i++) {
                if (camOptions.get(i).key.equals(experiment.depthInput.getCurrentCameraId()))
                    cameraSelection.setSelection(i);
            }
            layout.addView(cameraSelection);
            cameraSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    cv.setCamera(camOptions.get(position).key);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (self.parent != null) {
                        if (isExclusive) {
                            self.parent.leaveExclusive();
                        } else {
                            cv.requestFocus();
                            self.parent.requestExclusive(self);
                        }
                    }
                }
            });

            rootView = layout;
            rootView.setFocusableInTouchMode(false);
            ll.addView(rootView);

        }

        @Override
        public void onFragmentStop(PhyphoxExperiment experiment) {
            super.onFragmentStop(experiment);
            cv.stop();
            cv = null;
        }

        @Override
        //Create the HTML markup. We do not stream the video to the web interface, so this is just a placeholder and notification
        protected String createViewHTML(){
            return "<div style=\"font-size: 105%;\" class=\"graphElement\" id=\"" + htmlID + "\"><span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span><div class=\"warningIcon\" onclick=\"alert('"+ warningText + "')\"></div></div>";
        }

        @Override
        protected void restore() {
            super.restore();
            if (rootView != null && cv != null && parent != null) {
                isExclusive = false;

                if (expandImage != null && collapseImage != null) {
                    expandImage.setVisibility(VISIBLE);
                    collapseImage.setVisibility(INVISIBLE);
                }
                if (modeControl != null)
                    modeControl.setVisibility(View.GONE);
                if (cameraSelection != null)
                    cameraSelection.setVisibility(View.GONE);

                rootView.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
                rootView.requestLayout();

                cv.setInteractive(false);
            }
        }

        @Override
        protected void maximize() {
            super.maximize();
            if (rootView != null && cv != null && parent != null) {
                isExclusive = true;

                if (expandImage != null && collapseImage != null) {
                    expandImage.setVisibility(INVISIBLE);
                    collapseImage.setVisibility(VISIBLE);
                }
                if (modeControl != null)
                    modeControl.setVisibility(VISIBLE);
                if (cameraSelection != null)
                    cameraSelection.setVisibility(VISIBLE);

                rootView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
                rootView.requestLayout();

                cv.setInteractive(true);
            }
        }
    }

    enum ImageFilter {
        none, invert
    }

    //imageElement displays an image
    public class imageElement extends expViewElement implements Serializable {

        private String src;
        Drawable drawable;
        private float scale = 1.0f;
        private ImageFilter darkFilter = ImageFilter.none;
        private ImageFilter lightFilter = ImageFilter.none;

        //Label is not used
        imageElement(String valueOutput, Vector<String> inputs, Resources res, String src) {
            super("", valueOutput, inputs, res);
            this.src = src;
        }

        public void setScale(float scale) {
            this.scale = scale;
        }

        public void setFilters(ImageFilter darkFilter, ImageFilter lightFilter) {
            this.darkFilter = darkFilter;
            this.lightFilter = lightFilter;
        }

        @Override
        //This does not display anything. Do not update.
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        //Append the Android views we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            if (experiment.resourceFolder.startsWith("ASSET")) {
                String assetPath = "experiments/res/" + src;
                try {
                    InputStream is = res.getAssets().open(assetPath);
                    drawable = new BitmapDrawable(BitmapFactory.decodeStream(is));
                } catch (Exception e) {
                    Log.e("imageView", "Failed to open image from asset: " + assetPath);
                }
            } else {
                File srcFile = new File(experiment.resourceFolder, src);
                Bitmap bmp = BitmapFactory.decodeFile(srcFile.getAbsolutePath());
                if (bmp != null)
                    drawable = new BitmapDrawable(bmp);
            }
            if (drawable != null) {
                applyFilter(Helper.isDarkTheme(res) ? darkFilter : lightFilter);

                ImageView iv = new ImageView(c);
                LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                iv.setLayoutParams(lllp);

                iv.setImageDrawable(drawable);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setScaleX(scale);
                iv.setScaleY(scale);

                rootView = iv;
            } else {
                TextView tv = new TextView(c);
                LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lllp);

                tv.setText("Image not available");
                tv.setGravity(Gravity.CENTER);
                tv.setTypeface(null, Typeface.BOLD);
                rootView = tv;
            }

            //Add it to the linear layout
            ll.addView(rootView);
        }

        final ColorMatrixColorFilter colorFilterNone = new ColorMatrixColorFilter(
            new float[]{
                    1, 0, 0, 0, 0,
                    0, 1, 0, 0, 0,
                    0, 0, 1, 0, 0,
                    0, 0, 0, 1, 0
            }
        );

        final ColorMatrixColorFilter colorFilterInvert = new ColorMatrixColorFilter(
            new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
            }
        );

        protected void applyFilter(ImageFilter filter) {
            switch (filter) {
                case none:
                    drawable.setColorFilter(colorFilterNone);
                    break;
                case invert:
                    drawable.setColorFilter(colorFilterInvert);
                    break;
            }
        }

        @Override
        //Create the HTML version of this view:
        //<div>
        //  <img ... />
        //</div>
        protected String createViewHTML(){
            return "<div class=\"imageElement\" id=\"element" + htmlID + "\"><img style=\"width: " + (100.0*scale) + "% \" class=\"lightFilter_" + lightFilter.toString() + " darkFilter_" + darkFilter.toString() + "\" src=\"res?src=" + src + "\"></p></div>";
        }

    }

    public class cameraElement extends expViewElement implements  Serializable {

        private cameraElement self;
        private boolean isExclusive = false;
        transient private ExpViewFragment parent = null;
        transient private CameraPreviewFragment cameraPreviewFragment = null;
        float height = 300; //dp, might be settable in the future

        boolean grayscale;
        RGB markOverexposure;
        RGB markUnderexposure;
        ShowCameraControls showCameraControls = ShowCameraControls.FullViewOnly;
        CameraSettingLevel cameraSettingLevel = CameraSettingLevel.ADVANCED;
        String lockedSettings;

        final String warningText;

        Scrollable scrollable = new Scrollable() {
            @Override
            public void enableScrollable() {
                parent.enableScrolling();
                rootView.getParent().requestDisallowInterceptTouchEvent(false);
            }

            @Override
            public void disableScrollable() {
                parent.disableScrolling();
                rootView.getParent().requestDisallowInterceptTouchEvent(true);
            }
        };


        protected cameraElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            warningText = res.getString(R.string.remoteCameraPreviewWarning).replace("'", "\\'");
        }

        public void applyControlSettings(ShowCameraControls showCameraControls, int exposureAdjustmentLevel) {
            this.showCameraControls = showCameraControls;
            this.lockedSettings = lockedSettings;
            switch (exposureAdjustmentLevel) {
                case 1: cameraSettingLevel = CameraSettingLevel.BASIC;
                        break;
                case 2: cameraSettingLevel = CameraSettingLevel.INTERMEDIATE;
                        break;
                default: cameraSettingLevel = CameraSettingLevel.ADVANCED;
                        break;
            }
        }

        public void setPreviewParameters(boolean grayscale, RGB markOverexposure, RGB markUnderexposure) {
            this.grayscale = grayscale;
            this.markOverexposure = markOverexposure;
            this.markUnderexposure = markUnderexposure;
        }

        protected boolean toggleExclusive() {
            if (self.parent != null) {
                if (isExclusive) {
                    self.parent.leaveExclusive();
                } else {
                    self.parent.requestExclusive(self);
                }
                return true;
            }
            return false;
        }

        @Override
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
               return;

            super.createView(ll, c, res, parent, experiment);
            this.parent = parent;
            this.self = this;

            LayoutInflater inflater = LayoutInflater.from(c);
            rootView = inflater.inflate(R.layout.camera_layout, ll, false);
            rootView.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, parent.getResources().getDisplayMetrics());
            ll.addView(rootView);

            rootView.setOnClickListener(view -> toggleExclusive());
            rootView.setFocusableInTouchMode(false);

            FragmentContainerView containerView = rootView.findViewById(R.id.fragmentContainerView);
            if (cameraPreviewFragment == null)
                cameraPreviewFragment = new CameraPreviewFragment(experiment, scrollable, this::toggleExclusive, showCameraControls, cameraSettingLevel, grayscale, markOverexposure, markUnderexposure);

            parent.getChildFragmentManager().beginTransaction().add(containerView.getId(), cameraPreviewFragment).commit();
        }

        @Override
        protected void destroyView() {
            if (parent != null && cameraPreviewFragment != null)
                parent.getChildFragmentManager().beginTransaction().remove(cameraPreviewFragment).commit();
            cameraPreviewFragment = null;

        }

        @Override
        //Create the HTML markup. We do not stream the video to the web interface, so this is just a placeholder and notification
        protected String createViewHTML(){
            return "<div style=\"font-size: 105%;\" class=\"graphElement\" id=\"" + htmlID + "\"><span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span><div class=\"warningIcon\" onclick=\"alert('"+ warningText + "')\"></div></div>";
        }

        @Override
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        protected void onFragmentStop(PhyphoxExperiment experiment) {
            super.onFragmentStop(experiment);

        }

        @Override
        protected void restore() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                return;

            super.restore();
            if (rootView != null && cameraPreviewFragment != null && parent != null) {
                isExclusive = false;

                rootView.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, parent.getResources().getDisplayMetrics());
                rootView.requestLayout();

                cameraPreviewFragment.setInteractive(false);
            }
        }

        @Override
        protected void maximize() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                return;

            super.maximize();
            if (rootView != null && cameraPreviewFragment != null && parent != null) {
                isExclusive = true;

                rootView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
                rootView.requestLayout();

                cameraPreviewFragment.setInteractive(true);
            }
        }

        @Override
        protected void onViewSelected(boolean parentViewIsVisible) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                return;
            if (cameraPreviewFragment != null)
                cameraPreviewFragment.onPageVisibleToUser(parentViewIsVisible);
        }
    }

    public class toggleElement extends  expViewElement implements  Serializable {

        String defaultValue;
        protected toggleElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
        }

        @Override
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
            super.createView(ll, c, res, parent, experiment);

            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setVerticalGravity(Gravity.CENTER_VERTICAL);

            //Create the label in the left half of the row
            TextView labelView = new TextView(c);
            labelView.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //Left half of the whole row
            labelView.setText(this.label);
            labelView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setPadding(0, 0, (int) labelSize / 2, 0);

            //Create a horizontal linear layout, which seperates the right half into the edit field
            //and a textView to show the unit next to the user input
            SwitchMaterial switchView = new SwitchMaterial(c);
            LinearLayout.LayoutParams tableRow = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f);
            switchView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            switchView.setPadding((int) labelSize / 2, 0, 0, 0);

            //wrap switchMaterial into linearlayout as the gravity of the switch is not working as expected.
            LinearLayout switchViewRow = new LinearLayout(c);
            switchViewRow.setLayoutParams(tableRow);
            switchViewRow.addView(switchView);

            row.addView(labelView);
            row.addView(switchViewRow);

            boolean isSwitchedOn = !Objects.equals(this.defaultValue, "0");
            switchView.setChecked(isSwitchedOn);

            rootView = row;
            rootView.setFocusableInTouchMode(true);

            //Add it to the linear layout
            ll.addView(rootView);

        }

        public void setDefaultValue(String defaultValue){
            this.defaultValue = defaultValue;

        }

        @Override
        protected String createViewHTML() {
            return null;
        }

        @Override
        protected String getUpdateMode() {
            return null;
        }
    }
}
