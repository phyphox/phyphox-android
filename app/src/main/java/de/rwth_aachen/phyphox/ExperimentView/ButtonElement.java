package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.Bluetooth.BluetoothOutput;
import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.DataInput;
import de.rwth_aachen.phyphox.DataOutput;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkConnection;
import de.rwth_aachen.phyphox.NetworkConnection.NetworkService;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;

//ButtonElement implements a simple button which writes values from inputs to outputs when triggered
public class ButtonElement extends ExpViewElement implements Serializable, NetworkService.RequestCallback {
    private Vector<DataInput> inputs = null;
    private Vector<DataOutput> outputs = null;
    private Vector<String> triggers = null;
    private List<NetworkConnection> networkConnections = null;
    private Vector<BluetoothOutput> bluetoothOutputs = null;
    private boolean triggered = false;
    private DataBuffer dynamicBuffer;
    MaterialButton b;

    public class ButtonMapping {
        public Double min = Double.NEGATIVE_INFINITY;
        public Double max = Double.POSITIVE_INFINITY;
        public String str;

        public ButtonMapping(String str) {
            this.str = str;
        }
    }

    protected Vector<ButtonMapping> mappings = new Vector<>();

    public void addMapping(ButtonMapping mapping) {
        this.mappings.add(mapping);
    }

    //No special constructor.
    public ButtonElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
    }

    public void setIO(Vector<DataInput> inputs, Vector<DataOutput> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public void setTriggers(Vector<String> triggers) {
        this.triggers = triggers;
    }

    public void setDynamicBuffer(DataBuffer dynamicBuffer) {
        this.dynamicBuffer = dynamicBuffer;
    }

    @Override
    //This is not automatically updated, but triggered by the user, so it's "none"
    public String getUpdateMode() {
        if(dynamicBuffer == null){
            return "none";
        }
        return "single";
    }

    @Override
    //Create the view in Android and append it to the linear layout
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
        super.createView(ll, c, res, parent, experiment);

        networkConnections = experiment.networkConnections;
        bluetoothOutputs = experiment.bluetoothOutputs;

        b = new MaterialButton(c);

        LinearLayout.LayoutParams vglp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vglp.gravity = Gravity.CENTER;

        b.setBackgroundColor(c.getResources().getColor(R.color.phyphox_white_70));
        b.setTextColor(c.getResources().getColor(R.color.phyphox_black_80));
        b.setLayoutParams(vglp);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
        b.setText(this.label);

        if(dynamicBuffer != null){
            // Register the buffer of the dynamic label which is defined as string in xml
            experiment.getBuffer(dynamicBuffer.name).register(this);
        }

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
    public void trigger() {
        triggered = true;
        for (String t : triggers) {
            for (NetworkConnection nc : networkConnections) {
                if (nc.id.equals(t)) {
                    List<NetworkService.RequestCallback> requestCallbacks = new ArrayList<>();
                    requestCallbacks.add(this);
                    nc.execute(requestCallbacks);
                    ((MaterialButton)rootView).setEnabled(false);
                    ((MaterialButton)rootView).setAlpha(0.5f);
                }
            }
            for (BluetoothOutput btOut : bluetoothOutputs) {
                btOut.requestSend(t);
            }
        }
    }

    public void requestFinished(NetworkService.ServiceResult result) {
        if (parent == null || parent.getActivity() == null)
            return;
        parent.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((MaterialButton)rootView).setEnabled(true);
                ((MaterialButton)rootView).setAlpha(1f);
            }
        });
    }

    @Override
    //If triggered, write the data to the output buffers
    //Always return zero as the analysis process does not receive the values directly
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
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
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (!needsUpdate)
            return;
        needsUpdate = false;

        if(dynamicBuffer == null){
            return;
        }
        double x = dynamicBuffer.value;

        String buttonLabel = this.label;

        if (Double.isNaN(x)) {
            buttonLabel = this.label;
        } else {
            for (ButtonMapping map : mappings)  {
                if (x >= map.min && x <= map.max) {
                    buttonLabel = map.str;
                    break;
                }
            }
        }
        b.setText(buttonLabel);
    }

    @Override
    //Create the HTML markup for this element
    //<div>
    //  <span>Label</span> <input /> <span>unit</span>
    //</div>
    //Note that the input is send from here as well as the AJAX-request is placed in the
    //onchange-listener in the markup
    protected String createViewHTML(){

        if(dynamicBuffer == null){
            return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"buttonElement\" id=\"element"+htmlID+"\">" +
                    "<button onclick=\"ajax('control?cmd=trigger&element="+htmlID+"');\">" + this.label +"</button>" +
                    "</div>";
        }

        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"buttonElement\" id=\"element"+htmlID+"\">" +
                "<button class=\"valueNumber\" id=\"button"+htmlID+"\" onclick=\"ajax('control?cmd=trigger&element="+htmlID+"');\" " +
                "onchange=\"ajax('control?cmd=set&buffer="+valueOutput+"&value='+this.value)\">" + this.label +"</button>" +
                "</div>";
    }

    @Override
    public String setDataHTML() {
        if(dynamicBuffer == null){
            return "function() {}";
        }

        StringBuilder sb = new StringBuilder();

        String bufferName = super.inputs.get(0).replace("\"", "\\\"");

        sb.append("function (data) {");

        sb.append("     if (!data.hasOwnProperty(\""+bufferName+"\"))");
        sb.append("         return;");
        sb.append(      "var x = data[\""+bufferName+"\"][\"data\"][data[\"" + bufferName + "\"][\"data\"].length-1];");
        sb.append(      "var v = \""+this.label+"\";");

        sb.append(      "if (isNaN(x) || x == null) { v = \" "+this.label+"\" }");
        for (ButtonMapping map : mappings) {
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

        sb.append("     var valueNumber = document.getElementById(\"button"+htmlID+"\");");
        sb.append("     valueNumber.textContent = v;");
        sb.append("}");
        return sb.toString();
    }
}
