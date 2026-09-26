package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.Locale;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.DecimalTextWatcher;

//EditElement implements a simple edit box which takes a single value from the user
public class EditElement extends ExpViewElement implements Serializable {
    transient EditText et = null;
    transient private ValueAnimator commitAnimator = null;
    private double factor; //factor used for conversion. Mostly for prefixes like m, k, M, G...
    private String unit; //A string to display as unit
    private double defaultValue; //This value is filled into the dataBuffer before the user enters a custom value
    private double currentValue = Double.NaN; //This value is filled into the dataBuffer before the user enters a custom value
    private boolean signed = true; //Is the user allowed to give negative values?
    private boolean decimal = true; //Is the user allowed to give non-integer values?
    private Double min = Double.NEGATIVE_INFINITY;
    private Double max = Double.POSITIVE_INFINITY;
    private boolean focused = false; //Is the element currently focused? (Updates should be blocked while the element has focus and the user is working on its content)

    private boolean triggered = false; //Set by user interaction only; seeding the default is left to applyDefault()
    private boolean editable = true;

    public String label;

    private PhyphoxExperiment phyphoxExperiment;

    private LinearLayout root_ll;
    private Context c;


    //No special constructor. Just some defaults.
    public EditElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
        this.label = label;
        this.unit = "";
        this.factor = 1.;
    }

    //Interface to set the conversion factor
    public void setFactor(double factor) {
        this.factor = factor;
    }

    //Interface to set a default value
    public void setDefaultValue(double v) {
        this.defaultValue = v;
    }

    //Interface to set the unit string
    public void setUnit(String unit) {
        if (unit == null || unit.equals(""))
            this.unit = "";
        else
            this.unit = unit;
    }

    //Interface to allow signed values
    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    //Interface to allow non-integer values
    public void setDecimal(boolean decimal) {
        this.decimal = decimal;
    }

    //Interface to set limits
    public void setLimits(double min, double max) {
        this.min = min;
        this.max = max;
    }

    protected void setEditable(boolean editable){
        this.editable = editable;
    }

    @Override
    //This is an input, so the updateMode should be "input"
    public String getUpdateMode() {
        return "input";
    }

    @Override
    //Create the view in Android and append it to the linear layout
    public void createView(LinearLayout ll, final Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
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
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (decimal) {
            et.setKeyListener(DigitsKeyListener.getInstance(allowedDigits.toString()));
            et.setRawInputType(inputType);
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
        arrangeLabelAndControl(row, labelView, valueUnit);
        if (isFullWidth() && align != Gravity.START)
            et.setGravity(align | Gravity.CENTER_VERTICAL); //the field spans the row with its unit; only its text follows align

        rootView = row;
        rootView.setFocusableInTouchMode(true);

        //Add the row to the main linear layout passed to this function
        root_ll.addView(rootView);

        final Drawable originalBackground = et.getBackground();
        final ColorDrawable overlay = new ColorDrawable(Color.TRANSPARENT);
        final LayerDrawable combinedBackground = new LayerDrawable(new Drawable[]{originalBackground, overlay});
        et.setBackground(combinedBackground);

        final int colorYellow = ContextCompat.getColor(c, R.color.phyphox_yellow);
        final int colorGreen = ContextCompat.getColor(c, R.color.phyphox_green);

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (et.hasFocus()) {
                    overlay.setColor(colorYellow);
                    overlay.setAlpha(100);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        et.setOnFocusChangeListener((v, hasFocus) -> {
            focused = hasFocus;
            if (commitAnimator != null) {
                commitAnimator.cancel();
            }
            if (!hasFocus) {
                setValue(getValue()); //Write back the value actually used...
                triggered = true;

                overlay.setColor(colorGreen);
                commitAnimator = ValueAnimator.ofInt(255, 0);
                commitAnimator.setDuration(500);
                commitAnimator.addUpdateListener(animator -> overlay.setAlpha((int) animator.getAnimatedValue()));
                commitAnimator.start();
            } else {
                overlay.setAlpha(0);
            }
        });

        et.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
                }
                et.clearFocus();
                return true;
            }
            return false;
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

        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"editElement" + labelLayoutClass() + "\" id=\"element"+htmlID+"\">" +
                labelHTML() +
                "<input onchange=\"ajax('control?cmd=set&buffer="+valueOutput+"&value='+this.value/"+ factor + ")\" type=\"number\" class=\"value\" " + restrictions + " />" +
                "<span class=\"unit\">"+this.unit+"</span>" +
                "</div>";
    }

    //Get the value from the edit box (Note, that we have to divide by the factor to achieve a
    //use that is consistent with that of the ValueElement
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
            //A NaN is only shown as the default; the buffer gets it from applyDefault() on the next write pass
            if (Double.isNaN(v))
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
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        boolean seeded = inputs.size() > 0
                && applyDefault(experiment, inputs.get(0), defaultValue);
        if (!triggered)
            return seeded;
        triggered = false;
        experiment.getBuffer(inputs.get(0)).clear(false);
        experiment.getBuffer(inputs.get(0)).append(getValue());
        return true;
    }

    @Override
    //Set the value if the element is not focused
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        //Enter value from buffer if it has not been changed by the user
        //This ensures, that the old value is restored if the view has to be created after the views have been switched.
        double v = experiment.getBuffer(inputs.get(0)).value;
        setValue(v);
    }

    @Override
    protected void clear() {

    }

    @Override
    //The javascript function which updates the content of the input as it is updated on the phone
    public String setDataHTML() {
        String bufferName = inputs.get(0).replace("\"", "\\\"");
        return "function (data) {" +
                "var valueElement = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"value\")[0];" +
                "if (!data.hasOwnProperty(\""+bufferName+"\"))" +
                "    return;" +
                "var x = data[\""+bufferName+"\"][\"data\"][data[\"" + bufferName + "\"][\"data\"].length-1];" +
                "if (valueElement !== document.activeElement)" +
                "   valueElement.value = (x*"+factor+");" +
                "}";
    }
}
