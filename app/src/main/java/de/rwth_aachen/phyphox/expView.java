package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Vector;

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


public class expView implements Serializable{

    //Abstract expViewElement class defining the interface for any element of an experiment view
    public abstract class expViewElement implements Serializable {
        protected String label; //Each element has a label. Usually naming the data shown
        protected float labelSize; //Size of the label
        protected String valueOutput; //User input will be directed to this output, so the experiment can write it to a dataBuffer
        protected String valueInput; //Single valued data from this input can be displayed to the user
        protected String dataXInput; //Array data from this input can be displayed to the user, usually used for x-axis
        protected String dataYInput; //Array data from this input can be displayed to the user, usually used for y-axis

        protected int htmlID; //This holds a unique id, so the element can be referenced in the webinterface via an HTML ID

        //Constructor takes the label, any buffer name that should be used an a reference to the resources
        protected expViewElement(String label, String valueOutput, String valueInput, String dataXInput, String dataYInput, Resources res) {
            this.label = label;
            this.labelSize = res.getDimension(R.dimen.label_font);
            this.valueOutput = valueOutput;
            this.valueInput = valueInput;
            this.dataXInput = dataXInput;
            this.dataYInput = dataYInput;

            //If not set otherwise, set the input buffer to be identical to the output buffer
            //This allows to receive the old user-set value after the view has changed
            if (this.valueInput == null && this.valueOutput != null)
                this.valueInput = this.getValueOutput();
        }

        //Interface to change the label size
        protected void setLabelSize(float size) {
            this.labelSize = size;
        }

        //Abstract function to force child classes to implement createView
        //This will take a linear layout, which should be filled by this function
        protected abstract void createView(LinearLayout ll, Context c, Resources res);

        protected void cleanView() {

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

        //setValue is the function through which the main process can write a single value that
        //should be shown to the user
        protected void setValue(double x) {

        }

        //This function returns a JavaScript function. The argument of this function will receive
        //a single value that should be shown to the user
        protected String setValueHTML() {
            return "function(x) {}";
        }

        //setDataX is the function through which the main process can write a full dataBuffer
        // (array) that should be shown to the user. Usually used for x-data
        protected void setDataX(dataBuffer x) {

        }

        //This function returns a JavaScript function. The argument of this function will receive
        //an array that should be shown to the user. Usually used for x-data
        protected String setDataXHTML() {
            return "function(x) {}";
        }

        //setDataY is the function through which the main process can write a full dataBuffer
        // (array) that should be shown to the user. Usually used for y-data
        protected void setDataY(dataBuffer y) {

        }

        //This function returns a JavaScript function. The argument of this function will receive
        //an array that should be shown to the user. Usually used for y-data
        protected String setDataYHTML() {
            return "function(y) {}";
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

        //This returns the key name of the valueInput dataBuffer. Called by the main loop to figure
        // out which values to write to this element
        protected String getValueInput() {
            return this.valueInput;
        }

        //This returns the key name of the dataXInput dataBuffer. Called by the main loop to figure
        // out which buffer to write to this element as x
        protected String getDataXInput() {
            return this.dataXInput;
        }

        //This returns the key name of the dataYInput dataBuffer. Called by the main loop to figure
        // out which buffer to write to this element as y
        protected String getDataYInput() {
            return this.dataYInput;
        }

        //This is called, when the data for the view has been reset
        protected void clear() {

        }
    }

    //valueElement implements a simple text display for a single value with an unit and a given
    //format.
    public class valueElement extends expViewElement implements Serializable {
        transient private TextView tv; //Holds the Android textView
        private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
        private boolean scientificNotation; //Show scientific notation instead of fixed point (1e-3 instead of 0.001)
        private int precision; //The number of significant digits
        private String formatter; //This formatter is created when scientificNotation and precision are set
        private String unit; //A string to display as unit

        //Constructor takes the same arguments as the expViewElement constructor
        //It sets a precision of 2 with fixed point notation as default and creates the formatter
        valueElement(String label, String valueOutput, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueOutput, valueInput, dataXInput, dataYInput, res);
            this.scientificNotation = false;
            this.precision = 2;
            updateFormatter();
            this.unit = "";
            this.factor = 1.;
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
        protected void createView(LinearLayout ll, Context c, Resources res){
            //Create a row consisting of label and value
            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);

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
            labelView.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

            //Create the value (and unit) as textView
            tv = new TextView(c);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f)); //right half should be value+unit
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize); //Align left to the center of the row
            tv.setPadding((int) labelSize / 2, 0, 0, 0);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

