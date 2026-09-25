package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.RGB;

//SeparatorElement implements a simple spacing, optionally showing line
public class SeparatorElement extends ExpViewElement implements Serializable {
    private RGB color = new RGB(0);

    private float height = 0.1f;

    //Label is not used
    public SeparatorElement(String valueOutput, String visibility, Vector<String> inputs, Resources res) {
        super("", visibility, valueOutput, inputs, res);
    }

    public void setColor(RGB c) {
        this.color = c;
    }

    public void setHeight(float h) {
        this.height = h;
    }

    @Override
    //This does not display anything. Do not update.
    public String getUpdateMode() {
        return "none";
    }

    @Override
    //Append the Android views we need to the linear layout
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
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
        String c = color.hexString(); //rrggbb, or rrggbbaa with an alpha byte
        return "<div style=\"font-size:"+this.labelSize/.4+"%;background: #"+c+";height: "+height+"em\" class=\"separatorElement adjustableColor\" id=\"element"+htmlID+"\">" +
                "</div>";
    }

}
