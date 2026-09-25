package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.RGB;

//InfoElement implements a simple static text display, which gives additional info to the user
public class InfoElement extends ExpViewElement implements Serializable {

    private RGB color;
    private int gravity = Gravity.START;
    private int typeface = Typeface.NORMAL;
    private float size = 1.0f;

    //Constructor takes the same arguments as the ExpViewElement constructor
    public InfoElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
        this.color = new RGB(res.getColor(R.color.phyphox_white_100));
    }

    public void setColor(RGB c) {
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
    public String getUpdateMode() {
        return "none";
    }

    @Override
    //Append the Android views we need to the linear layout
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment){
        super.createView(ll, c, res, parent, experiment);

        //Create the text as textView
        TextView textView = new TextView(c);
        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
//            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, context.getDimension(R.dimen.info_element_margin), context.getDisplayMetrics());
//            lllp.setMargins(0, margin, 0, margin);
        textView.setLayoutParams(lllp);
        textView.setText(this.label);
        textView.setGravity(gravity);
        textView.setTypeface(null, typeface);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.info_element_font) * size);

        textView.setTextColor(color.autoLightColor(res).intColor());

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
        String c = color.hexString(); //rrggbb, or rrggbbaa with an alpha byte
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

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
    }

}
