package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.RGB;

public class SliderElement extends ExpViewElement implements Serializable {

    public enum SliderType {
        Normal, Range
    }

    private double defaultValue, maxValue, minValue, stepSize;
    private int precision;
    private SliderType type;
    private Boolean showValue;
    private RGB color;
    boolean triggered = false;
    TextView valueTv;
    Slider slider;
    RangeSlider rangeSlider;

    public SliderElement(String label, String visibility, Vector<String> valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
    }

    public void setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    public void setStepSize(double stepSize) {
        this.stepSize = stepSize;
    }

    public void setPrecision(int precision) { this.precision = precision; }

    public void setType(SliderType type) { this.type = type; }

    public void setShowValue(Boolean showValue) { this.showValue = showValue; }

    public void setColor(RGB color) {
        this.color = color;
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        super.createView(ll, c, res, parent, experiment);

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout column = new LinearLayout(c);
        column.setLayoutParams(layoutParams);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout row = new LinearLayout(c);
        row.setLayoutParams(layoutParams);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Second row is for slider view
        LinearLayout secondRow = new LinearLayout(c);
        secondRow.setLayoutParams(layoutParams);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.CENTER | Gravity.CENTER_HORIZONTAL);

        TextView labelView = new TextView(c);
        labelView.setLayoutParams(getTableRowParams(0.5f)); //left half should be label
        labelView.setText(this.label);
        labelView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); //Align right to the center of the row
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
        labelView.setPadding(0, 0, (int) labelSize / 2, 0);
        labelView.setTextColor(color.autoLightColor(res).intColor());

        valueTv = new TextView(c);
        valueTv.setLayoutParams(getTableRowParams(0.5f)); //right half should be value+unit
        valueTv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize); //Align left to the center of the row
        valueTv.setPadding((int) labelSize / 2, 0, 0, 0);
        valueTv.setTypeface(null, Typeface.BOLD);
        valueTv.setTextColor(color.autoLightColor(res).intColor());
        valueTv.setText(numberFormatter(defaultValue));

        TextView minValueLabel = new TextView(c);
        minValueLabel.setLayoutParams(getTableRowParams(0.1f));
        minValueLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        minValueLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize / 1.5f);
        minValueLabel.setTextColor(color.autoLightColor(res).intColor());
        minValueLabel.setText(numberFormatter(minValue));

        TextView maxValueLabel = new TextView(c);
        maxValueLabel.setLayoutParams(getTableRowParams(0.1f));
        maxValueLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        maxValueLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize / 1.5f);
        maxValueLabel.setTextColor(color.autoLightColor(res).intColor());
        maxValueLabel.setText(numberFormatter(maxValue));

        //Add label and value to the row
        if (showValue) {
            row.addView(labelView);
            row.addView(valueTv);
        }

        secondRow.addView(minValueLabel);

        LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if(type == SliderType.Range){
            View rangeSliderView = inflater.inflate(R.layout.range_slider, null);
            rangeSlider = rangeSliderView.findViewById(R.id.sliderView);
            rangeSlider.setLabelBehavior(LabelFormatter.LABEL_GONE);
            //The formatter is also what TalkBack announces, so it has to undo the step conversion
            rangeSlider.setLabelFormatter(value -> numberFormatter(getSteppedValue(value)));
            rangeSlider.setLayoutParams(getTableRowParams(0.9f));
            if(stepSize != 0.0) {
                //Note: Android's requirements for the step size to perfectly fit into the given range seems rather restrictive and is quite prone to errors given the limited floating point precision and values converted from user strings. So, if we need steps, we make our own and can make sure that the behavior matches our iOS implementation.
                rangeSlider.setValueFrom(0);
                rangeSlider.setValueTo((float)(Math.ceil(maxValue/stepSize) - Math.floor(minValue/stepSize)));
                rangeSlider.setStepSize(1);
            } else {
                rangeSlider.setValueFrom((float)minValue);
                rangeSlider.setValueTo((float)maxValue);
            }
            secondRow.addView(rangeSlider);

            rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
                float lowerValue = slider.getValues().get(0);
                float upperValue = slider.getValues().get(1);
                if(showValue)
                    valueTv.setText(getFormattedRangeValue(getSteppedValue(lowerValue), getSteppedValue(upperValue)));
                rangeSlider.setValues(lowerValue, upperValue);
                if (fromUser)
                    triggered = true;
            });

        }  else {
            View view = inflater.inflate(R.layout.slider, null);
            slider = view.findViewById(R.id.sliderView);
            if(stepSize != 0.0) {
                //Note: Android's requirements for the step size to perfectly fit into the given range seems rather restrictive and is quite prone to errors given the limited floating point precision and values converted from user strings. So, if we need steps, we make our own and can make sure that the behavior matches our iOS implementation.
                slider.setValueFrom(0);
                slider.setValueTo((float)(Math.ceil(maxValue/stepSize) - Math.floor(minValue/stepSize)));
                slider.setStepSize(1);
            } else {
                slider.setValueFrom((float)minValue);
                slider.setValueTo((float)maxValue);
            }
            slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
            //Same as for the range slider above: what TalkBack announces
            slider.setLabelFormatter(value -> numberFormatter(getSteppedValue(value)));
            slider.setValue((float)defaultValue);
            slider.setLayoutParams(getTableRowParams(0.9f));
            slider.addOnChangeListener((slider, value, fromUser) -> {
                valueTv.setText(numberFormatter(getSteppedValue(value)));
                slider.setValue(value);
                if (fromUser)
                    triggered = true;
            });
            secondRow.addView(slider);
        }

        secondRow.addView(maxValueLabel);
        column.addView(row);
        column.addView(secondRow);
        //Add the row to the linear layout
        rootView = column;
        rootView.setFocusableInTouchMode(true);
        ll.addView(rootView);

    }

    private TableRow.LayoutParams getTableRowParams(float weight){
        return new TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
    }

    private String getFormattedRangeValue(double lowerValue, double upperValue){
        return numberFormatter(lowerValue) +
                " - " +
                numberFormatter(upperValue);
    }

    private String numberFormatter(double value){
       return BigDecimal
               .valueOf(value)
               .setScale(precision, RoundingMode.HALF_UP)
               .toString();
    }

    protected double getSteppedValue(double value) {
        double steppedVal;
        if (stepSize == 0.0)
            steppedVal = value;
        else
            steppedVal = (value + Math.floor(minValue / stepSize)) * stepSize;

        return Math.min(Math.max(steppedVal, minValue), maxValue);
    }

    //Stepped and clamped, so the seeded buffer never holds a value the slider does not display
    private double displayedValueFor(double configured) {
        if (stepSize == 0.0)
            return Math.min(Math.max(configured, minValue), maxValue);
        return getSteppedValue(Math.round(configured / stepSize) - Math.floor(minValue / stepSize));
    }

    //Seeds empty buffers from the attributes, so it works before the widget has been laid out
    private boolean seedEmptyBuffers(PhyphoxExperiment experiment) {
        if (inputs.size() == 0)
            return false;
        boolean seeded = applyDefault(experiment, inputs.get(0),
                displayedValueFor(type == SliderType.Range ? minValue : defaultValue));
        if (type == SliderType.Range && inputs.size() > 1)
            seeded |= applyDefault(experiment, inputs.get(1), displayedValueFor(maxValue));
        return seeded;
    }

    @Override
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        boolean seeded = seedEmptyBuffers(experiment);

        if(!triggered)
            return seeded;
        triggered = false;

        DataBuffer mainBuffer = null;
        if (inputs.size() > 0) {
            mainBuffer = experiment.getBuffer(inputs.get(0));
        }

        if(type == SliderType.Range){
            List<Float> values = rangeSlider.getValues();
            if (values.size() == 2) {
                if (mainBuffer != null) {
                    mainBuffer.clear(false);
                    mainBuffer.append(getSteppedValue(values.get(0)));
                }
                DataBuffer upperBuffer = null;
                if (inputs.size() > 1) {
                    upperBuffer = experiment.getBuffer(inputs.get(1));
                }
                if (upperBuffer != null) {
                    upperBuffer.clear(false);
                    upperBuffer.append(getSteppedValue(values.get(1)));
                }
            }
        } else {
            if (mainBuffer != null) {
                mainBuffer.clear(false);
                mainBuffer.append(getSteppedValue(slider.getValue()));
            }
        }
        return true;
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (!needsUpdate || triggered)
            return;
        //The read pass also runs for pages the pager has not built yet
        if (type == SliderType.Range ? rangeSlider == null : slider == null)
            return;
        needsUpdate = false;
        if (inputs.size() == 0)
            return;
        double value = experiment.getBuffer(inputs.get(0)).value;
        if (Double.isNaN(value))
            value = (type == SliderType.Range) ? minValue: defaultValue;

        if (stepSize != 0.0)
            value = Math.round(value/stepSize) -  Math.floor(minValue / stepSize);

        if(type == SliderType.Range){
            if (value < rangeSlider.getValueFrom())
                value = rangeSlider.getValueFrom();
            if (value > rangeSlider.getValueTo())
                value = rangeSlider.getValueTo();

            if (inputs.size() != 2)
                return;
            double upperValue = experiment.getBuffer(inputs.get(1)).value;
            if(Double.isNaN(upperValue))
                upperValue = maxValue;

            if (stepSize != 0.0)
                upperValue = Math.round(upperValue/stepSize) -  Math.floor(minValue / stepSize);

            if (upperValue < rangeSlider.getValueFrom())
                upperValue = rangeSlider.getValueFrom();
            if (upperValue > rangeSlider.getValueTo())
                upperValue = rangeSlider.getValueTo();

            rangeSlider.setValues((float) value, (float) upperValue);
            if(showValue)
                valueTv.setText(getFormattedRangeValue((float) getSteppedValue(value), (float) getSteppedValue(upperValue)));
        } else {
            if (value < slider.getValueFrom())
                value = slider.getValueFrom();
            if (value > slider.getValueTo())
                value = slider.getValueTo();
            slider.setValue((float) value);
            if(showValue)
                valueTv.setText(numberFormatter(getSteppedValue(value)));
        }
    }

    boolean isTriggered = false;

    @Override
    protected boolean isFocused() {
        return isTriggered;
    }

    @Override
    protected String createViewHTML() {
        String valueTag = showValue ? "<span class=\"label\">"+this.label+"</span>" +
                "<span class=\"value\" id=\"value"+htmlID+"\">"+defaultValue+"</span>" : "";
        return (type == SliderType.Range) ? getTwoSlidersVerticallyHTML() :

             "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"sliderElement\" id=\"element"+htmlID+"\">" +
                     valueTag +
                    "<div class=\"sliderContainer\">" +
                        "<span class=\"minValue\" >"+minValue+"</span>" +
                            "<input type=\"range\" class=\"slider\" id=\"input"+htmlID+"\"" +
                                "min=\"1\" max=\"100\" value=\"100\" step="+stepSize+"\""+
                                ">" +
                            "</input>" +
                        "<span class=\"maxValue\">"+maxValue+"</span>" +
                     "</div>" +
                "</div>";

    }


    private String getTwoSlidersVerticallyHTML(){
        String valueTag = showValue ? "<span class=\"label\">"+this.label+"</span>" +
                "<span class=\"value\" id=\"value"+htmlID+"\">"+defaultValue+"</span>" : "" ;
        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"sliderElement\" id=\"element"+htmlID+"\">" +
                                        valueTag +
                                        "<div class=\"sliderContainer\">" +
                                        "<span class=\"minValue\" >"+minValue+"</span>" +
                                            "<input type=\"range\" class=\"slider\" id=\"input"+htmlID+"\"" +
                                                "min="+minValue+"\" max="+maxValue+"\" value="+defaultValue+"\" step="+stepSize+"\" "+
                                                ">" +
                                            "</input>" +
                                        "<span class=\"maxValue\"></span>" +
                                        "</div>" +
                                        "<div class=\"sliderContainer\">" +
                                        "<span class=\"minValue\"></span>" +
                                        "<input type=\"range\" class=\"slider\" id=\"input1"+htmlID+"\"" +
                                            "min="+minValue+"\" max="+maxValue+"\" step="+stepSize+"\""+
                                                ">" +
                                                "</input>" +
                                            "<span class=\"maxValue\">"+maxValue+"</span>" +
                                        "</div>" +
                                "</div>";
    }

    @Override
    public String setDataHTML() {
        String bufferName = inputs.get(0).replace("\"", "\\\"");

        return type == SliderType.Range ? setHTMLForRangeSlider() :

            "function (data) {\n" +
                "                    if (!data.hasOwnProperty(\""+bufferName+"\"))\n" +
                "                        return;\n" +
                "                    var x = data[\""+bufferName+"\"][\"data\"][data[\""+bufferName+"\"][\"data\"].length - 1];\n" +
                "                    var selectedValue = parseFloat(x).toFixed("+precision+")\n" +
                "                    var sliderElement = document.getElementById(\"input"+htmlID+"\")\n" +
                "                    var valueDisplay = document.getElementById(\"value"+htmlID+"\");\n" +
                "                    if (sliderElement) {\n" +
                "                        sliderElement.min = "+minValue+";\n" +
                "                        sliderElement.max = "+maxValue+";\n" +
                "                        sliderElement.step = "+stepSize+";\n" +
                "                    }\n" +
                "                   if (!sliderElement.classList.contains(\"isSliderUpdating\")) {\n"+
                "                        sliderElement.value = selectedValue || "+defaultValue+" ; \n" +
                "                   }\n"+

                "                    if(valueDisplay){ \n"+
                "                        valueDisplay.textContent = parseFloat(sliderElement.value).toFixed("+precision+");\n"+
                "                    }\n" +

                "                    sliderElement.addEventListener('input', function() {\n"+
                "                        if (!sliderElement.classList.contains(\"isSliderUpdating\")) {\n"+
                "                            sliderElement.classList.add(\"isSliderUpdating\");\n"+
                "                         }\n" +
                "                     });\n" +

                "                      sliderElement.addEventListener('change', function() {\n"+
                "                              if (valueDisplay) {\n" +
                "                                  valueDisplay.textContent = parseFloat(sliderElement.value).toFixed("+precision+");\n" +
                "                               }\n" +
                "                               if (sliderElement.classList.contains(\"isSliderUpdating\")) {"+
                "                                   ajax('control?cmd=set&buffer="+getValueOutputs().get(0)+"&value='+sliderElement.value);\n"+
                "                                   sliderElement.classList.remove(\"isSliderUpdating\");"+
                "                               }\n" +
                "                        });\n" +
                "            }";
    }

    private String setHTMLForRangeSlider() {
        String lowerValueBufferName = inputs.get(0).replace("\"", "\\\"");
        String upperValueBufferName = inputs.get(1).replace("\"", "\\\"");

        return "function (data) {\n" +
                "                    if (!data.hasOwnProperty(\""+lowerValueBufferName+"\"))\n" +
                "                        return;\n" +
                "                    if (!data.hasOwnProperty(\""+upperValueBufferName+"\"))\n" +
                "                        return;\n" +

                "                    var x = data[\""+lowerValueBufferName+"\"][\"data\"][data[\""+lowerValueBufferName+"\"][\"data\"].length - 1];\n" +
                "                    var y = data[\""+upperValueBufferName+"\"][\"data\"][data[\""+upperValueBufferName+"\"][\"data\"].length - 1];\n" +

                "                    var selectedValueX = parseFloat(x).toFixed("+precision+")\n" +
                "                    var selectedValueY = parseFloat(y).toFixed("+precision+")\n" +

                "                    var sliderElementOne = document.getElementById(\"input"+htmlID+"\")\n" +
                "                    var sliderElementTwo = document.getElementById(\"input1"+htmlID+"\")\n" +
                "                    var valueDisplay = document.getElementById(\"value"+htmlID+"\");\n" +

                "                    if (sliderElementOne && sliderElementTwo) {\n" +
                "                        sliderElementOne.min = "+minValue+";\n" +
                "                        sliderElementTwo.min = "+minValue+";\n" +
                "                        sliderElementOne.max = "+maxValue+";\n" +
                "                        sliderElementTwo.max = "+maxValue+";\n" +
                "                        sliderElementOne.step = "+stepSize+";\n" +
                "                        sliderElementTwo.step = "+stepSize+";\n" +
                "                    }\n else { return; } \n" +

                                    //The following check is done so that it doesn't update the slider element when the user is interacting with it.
                                    //When the user is sliding the slider the class name 'focus' is added and when the slider is released the class is deleted
                                    //This lets us to check when the user is not interacting with the slider, so that slider can be updated with the new buffer value
                "                   if (!sliderElementOne.classList.contains(\"isSliderOneUpdating\")) {\n"+
                "                        sliderElementOne.value = selectedValueX;\n" +
                "                   }\n"+
                "                   if (!sliderElementTwo.classList.contains(\"isSliderTwoUpdating\")) {\n"+
                "                        sliderElementTwo.value = selectedValueY;\n" +
                "                   }\n"+

                "                    if(valueDisplay){ \n"+
                "                        valueDisplay.textContent = parseFloat(sliderElementOne.value).toFixed("+precision+").concat(\" - \", parseFloat(sliderElementTwo.value).toFixed("+precision+"));\n"+
                "                    }\n" +

                "                       sliderElementOne.addEventListener('input', function() {\n"+
                "                           if (!sliderElementOne.classList.contains(\"isSliderOneUpdating\")) {\n"+
                "                               sliderElementOne.classList.add(\"isSliderOneUpdating\")\n"+
                "                           }\n" +
                "                           if(Number(sliderElementOne.value) > Number(sliderElementTwo.value)) {\n"+
                "                               sliderElementOne.value = sliderElementTwo.value\n"+
                "                            }\n" +
                "                        });\n" +

                "                       sliderElementOne.addEventListener('change', function() {\n"+
                "                            if(Number(sliderElementOne.value) <= Number(sliderElementTwo.value)) {\n"+
                "                                if (valueDisplay) {\n" +
                "                                   valueDisplay.textContent = parseFloat(sliderElementOne.value).toFixed("+precision+").concat(\" - \", parseFloat(sliderElementTwo.value).toFixed("+precision+"));\n" +
                "                                 }\n" +
                "                               if (sliderElementOne.classList.contains(\"isSliderOneUpdating\")) {"+
                "                                   ajax('control?cmd=set&buffer="+getValueOutputs().get(0)+"&value='+sliderElementOne.value)\n"+
                "                                   sliderElementOne.classList.remove(\"isSliderOneUpdating\")"+
                "                               }\n" +
                "                            }\n" +
                "                        });\n" +

                "                       sliderElementTwo.addEventListener('input', function() {\n"+
                "                           if (!sliderElementTwo.classList.contains(\"isSliderTwoUpdating\")) {\n"+
                "                               sliderElementTwo.classList.add(\"isSliderTwoUpdating\")\n"+
                "                           }\n" +
                "                           if(Number(sliderElementOne.value) > Number(sliderElementTwo.value)) {\n"+
                "                               sliderElementTwo.value = sliderElementOne.value\n"+
                "                            }\n" +
                "                        });\n" +

                "                       sliderElementTwo.addEventListener('change', function() {\n"+
                "                            if(Number(sliderElementOne.value) <= Number(sliderElementTwo.value)) {\n"+
                "                                if (valueDisplay) {\n" +
                "                                   valueDisplay.textContent = parseFloat(sliderElementOne.value).toFixed("+precision+").concat(\" - \", parseFloat(sliderElementTwo.value).toFixed("+precision+"));\n" +
                "                                 }\n" +
                "                               if (sliderElementTwo.classList.contains(\"isSliderTwoUpdating\")) {"+
                "                                   ajax('control?cmd=set&buffer="+getValueOutputs().get(1)+"&value='+sliderElementTwo.value)\n"+
                "                                   sliderElementTwo.classList.remove(\"isSliderTwoUpdating\")"+
                "                               }\n" +
                "                            }\n" +
                "                        });\n" +
                "       }";
    }

    @Override
    public String getUpdateMode() {
        return "input";
    }
}
