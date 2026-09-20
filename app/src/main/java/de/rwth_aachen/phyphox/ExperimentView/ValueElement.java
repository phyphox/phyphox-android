package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.GpsInput;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.RGB;

//ValueElement implements a simple text display for a single value with an unit and a given
//format.
public class ValueElement extends ExpViewElement implements Serializable {
    transient private TextView tv = null;
    private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
    private double size;
    private boolean scientificNotation; //Show scientific notation instead of fixed point (1e-3 instead of 0.001)
    private int precision; //The number of significant digits
    private String formatter; //This formatter is created when scientificNotation and precision are set
    private String unit; //A string to display as unit
    private RGB color;
    private String positiveUnit, negativeUnit;
    private GpsInput.ValueFormat valueFormat;

    public class Mapping {
        public Double min = Double.NEGATIVE_INFINITY;
        public Double max = Double.POSITIVE_INFINITY;
        public String str;

        public Mapping(String str) {
            this.str = str;
        }
    }

    protected Vector<Mapping> mappings = new Vector<>();

    public void addMapping(Mapping mapping) {
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

    //Constructor takes the same arguments as the ExpViewElement constructor
    //It sets a precision of 2 with fixed point notation as default and creates the formatter
    public ValueElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
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
    public void setScientificNotation(boolean sn) {
        this.scientificNotation = sn;
        updateFormatter();
    }

    //Interface to set precision
    public void setPrecision(int p) {
        this.precision = p;
        updateFormatter();
    }

    public void setSize(double size) {
        this.size = size;
    }

    public void setColor(RGB c) {
        this.color = c;
    }

    public void setGpsFormat(String gpsFormat) {
        if(gpsFormat != null){
            if(gpsFormat.equalsIgnoreCase("degree-minutes")){
                this.valueFormat = GpsInput.ValueFormat.DEGREE_MINUTES;
            } else if(gpsFormat.equalsIgnoreCase("degree-minutes-seconds")){
                this.valueFormat = GpsInput.ValueFormat.DEGREE_MINUTES_SECONDS;
            } else if (gpsFormat.equalsIgnoreCase("ascii")) {
                this.valueFormat = GpsInput.ValueFormat.ASCII_;
            } else {
                this.valueFormat = GpsInput.ValueFormat.FLOAT;
            }
        }

    }

    public void setNegativeUnit(String negativeUnit) {
        this.negativeUnit = negativeUnit;
    }

    public void setPositiveUnit(String positiveUnit) {
        this.positiveUnit = positiveUnit;
    }

    //Interface to set conversion factor. The element will show inputValue times this factor
    public void setFactor(double factor) {
        this.factor = factor;
    }

    //Interface to set the unit string
    public void setUnit(String unit) {
        //If there is a unit we will save the space in this string as well...
        if (unit == null || unit.equals(""))
            this.unit = "";
        else
            this.unit = " "+unit;
    }

    @Override
    //This is a single value. So the updateMode is "single"
    public String getUpdateMode() {
        if(this.valueFormat == GpsInput.ValueFormat.ASCII_){
            return "full";
        }
        return "single";
    }

    @Override
    //Append the Android vews we need to the linear layout
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
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
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
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
                    double factoredValue = x * this.factor;
                    if(valueFormat != null){
                        if(valueFormat == GpsInput.ValueFormat.ASCII_)
                            vStr = convertDecimalToAscii(experiment.getBuffer(inputs.get(0)).getArray());
                        else
                            vStr = formatGeoCoordinate(factoredValue, valueFormat);
                    } else {
                        vStr = String.format(this.formatter, factoredValue);
                    }

                    if(positiveUnit != null && (factoredValue >= 0) ){
                        uStr = this.positiveUnit;
                    } else if(negativeUnit != null && (factoredValue < 0)){
                        uStr = this.negativeUnit;
                    } else {
                        uStr = this.unit;
                    }

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

    /**
     * Formats a geographical coordinate (latitude or longitude) into a string representation
     * based on the specified output format.
     *
     * @param coordinate   The coordinate value (latitude or longitude) as a double.
     *                     Positive values represent North/East, negative values represent South/West.
     * @param outputFormat The desired output format for the coordinate.
     *                     Can be one of:
     *                     <ul>
     *                         <li>{@link GpsInput.ValueFormat#DEGREE_MINUTES}: Degree and decimal minutes (e.g., 40° 26.767')</li>
     *                         <li>{@link GpsInput.ValueFormat#DEGREE_MINUTES_SECONDS}: Degree, minutes, and decimal seconds (e.g., 40° 26' 46.020'')</li>
     *                         <li>{@link GpsInput.ValueFormat#FLOAT}: Decimal degrees (e.g., 40.446)</li>
     *                     </ul>
     * @return A string representation of the coordinate in the specified format.
     *         The string will be formatted to three decimal places for the decimal parts.
     * @see GpsInput.ValueFormat
     */
    private String formatGeoCoordinate(Double coordinate, GpsInput.ValueFormat outputFormat) {
        int degree = coordinate.intValue();
        double decimalMinutes = Math.abs((coordinate - degree) * 60);
        int integralMinutes = (int) decimalMinutes;
        double decimalSeconds = Math.abs((decimalMinutes - integralMinutes) * 60);

        switch (outputFormat) {
            case DEGREE_MINUTES:
                return degree  + "° " + String.format(this.formatter, decimalMinutes) + "' ";

            case DEGREE_MINUTES_SECONDS:
                return degree + "° " + integralMinutes + "' "  + String.format(this.formatter, decimalSeconds) + "'' ";
            case FLOAT:
            default:
                return String.format(this.formatter, coordinate) + " " ;
        }
    }

    private String convertDecimalToAscii(Double[] decimals) {
        StringBuilder sb = new StringBuilder();
        for (Double decimal : decimals) {
            if(decimal == null){
                continue;
            }
            int ascii = (int) Math.round(decimal);
            if(ascii < 126 && ascii > 31){
                sb.append((char) ascii);
            }
        }
        return sb.toString();
    }

    @Override
    //In Javascript we just have to set the content of the value <span> to the value using jquery
    public String setDataHTML() {
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

        sb.append("     var unitLabel = \""+ this.unit + "\";");
        if(positiveUnit != null){
            sb.append("     if(x >= 0 ){");
            sb.append("         unitLabel =  \""+ positiveUnit + "\";");
            sb.append("     };");
        } else if(negativeUnit != null){
            sb.append("     if(x < 0 ){");
            sb.append("         unitLabel = \""+ negativeUnit + "\";");
            sb.append("     };");
        }

        sb.append("     var degree = Math.floor(x);");
        sb.append("     var decibelMinutes = Math.abs((x - degree) * 60);");
        sb.append("     var integralMinutes = Math.floor(decibelMinutes);");
        sb.append("     var decibelSeconds = Math.abs(decibelMinutes - integralMinutes) * 60;");
        sb.append("     x =  x*" + factor + ";" );

        if(valueFormat == null){
            sb.append("     x = x.to"+(scientificNotation ? "Exponential" : "Fixed")+"("+precision+");");
        } else {
            if(valueFormat == GpsInput.ValueFormat.DEGREE_MINUTES){
                sb.append("         x =  degree + \"° \" + decibelMinutes.to"+(scientificNotation ? "Exponential" : "Fixed")+"("+precision+")  +  \"' \"  ;");
            } else if(valueFormat == GpsInput.ValueFormat.DEGREE_MINUTES_SECONDS){
                sb.append("         x = degree + \"° \" + integralMinutes + \"' \" +  decibelSeconds.to"+(scientificNotation ? "Exponential" : "Fixed")+"("+precision+") + \"'' \"  ;");
            } else if(valueFormat == GpsInput.ValueFormat.ASCII_){
                sb.append("         var decimals = data[\""+bufferName+"\"][\"data\"];");
                sb.append("         var x_ = \""+"\"  ;");
                sb.append("         decimals.forEach(decimal => {");
                sb.append("             if (decimal !== null && decimal !== undefined) {");
                sb.append("                 const intDecimal = Math.round(decimal);");
                    sb.append("                 if (intDecimal > 31 && intDecimal < 126) {");
                sb.append("                     const asciiCharacter = String.fromCharCode(intDecimal);");
                sb.append("                     x_ += asciiCharacter;");
                sb.append("                 } else {");
                sb.append("                     console.log(`No valid ASCII character for decimal ${intDecimal}.`);");
                sb.append("                 }");
                sb.append("              }");
                sb.append("         x = x_;");
                sb.append("         });");

            } else {
                sb.append("         x = x.to"+(scientificNotation ? "Exponential" : "Fixed")+"("+precision+");");
            }
        }

        sb.append("     var valueElement = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"value\")[0];");
        sb.append("     var valueNumber = valueElement.getElementsByClassName(\"valueNumber\")[0];");
        sb.append("     var valueUnit = valueElement.getElementsByClassName(\"valueUnit\")[0];");
        sb.append("     if (v == null) {");
        sb.append("         v = x;");
        sb.append("         valueUnit.textContent = unitLabel;");
        sb.append("     } else {");
        sb.append("         valueUnit.textContent = \"\";");
        sb.append("     }");
        sb.append("     valueNumber.textContent = v;");

        sb.append("}");

        return sb.toString();
    }
}
