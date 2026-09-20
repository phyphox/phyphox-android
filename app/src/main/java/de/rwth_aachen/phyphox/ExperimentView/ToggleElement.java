package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;

public class ToggleElement extends  ExpViewElement implements  Serializable {

    double defaultValue;

    private boolean triggered = false; //Set by user interaction only, see applyDefault()
    private boolean followingBuffer = false;

    SwitchMaterial switchView;

    public ToggleElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
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
        switchView = new SwitchMaterial(c);
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

        switchView.setChecked(bufferValueOrDefault(experiment, defaultValue) != 0.0);

        //setChecked() fires this too; followingBuffer keeps a buffer-driven move from counting as user input
        switchView.setOnCheckedChangeListener((compoundButton, b) -> {
            if (!followingBuffer)
                triggered = true;
        });

        triggered = false; //a freshly built widget is not a user action

        rootView = row;
        rootView.setFocusableInTouchMode(true);

        //Add it to the linear layout
        ll.addView(rootView);

    }

    public void setDefaultValue(double defaultValue){
        this.defaultValue = defaultValue;
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (switchView == null)
            return;

        DataBuffer buffer = experiment.getBuffer(inputs.get(0));
        if (buffer == null || buffer.getFilledSize() == 0)
            return; //an empty buffer is nothing to follow yet, not a zero

        //A switch cannot show NaN; the default stands in until applyDefault() has seeded the buffer
        boolean checked = Double.isNaN(buffer.value) ? defaultValue != 0.0 : (int) buffer.value != 0;
        if (checked != switchView.isChecked() && !triggered) {
            followingBuffer = true;
            switchView.setChecked(checked);
            followingBuffer = false;
        }
    }

    @Override
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        boolean seeded = inputs.size() > 0
                && applyDefault(experiment, inputs.get(0), defaultValue);
        if(!triggered || switchView == null){
            return seeded;
        }
        triggered = false;
        experiment.getBuffer(inputs.get(0)).clear(false);
        double switchValue = switchView.isChecked() ? 1.0 : 0.0;
        experiment.getBuffer(inputs.get(0)).append(switchValue);
        return  true;
    }

    //No clear() on purpose: the switch follows the reseeded buffer, as on iOS

    @Override
    protected String createViewHTML() {

        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"switchElement\" id=\"element"+htmlID+"\">" +
                "<span class=\"label\">"+this.label+"</span>" +
                "</span><input type=\"checkbox\" class=\"value\" id=\"radio"+htmlID+"\" ></input>" +
                "</div>";
    }



    @Override
    public String setDataHTML() {
        String bufferName = inputs.get(0).replace("\"", "\\\"");
        return "function (data) {\n" +
                "                if (!data.hasOwnProperty(\""+bufferName+"\"))\n" +
                "                    return;\n" +
                "\n" +
                "                var x = data[\""+bufferName+"\"][\"data\"][data[\""+bufferName+"\"][\"data\"].length - 1];\n" +
                "                var radioButton = document.getElementById(\"radio"+htmlID+"\");\n" +
                "            \n" +
                "                if (isNaN(x) || x == null || x == 0 || x == 0.0) {\n" +
                "                    radioButton.checked = false;\n" +
                "                } else {\n" +
                "                    radioButton.checked = true;\n" +
                "                }\n" +
                "            \n" +
                "                // Update value when checkbox state changes\n" +
                "                radioButton.onchange = function() {\n" +
                "                    var value = radioButton.checked ? 1.0 : 0.0;\n" +
                "                    ajax('control?cmd=set&buffer="+bufferName+"&value='+value);\n" +
                "                };\n" +
                "            }";
    }

    @Override
    public String getUpdateMode() {
        return "input";
    }
}
