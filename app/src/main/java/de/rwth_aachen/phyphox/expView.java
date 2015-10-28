package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.Vector;

public class expView{
    public abstract class expViewElement {
        protected String label;
        protected float labelSize;
        protected String valueInput;
        protected String dataXInput;
        protected String dataYInput;
        protected int htmlID;
        protected Resources res;

        protected expViewElement(String label, String valueInput, String dataXInput, String dataYInput, Resources res) {
            this.label = label;
            this.labelSize = 25;
            this.valueInput = valueInput;
            this.dataXInput = dataXInput;
            this.dataYInput = dataYInput;
            this.res = res;
        }

        protected void setLabelSize(float size) {
            this.labelSize = size;
        }

        protected abstract void createView(LinearLayout ll, Context c);
        protected abstract String createViewHTML();
        protected String getViewHTML(int id) {
            this.htmlID = id;
            return createViewHTML();
        }

        protected abstract String getUpdateMode();

        protected void setValue(double x) {

        }

        protected String setValueHTML() {
            return "function(x) {}";
        }

        protected void setDataX(dataBuffer x) {

        }

        protected String setDataXHTML() {
            return "function(x) {}";
        }

        protected void setDataY(dataBuffer y) {

        }

        protected String setDataYHTML() {
            return "function(y) {}";
        }

        protected void dataComplete() {

        }

        protected String dataCompleteHTML() {
            return "function() {}";
        }

        protected String getValueInput() {
            return this.valueInput;
        }

        protected String getDataXInput() {
            return this.dataXInput;
        }

        protected String getDataYInput() {
            return this.dataYInput;
        }
    }

    public class valueElement extends expViewElement {
        private TextView tv;
        private double factor;
        private boolean scientificNotation;
        private int precision;
        private String formatter;
        private String unit;

        valueElement(String label, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueInput, dataXInput, dataYInput, res);
            this.scientificNotation = false;
            this.precision = 2;
            updateFormatter();
            this.unit = "";
            this.factor = 1.;
        }

        protected void updateFormatter() {
            if (scientificNotation)
                formatter = "%."+precision+"e";
            else
                formatter = "%."+precision+"f";
        }

        protected void setScientificNotation(boolean sn) {
            this.scientificNotation = sn;
            updateFormatter();
        }

        protected void setPrecision(int p) {
            this.precision = p;
            updateFormatter();
        }

        protected void setFactor(double factor) {
            this.factor = factor;
        }

        protected void setUnit(String unit) {
            if (unit == null || unit.equals(""))
                this.unit = "";
            else
                this.unit = " "+unit;
        }

        @Override
        protected String getUpdateMode() {
            return "single";
        }

        @Override
        protected void createView(LinearLayout ll, Context c){
            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView labelView = new TextView(c);
            labelView.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f));
            labelView.setText(this.label);
            labelView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setPadding(0, 0, (int) labelSize / 2, 0);
            labelView.setTextColor(res.getColor(R.color.main));

            tv = new TextView(c);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.5f));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            tv.setPadding((int) labelSize / 2, 0, 0, 0);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(res.getColor(R.color.main));

            row.addView(labelView);
            row.addView(tv);
            ll.addView(row);
        }

        @Override
        protected String createViewHTML(){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"valueElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\" \">"+this.label+"</span>" +
                    "<span class=\"value\" \"></span>" +
                    "</div>";
        }

        @Override
        protected void setValue(double x) {
            tv.setText(String.format(this.formatter, x*this.factor)+this.unit);
        }

        @Override
        protected String setValueHTML() {
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

    public class graphElement extends expViewElement {
        private graphView gv = null;
        private int height;
        private Double[] dataX;
        private Double[] dataY;
        private boolean line = false;
        private int historyLength = 1;
        private String labelX = null;
        private String labelY = null;
        private boolean partialUpdate = false;

        graphElement(String label, String valueInput, String dataXInput, String dataYInput, Resources res) {
            super(label, valueInput, dataXInput, dataYInput, res);
            height = 300;
        }

        protected void setHeight(int height) {
            this.height = height;
        }

        protected void setLine(boolean line) {
            this.line = line;
            if (gv != null)
                gv.setLine(line);
        }

        protected void setHistoryLength(int hl) {
            this.historyLength = hl;
            if (gv != null)
                gv.setHistoryLength(hl);
        }

        protected void setLabel(String labelX, String labelY) {
            this.labelX = labelX;
            this.labelY = labelY;
            if (gv != null)
                gv.setLabel(labelX, labelY);
        }

        protected void setPartialUpdate(boolean pu) {
            this.partialUpdate = pu;
        }

        @Override
        protected String getUpdateMode() {
            if (partialUpdate)
                return "partial";
            else
                return "full";
        }

        @Override
        protected void createView(LinearLayout ll, Context c){
            LinearLayout row = new LinearLayout(c);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.VERTICAL);

            TextView labelView = new TextView(c);
            labelView.setLayoutParams(new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            labelView.setText(this.label);
            labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
            labelView.setTextColor(res.getColor(R.color.main));

            gv = new graphView(c);
            gv.setLayoutParams(new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height));

            gv.setLine(line);
            gv.setHistoryLength(historyLength);
            gv.setLabel(labelX, labelY);

            row.addView(labelView);
            row.addView(gv);
            ll.addView(row);

        }

        @Override
        protected String createViewHTML(){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"graphElement\" id=\"element"+htmlID+"\">" +
                    "<span class=\"label\" \">"+this.label+"</span>" +
                    "<div class=\"graph\" style=\"height:"+this.height/10.+"vh;\" \"></div>" +
                    "</div>";
        }

        @Override
        protected void setDataX(dataBuffer x) {
            dataX = x.getArray();
        }

        @Override
        protected String setDataXHTML() {
            return "function (x) {"+
                        "elementData["+htmlID+"][\"x\"] = x" +
                    "}";
        }

        @Override
        protected void setDataY(dataBuffer y) {
            dataY = y.getArray();
        }

        @Override
        protected String setDataYHTML() {
            return "function (y) {"+
                        "elementData["+htmlID+"][\"y\"] = y" +
                    "}";
        }

        @Override
        protected void dataComplete() {
            if (dataY != null) {
                if (dataX != null) {
                    gv.addGraphData(dataY, dataX);
                    dataX = null;
                } else
                    gv.addGraphData(dataY);
                dataY = null;
            }
        }

        @Override
        protected String dataCompleteHTML() {
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
                        "$.plot(\"#element"+htmlID+" .graph\", [{ \"color\": \"" + "#"+String.format("%08x", res.getColor(R.color.highlight)).substring(2) + "\" , \"data\": d }], {\"xaxis\": {\"axisLabel\": \""+this.labelX+"\", \"ticks\": 3, \"tickColor\": \""+ "#"+String.format("%08x", res.getColor(R.color.grid)).substring(2) +"\"}, \"yaxis\": {\"axisLabel\": \""+this.labelY+"\", \"ticks\": 3, \"tickColor\": \""+ "#"+String.format("%08x", res.getColor(R.color.grid)).substring(2) +"\"}, \"grid\": {\"borderColor\": \""+ "#"+String.format("%08x", res.getColor(R.color.main)).substring(2) +"\"}});" +
                    "}";
        }
    }

    public String name;
    public Vector<expViewElement> elements = new Vector<>();
}