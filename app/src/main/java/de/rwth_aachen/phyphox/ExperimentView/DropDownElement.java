package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.Helper;
import de.rwth_aachen.phyphox.helper.RGB;

public class DropDownElement extends ExpViewElement implements Serializable {

    double defaultValue;

    private RGB color;

    MaterialAutoCompleteTextView autoCompleteTextView;

    private boolean triggered = false; //Set by user interaction only, see applyDefault()
    private int currentIndex = 0;

    public class Mapping {
        public String value;
        public String str;

        public Mapping(String str) {
            this.str = str;
        }

    }

    protected Vector<DropDownElement.Mapping> mappings = new Vector<>();

    public void addMapping(DropDownElement.Mapping mapping) {
        this.mappings.add(mapping);
    }

    public void setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setColor(RGB c) {
        this.color = c;
    }

    public DropDownElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
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

        //Material Design 3 uses TextInputLayout and AutoCompleteTextView for dropdowns instead of Spinner
        TextInputLayout textInputLayout = new TextInputLayout(
                new ContextThemeWrapper(c, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox_ExposedDropdownMenu)
        );

        textInputLayout.setLayoutParams(new TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.5f)); //right half should be value+unit
        textInputLayout.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        textInputLayout.setPadding((int) labelSize / 2, 0, 0, 0);

        autoCompleteTextView = new MaterialAutoCompleteTextView(c);
        autoCompleteTextView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        autoCompleteTextView.setInputType(InputType.TYPE_NULL);

        textInputLayout.addView(autoCompleteTextView);

        String[] options = new String[mappings.size()];
        int index = 0;
        for(Mapping title: mappings){
            if(title.str.isEmpty()){
                options[index] = title.value;
            } else {
                options[index] = title.str;
            }
            index++;
        }

        autoCompleteTextView.setSimpleItems(options);

        autoCompleteTextView.setDropDownBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(c, Helper.isDarkTheme(res) ?
                                        R.color.phyphox_black_50 :
                                        R.color.phyphox_white_100)));


        autoCompleteTextView.setText(options[0], false);

        setFromValue(bufferValueOrDefault(experiment, defaultValue));

        triggered = false; //a freshly built widget is not a user action

        autoCompleteTextView.setOnItemClickListener((adapterView, view, position, id) -> {
            triggered = true;
            currentIndex = position;
        });

        row.addView(labelView);
        row.addView(textInputLayout);

        rootView = row;
        rootView.setFocusableInTouchMode(true);
        ll.addView(rootView);

    }

    @Override
    public String getUpdateMode() {
        return "input";
    }

    protected double getValue() {
        return  Double.parseDouble(mappings.get(currentIndex).value);
    }

    private void setFromValue(double x) {
        int index= -1;
        for(int i = 0; i < mappings.size(); i++){
            if(Double.parseDouble(mappings.get(i).value) == x){
                index = i;
                break;
            }
        }
        currentIndex = (index == -1)? 0 : index;
        autoCompleteTextView.setText(mappings.get(currentIndex).str, false);
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (!needsUpdate || triggered || autoCompleteTextView == null)
            return;
        DataBuffer buffer = experiment.getBuffer(inputs.get(0));
        if (buffer == null || buffer.getFilledSize() == 0)
            return; //an empty buffer is nothing to follow yet, not a zero
        needsUpdate = false;
        //A menu cannot show NaN; the default stands in until applyDefault() has seeded the buffer
        double x = Double.isNaN(buffer.value) ? defaultValue : buffer.value;

        setFromValue(x);
    }

    @Override
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        boolean seeded = inputs.size() > 0
                && applyDefault(experiment, inputs.get(0), defaultValue);
        if (!triggered || autoCompleteTextView == null)
            return seeded;
        triggered = false;
        experiment.getBuffer(inputs.get(0)).clear(false);
        experiment.getBuffer(inputs.get(0)).append(getValue());
        return true;
    }

    @Override
    protected String createViewHTML() {

        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"dropdownElement\" id=\"element"+htmlID+"\">" +
                "<span class=\"label\">"+this.label+"</span>" +
                "<select onchange=\"ajax('control?cmd=set&buffer="+valueOutput+"&value='+this.value)\" class=\"value\" id=\"select"+htmlID+"\" />" +
                "</div>";

    }



    @Override
    public String setDataHTML() {

        String bufferName = inputs.get(0).replace("\"", "\\\"");

        ArrayList<String> options = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();

        for(Mapping maps: mappings){
            options.add(maps.str);
            values.add(maps.value);
        }

        // Create a string in JSON-like format
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < options.size(); i++) {
            jsonArray.append("\"").append(options.get(i)).append("\"");
            if (i < options.size() - 1) {
                jsonArray.append(", ");
            }
        }
        jsonArray.append("]");


        return "function (data) {\n" +
                "                    if (!data.hasOwnProperty(\""+bufferName+"\"))\n" +
                "                        return;\n" +
                "                    var x = data[\""+bufferName+"\"][\"data\"][data[\""+bufferName+"\"][\"data\"].length - 1];\n" +
                "                    \n" +
                "                    var dropdownElement = document.getElementById(\"select"+htmlID+"\")\n" +
                "            \n" +
                "                    var selectedValue = x\n" +
                "                    dropdownElement.innerHTML = \"\"\n" +
                "            \n" +
                "                    var values = "+values+" \n" +
                "                    var options =  "+ jsonArray +"\n" +
                "                    for (var i = 0; i < options.length ; i++){\n" +
                "                        var option = document.createElement(\"option\")\n" +
                "                        option.value = values[i]\n" +
                "                        if(options[i] == \"\"){\n" +
                "                            option.text = values[i]\n" +
                "                        } else {\n" +
                "                            option.text = options[i]\n" +
                "                        }\n" +
                "                        \n" +
                "                        dropdownElement.appendChild(option)\n" +
                "                    }\n" +
                "            \n" +
                "            \n" +

                "                    if (values.includes(selectedValue)) {\n" +
                "                        dropdownElement.selectedIndex = values.indexOf(selectedValue)\n" +
                "                    } else {\n" +
                "                      dropdownElement.selectedIndex = 0\n" +
                "                    }\n" +
                "            \n" +
                "             \n" +
                "            }";
    }

}