            //Add label and value to the row
            row.addView(labelView);
            row.addView(tv);

            //Add the row to the linear layout
            ll.addView(row);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <span>Label</span><span>Value</span>
        //</div>
        protected String createViewHTML(){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"valueElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\">"+this.label+"</span>" +
                    "<span class=\"value\"></span>" +
                    "</div>";
        }

        @Override
        //We just have to send calculated value and the unit to the textView
        protected void setValue(double x) {
            if (tv != null)
                tv.setText(String.format(this.formatter, x*this.factor)+this.unit);
        }

        @Override
        //In Javascript we just have to set the content of the value <span> to the value using jquery
        protected String setValueHTML() {
            //In JavaScript we have to use "toExponential" or "toFixed" isntead of a formatter
            if (scientificNotation)
                return "function (x) {" +
                        "$(\"#element"+htmlID+" .value\").text((x*"+factor+").toExponential("+precision+")+\" "+unit+"\")" +
                        "}";
            else
                return "function (x) {" +
                        "$(\"#element"+htmlID+" .value\").text((x*"+factor+").toFixed("+precision+")+\" "+unit+"\")" +
                        "}";
        }
    }

    //infoElement implements a simple static text display, which gives additional info to the user
    public class infoElement extends expViewElement implements Serializable {
        transient private TextView tv; //Holds the Android textView

        //Constructor takes the same arguments as the expViewElement constructor
        infoElement(String label, String valueOutput, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueOutput, valueInput, dataXInput, dataYInput, res);
        }

        @Override
        //This does not display anything. Do not update.
        protected String getUpdateMode() {
            return "none";
        }

        @Override
        //Append the Android views we need to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res){

            //Create the text as textView
            TextView textView = new TextView(c);
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.info_element_margin), res.getDisplayMetrics());
            lllp.setMargins(0, margin, 0, margin);
            textView.setLayoutParams(lllp);
            textView.setText(this.label);
            textView.setGravity(Gravity.LEFT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.info_element_font));
            textView.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

            //Add it to the linear layout
            ll.addView(textView);
        }

        @Override
        //Creat the HTML version of this view:
        //<div>
        //  <p>text</p>
        //</div>
        protected String createViewHTML(){
            return "<div style=\"font-size:\"+this.labelSize/.4+\"%;\" class=\"infoElement\" id=\"element"+htmlID+"\">" +
                    "<p>"+this.label+"</p>" +
                    "</div>";
        }

        @Override
        //In Javascript we don't have to do anything
        protected String setValueHTML() {
            return "function (x) {}";
        }
    }

    //editElement implements a simple edit box which takes a single value from the user
    public class editElement extends expViewElement implements Serializable {
        transient private EditText et; //Holds the Android EditText
        private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
        private String unit; //A string to display as unit
        private double defaultValue; //This value is filled into the dataBuffer before the user enters a custom value
        private double currentValue = Double.NaN; //This value is filled into the dataBuffer before the user enters a custom value
        private boolean signed = true; //Is the user allowed to give negative values?
        private boolean decimal = true; //Is the user allowed to give non-integer values?
        private boolean focused = false; //Is the element currently focused? (Updates should be blocked while the element has focus and the user is working on its content)

        //No special constructor. Just some defaults.
        editElement(String label, String valueOutput, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueOutput, valueInput, dataXInput, dataYInput, res);
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

        @Override
        //This is an input, so the updateMode should be "input"
        protected String getUpdateMode() {
            return "input";
        }

        @Override
        //Create the view in Android and append it to the linear layout
        protected void createView(LinearLayout ll, Context c, Resources res){
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
            et = new EditText(c);
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

            //Add the row to the main linear layout passed to this function
            ll.addView(row);

            //Add a listener to the edit box to keep track of the focus
            et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean hasFocus) {
                    focused = hasFocus;
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
            if (!signed)
                restrictions += "min=\"0\" ";
            if (!decimal)
                restrictions += "step=\"1\" ";

            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"editElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\">"+this.label+"</span>" +
                    "<input onchange=\"$.getJSON('control?cmd=set&buffer="+valueOutput+"&value='+$(this).val()/"+ factor + ")\" type=\"number\" class=\"value\" " + restrictions + " />" +
                    "<span class=\"unit\">"+this.unit+"</span>" +
                    "</div>";
        }

        @Override
        //Get the value from the edit box (Note, that we have to divide by the factor to achieve a
        //use that is consistent with that of the valueElement
        protected double getValue() {
            if (et == null)
                return currentValue;
            try {
                currentValue = Double.valueOf(et.getText().toString())/factor;
            } catch (Exception e) {
                return currentValue;
            }
            return currentValue;
        }

        @Override
        //Set the value if the element is not focused
        protected void setValue(double v) {
            //Enter value from buffer if it has not been changed by the user
            //This ensures, that the old value is restored if the view has to be created after the views have been switched.
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
        //The javascript function which updates the content of the input as it is updated on the phone
        protected String setValueHTML() {
            return "function (x) {" +
                    "if (!$(\"#element"+htmlID+" .value\").is(':focus'))" +
                        "$(\"#element"+htmlID+" .value\").val((x*"+factor+"))" +
                    "}";
        }
    }

    //graphElement implements a graph that displays y vs. x arrays from the dataBuffer
    //This class mostly wraps the graphView, which (being rather complex) is implemented in its own
    //class. See graphView.java...
    public class graphElement extends expViewElement implements Serializable {
        transient private graphView gv = null; //Holds a GraphView instance
        transient private PlotRenderer plotRenderer = null;
        private double aspectRatio; //The aspect ratio defines the height of the graph view based on its width (aspectRatio=width/height)
        transient private floatBufferRepresentation dataX; //The x data to be displayed
        transient private floatBufferRepresentation dataY; //The y data to be displayed
        private double dataMinX, dataMaxX, dataMinY, dataMaxY;

        private boolean line = false; //Show lines instead of points?
        private int historyLength = 1; //If set to n > 1 the graph will also show the last n sets in a different color
        private String labelX = null; //Label for the x-axis
        private String labelY = null; //Label for the y-axis
        private boolean partialUpdate = false; //Allow partialUpdate of newly added data points instead of transfering the whole dataset each time (web-interface)
        private boolean logX = false; //logarithmic scale for the x-axis?
        private boolean logY = false; //logarithmic scale for the y-axis?
        private double lineWidth = 1.0;
        private int color;

        private String highlightColor;
        private String backgroundGridRemoteColor;
        private String gridColor;
        private String mainRemoteColor;
        private String lineColor;

        graphView.scaleMode scaleMinX = graphView.scaleMode.auto;
        graphView.scaleMode scaleMaxX = graphView.scaleMode.auto;
        graphView.scaleMode scaleMinY = graphView.scaleMode.auto;
        graphView.scaleMode scaleMaxY = graphView.scaleMode.auto;

        double minX = 0.;
        double maxX = 0.;
        double minY = 0.;
        double maxY = 0.;

        //Quite usual constructor...
        graphElement(String label, String valueOutput, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueOutput, valueInput, dataXInput, dataYInput, res);
            aspectRatio = 2.5;
            color = res.getColor(R.color.highlight);
        }

        //Interface to change the height of the graph
        protected void setAspectRatio(double aspectRatio) {
            this.aspectRatio = aspectRatio;
        }

        protected void setLineWidth(double lineWidth) {
            this.lineWidth = lineWidth;
            if (gv != null)
                gv.setLineWidth(lineWidth);
        }

        protected void setColor(int color) {
            this.color = color;
            if (gv != null)
                gv.setColor(color);
        }

        //Interface to switch between points and lines
        protected void setLine(boolean line) {
            this.line = line;
            if (gv != null)
                gv.setLine(line);
        }

        public void setScaleModeX(graphView.scaleMode minMode, double minV, graphView.scaleMode maxMode, double maxV) {
            this.scaleMinX = minMode;
            this.scaleMaxX = maxMode;
            this.minX = minV;
            this.maxX = maxV;
            if (gv != null)
                gv.setScaleModeX(minMode, minV, maxMode, maxV);
        }

        public void setScaleModeY(graphView.scaleMode minMode, double minV, graphView.scaleMode maxMode, double maxV) {
            this.scaleMinY = minMode;
            this.scaleMaxY = maxMode;
            this.minY = minV;
            this.maxY = maxV;
            if (gv != null)
                gv.setScaleModeY(minMode, minV, maxMode, maxV);
        }

        //Interface to set a history length
        protected void setHistoryLength(int hl) {
            this.historyLength = hl;
            if (gv != null)
                gv.setHistoryLength(hl);
        }

        //Interface to set the axis labels.
        protected void setLabel(String labelX, String labelY) {
            this.labelX = labelX;
            this.labelY = labelY;
            if (gv != null)
                gv.setLabel(labelX, labelY);
        }

        //Interface to set log scales
        protected void setLogScale(boolean logX, boolean logY) {
            this.logX = logX;
            this.logY = logY;
        }

        //Interface to set partial updates vs. full updates of the data sets
        protected void setPartialUpdate(boolean pu) {
            this.partialUpdate = pu;
        }

        @Override
        //The update mode is "partial" or "full" as this element uses arrays. The experiment may
        //decide if partial updates are sufficient
        protected String getUpdateMode() {
            if (partialUpdate)
                return "partial";
            else
                return "full";
        }

        @Override
        //Create the actual view in Android
        protected void createView(LinearLayout ll, Context c, Resources res){

            highlightColor = String.format("%08x", res.getColor(R.color.highlight)).substring(2);
            backgroundGridRemoteColor = String.format("%08x", res.getColor(R.color.backgroundGridRemote)).substring(2);
            mainRemoteColor = String.format("%08x", res.getColor(R.color.mainRemote)).substring(2);
            gridColor = String.format("%08x", res.getColor(R.color.grid)).substring(2);
            lineColor = String.format("%08x", color).substring(2);

            //We need a label and want to put the graph below. So we wrap everything into a vertical
            //linear layout (Axis labels are handled by the graphView)
            LinearLayout gvll = new LinearLayout(c);
            gvll.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            gvll.setOrientation(LinearLayout.VERTICAL);

            //Create the label
            TextView labelView = new TextView(c);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, res.getDimension(R.dimen.graph_label_start_margin), res.getDisplayMetrics()), 0, 0, 0);
            labelView.setLayoutParams(lp);
            labelView.setText(this.label);
            labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setTextColor(ContextCompat.getColor(c, R.color.mainExp));

            FrameLayout fl = new FrameLayout(c);
            fl.setLayoutParams(new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            PlotAreaView plotAreaView = new PlotAreaView(c);
            plotAreaView.setLayoutParams(new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            plotRenderer = new PlotRenderer(res);
            plotRenderer.start();
            plotAreaView.setSurfaceTextureListener(plotRenderer);


            //Create the graphView
            gv = new graphView(c, aspectRatio, plotAreaView, plotRenderer);
            gv.setLayoutParams(new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            //Send our parameters to the graphView isntance
            gv.setLine(line);
            gv.setLineWidth(lineWidth);
            gv.setColor(color);
            gv.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
            gv.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
            gv.setHistoryLength(historyLength);
            gv.setLabel(labelX, labelY);
            gv.setLogScale(logX, logY);

            fl.addView(plotAreaView);
            fl.addView(gv);

            //Add label and graphView to our own wrapper linear layout
            gvll.addView(labelView);
            gvll.addView(fl);

            //Add the wrapper layout to the linear layout given to this function
            ll.addView(gvll);

        }

        @Override
        public void cleanView() {
            plotRenderer.halt();
            try {
                plotRenderer.join();
            } catch (InterruptedException e) {
                Log.e("cleanView", "Renderer: Interrupted execution.");
            }
            plotRenderer = null;
            gv = null;
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
                    "<span class=\"label\">"+this.label+"</span>" +
                    "<div class=\"graphBox\"><div class=\"graphRatio\" style=\"padding-top: "+100.0/this.aspectRatio+"%\"></div><div class=\"graph\"></div></div>" +
                    "</div>";
        }

        @Override
        //Store the x data array until we update
        protected void setDataX(dataBuffer x) {
            dataX = x.getFloatBuffer();
            dataMinX = x.getMin();
            dataMaxX = x.getMax();
        }

        @Override
        //Return a javascript function which stores the x data array for later use
        protected String setDataXHTML() {
            return "function (x) {"+
                        "elementData["+htmlID+"][\"x\"] = x" +
                    "}";
        }

        @Override
        //Store the y data array until we update
        protected void setDataY(dataBuffer y) {
            dataY = y.getFloatBuffer();
            dataMinY = y.getMin();
            dataMaxY = y.getMax();
        }

        @Override
        //Return a javascript function which stores the y data array for later use
        protected String setDataYHTML() {
            return "function (y) {"+
                        "elementData["+htmlID+"][\"y\"] = y" +
                    "}";
        }

        @Override
        //Data complete, let's send it to the graphView
        //Also clear the data afterwards to avoid sending it multiple times if it is not updated for
        //some reason
        protected void dataComplete() {
            if (gv == null)
                return;
            if (dataY != null) {
                if (dataX != null) {
                    gv.addGraphData(dataY, dataMinY, dataMaxY, dataX, dataMinX, dataMaxX);
                    dataX = null;
                } else
                    gv.addGraphData(dataY, dataMinY, dataMaxY);
                dataY = null;
            }
        }

        @Override
        //This looks pretty ugly and indeed needs a clean-up...
        //This function returns a javascript function which updates the flot chart.
        //So we have to set-up some JSON objects to define the graph, put it into the JavaScript
        //function (which has to setup some JSON itself) and return the whole nightmare. There
        //certainly is a way to beautify this, but it's not too obvious...
        protected String dataCompleteHTML() {//TODO: Create intelligent function to setup ticks on log scales
            String transformX, transformY;
            if (logX)
                transformX = "ticks: [0.1,1,10,100,1000,10000], transform: function (v) { if (v >= 0.001) return Math.log(v); else return Math.log(0.001) }, inverseTransform: function (v) { return Math.exp(v); }, ";
            else
                transformX = "\"ticks\": 3, ";
            if (logY)
                transformY = "ticks: [0.01,0.1,1,10], transform: function (v) { if (v >= 0.001) return Math.log(v); else return Math.log(0.001) }, inverseTransform: function (v) { return Math.exp(v); }, ";
            else
                transformY = "\"ticks\": 3, ";
            return "function () {" +
                        "var d = [];" +
                        "if (!elementData["+htmlID+"].hasOwnProperty(\"y\"))" +
                            "return;" +
                        "if (!elementData["+htmlID+"].hasOwnProperty(\"x\") || elementData["+htmlID+"][\"x\"].length < elementData["+htmlID+"][\"y\"].length) {" +
                            "elementData["+htmlID+"][\"x\"] = [];" +
                            "for (i = 0; i < elementData["+htmlID+"][\"y\"].length; i++)" +
                                "elementData["+htmlID+"][\"x\"][i] = i" +
                        "}" +
                        "for (i = 0; i < elementData["+htmlID+"][\"y\"].length; i++)" +
                            "d[i] = [elementData["+htmlID+"][\"x\"][i], elementData["+htmlID+"][\"y\"][i]];" +
                        "$.plot(\"#element"+htmlID+" .graph\", [{ \"color\": \"" + "#"+ lineColor + "\" , \"data\": d }], {\"lines\": {show:"+(line ? "true" : "false")+", \"lineWidth\": "+(2.0*lineWidth)+"}, \"points\": {show:"+(!line ? "true" : "false")+"}, \"xaxis\": {" + transformX + "\"axisLabel\": \""+this.labelX+"\", \"tickColor\": \""+ "#"+gridColor +"\"}, \"yaxis\": {" + transformY + "\"axisLabel\": \""+this.labelY+"\", \"tickColor\": \""+ "#"+ gridColor +"\"}, \"grid\": {\"borderColor\": \""+ "#"+ mainRemoteColor +"\", \"backgroundColor\": \""+ "#"+backgroundGridRemoteColor +"\"}});" +
                    "}";
        }

        @Override
        protected void clear() {
            //Set our scale mode again to reset scaling from old data
            if (gv != null) {
                gv.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
                gv.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
            }
        }
    }

    //Remember? We are in the expView class.
    //An experiment view has a name and holds a bunch of expViewElement instances
    public String name;
    public Vector<expViewElement> elements = new Vector<>();
}