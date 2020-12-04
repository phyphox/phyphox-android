package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;

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

        protected void cleanView(PhyphoxExperiment experiment) {
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
                rootView.setVisibility(View.VISIBLE);
            }
        }

        protected void maximize() {
            state = State.maximized;
            if (rootView != null) {
                rootView.setVisibility(View.VISIBLE);
            }
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
        private int color;

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
            this.color = res.getColor(R.color.mainExp);
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

        protected void setColor(int c) {
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
            labelView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); //Align right to the center of the row
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setPadding(0, 0, (int) labelSize / 2, 0);
            labelView.setTextColor(color);

            //Create the value (and unit) as textView
            tv = new TextView(c);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //right half should be value+unit
            tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize*(float)size); //Align left to the center of the row
            tv.setPadding((int) labelSize / 2, 0, 0, 0);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(color);


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
            String c = String.format("%08x", color).substring(2);
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

        private int color;
        private int gravity = Gravity.START;
        private int typeface = Typeface.NORMAL;
        private float size = 1.0f;

        //Constructor takes the same arguments as the expViewElement constructor
        infoElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.color = res.getColor(R.color.mainExp);
        }

        protected void setColor(int c) {
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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
//            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, context.getDimension(R.dimen.info_element_margin), context.getDisplayMetrics());
//            lllp.setMargins(0, margin, 0, margin);
            textView.setLayoutParams(lllp);
            textView.setText(this.label);
            textView.setGravity(gravity);
            textView.setTypeface(null, typeface);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.info_element_font) * size);

            textView.setTextColor(color);

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
            String c = String.format("%08x", color).substring(2);
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
        private int color = 0;

        private float height = 0.1f;

        //Label is not used
        separatorElement(String valueOutput, Vector<String> inputs, Resources res) {
            super("", valueOutput, inputs, res);
        }

        public void setColor(int c) {
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
            rootView.setBackgroundColor(color);

            //Add it to the linear layout
            ll.addView(rootView);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <p>text</p>
        //</div>
        protected String createViewHTML(){
            String c = String.format("%08x", color).substring(2);
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

        //No special constructor. Just some defaults.
        editElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
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

        @Override
        //This is an input, so the updateMode should be "input"
        protected String getUpdateMode() {
            return "input";
        }

        @Override
        //Create the view in Android and append it to the linear layout
        protected void createView(LinearLayout ll, final Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);
            //Create a row holding the label and the textEdit
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
            labelView.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

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
         //   et.setPadding((int) labelSize / 2, 0, 0, 0);
            et.setTypeface(null, Typeface.BOLD);
            et.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

            //Construct the inputType flags from our own state
            int inputType = InputType.TYPE_CLASS_NUMBER;
            if (signed)
                inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
            if (decimal)
                inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
            et.setInputType(inputType);

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
            unitView.setTextColor(ContextCompat.getColor(c, R.color.mainExp));
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
            ll.addView(rootView);

            //Add a listener to the edit box to keep track of the focus
            et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean hasFocus) {
                    focused = hasFocus;
                    if (!hasFocus) {
                        setValue(getValue()); //Write back the value actually used...
                        triggered = true;
                    }
                }
            });

            et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    et.clearFocus();
                    return true;
                }
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
                currentValue = Double.valueOf(et.getText().toString())/factor;
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
                if (et != null)
                    et.setText(String.valueOf(currentValue*factor));
            }
        }

        @Override
        //If triggered, write the data to the output buffers
        //Always return zero as the analysis process does not receive the values directly
        protected boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
            if (!triggered)
                return false;
            triggered = false;
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
            b.setTextColor(ContextCompat.getColor(c, R.color.mainExp));
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
        private boolean logX = false; //logarithmic scale for the x-axis?
        private boolean logY = false; //logarithmic scale for the y-axis?
        private boolean logZ = false; //logarithmic scale for the z-axis?
        private int xPrecision = 3;
        private int yPrecision = 3;
        private int zPrecision = 3;
        private Vector<Double> lineWidth = new Vector<>();
        private Vector<Integer> color = new Vector<>();

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

        GraphView.ZoomState zoomState = null;

        final String warningText;

        //Quite usual constructor...
        graphElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
            this.self = this;

            margin = res.getDimensionPixelSize(R.dimen.activity_vertical_margin);

            aspectRatio = 2.5;
            gridColor = String.format("%08x", res.getColor(R.color.grid)).substring(2);
            nCurves = (inputs.size()+1)/2;

            for (int i = 0; i < nCurves; i++) {
                color.add(res.getColor(R.color.highlight));
                lineWidth.add(1.0);
                style.add(GraphView.Style.lines);
                mapWidth.add(0);
                dataX = new FloatBufferRepresentation[nCurves];
                dataY = new FloatBufferRepresentation[nCurves];
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

        protected void setColor(int color, int i) {
            this.color.set(i, color);
            if (gv != null)
                gv.setColor(color, i);
        }

        protected void setColor(int color) {
            for (int i = 0; i < nCurves || i < historyLength; i++)
                setColor(color, i);
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

            if (act != null && act instanceof Experiment) {
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

            //Send our parameters to the graphView isntance
            if (historyLength > 1)
                gv.setHistoryLength(historyLength);
            else
                gv.setCurves(nCurves);

            for (int i = 0; i < nCurves; i++) {
                gv.setStyle(style.get(i), i);
                gv.setMapWidth(mapWidth.get(i), i);
                gv.setLineWidth(lineWidth.get(i), i);
                gv.setColor(color.get(i), i);
            }
            gv.graphSetup.incrementalX = partialUpdate;
            gv.setAspectRatio(aspectRatio);
            gv.setColorScale(colorScale);
            gv.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
            gv.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
            gv.setScaleModeZ(scaleMinZ, minZ, scaleMaxZ, maxZ);
            gv.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);
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
        public void cleanView(PhyphoxExperiment experiment) {
            super.cleanView(experiment);

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
                    gv.addGraphData(dataY, dataMinY, dataMaxY, dataX, dataMinX, dataMaxX, dataMinZ, dataMaxZ);
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
                                    "borderColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2)).substring(2) + "\")," +
                                    "backgroundColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2)).substring(2) + "\")," +
                                    "borderWidth: " + (style.get(i/2) == GraphView.Style.vbars || style.get(i/2) == GraphView.Style.hbars ? 0.0 : lineWidth.get(i/2)) + "*scaleFactor," +
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
        public void applyZoom(double min, double max, boolean follow, String unit, String buffer, boolean yAxis) {
            if (unit != null) {
                if (unitX.equals(unit)) {
                    zoomState.minX = min;
                    zoomState.maxX = max;
                    zoomState.follows = follow;
                }
                if (unitY.equals(unit)) {
                    zoomState.minY = min;
                    zoomState.maxY = max;
                }
            } else if (buffer != null) {
                for (int i = 0; i < inputs.size(); i++) {
                    if (inputs.get(i).equals(buffer)) {
                        if (i % 2 == 1) {
                            zoomState.minX = min;
                            zoomState.maxX = max;
                            zoomState.follows = follow;
                        } else {
                            zoomState.minY = min;
                            zoomState.maxY = max;
                        }
                    }
                }
            } else {
                if (!yAxis) {
                    zoomState.minX = min;
                    zoomState.maxX = max;
                    zoomState.follows = follow;
                } else {
                    zoomState.minY = min;
                    zoomState.maxY = max;
                }
            }
            gv.zoomState = zoomState;
            gv.invalidate();
        }
    }

    //svgElement shows an svg image, optionally, parts of its source code can be replaced by measured values.
    public class svgElement extends expViewElement implements Serializable {
        transient ParametricSVGView svgView = null;
        int backgroundColor = 0xffffff;
        String source = null;

        //Constructor takes the same arguments as the expViewElement constructor
        svgElement(String label, String valueOutput, Vector<String> inputs, Resources res) {
            super(label, valueOutput, inputs, res);
        }

        public void setBackgroundColor(int c) {
            this.backgroundColor = c;
            if (svgView != null)
                svgView.setBackgroundColor(c);
        }

        public void setSvgParts(String source) {
            this.source = source;
            if (svgView != null)
                svgView.setSvgParts(source);
        }

        @Override
        //This is a single value. So the updateMode is "single"
        protected String getUpdateMode() {
            return "single";
        }

        @Override
        //Append the Android views we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
            super.createView(ll, c, res, parent, experiment);

            svgView = new ParametricSVGView(c);
            svgView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            svgView.setBackgroundColor(backgroundColor);
            if (source != null)
                svgView.setSvgParts(source);

            //Set the ParametricSVGView as root
            rootView = svgView;
            rootView.setFocusableInTouchMode(false);
            ll.addView(rootView);
        }

        @Override
        //Create the HTML version of this view:
        protected String createViewHTML(){
            return "";
        }

        @Override
        //We just have to send calculated value and the unit to the textView
        protected void onMayReadFromBuffers(PhyphoxExperiment experiment) {
            if (!needsUpdate)
                return;
            needsUpdate = false;
            double[] data = new double[inputs.size()];
            for (int i = 0; i < inputs.size(); i++)
                data[i] = experiment.getBuffer(inputs.get(i)).value;
            svgView.update(data);
        }

        @Override
        //In Javascript we just have to set the content of the value <span> to the value using jquery
        protected String setDataHTML() {
            return "";
        }
    }

    //Remember? We are in the expView class.
    //An experiment view has a name and holds a bunch of expViewElement instances
    public String name;
    public Vector<expViewElement> elements = new Vector<>();
}
