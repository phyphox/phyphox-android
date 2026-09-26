package de.rwth_aachen.phyphox.ExperimentView;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.camera.depth.DepthPreview;
import de.rwth_aachen.phyphox.helper.Helper;

//depthGUI implements a camera preview and interface to customize the data acquisition of the
// depth sensor (LiDAR/ToF)
public class DepthGuiElement extends ExpViewElement implements Serializable {
    private final DepthGuiElement self;
    transient private DepthPreview cv = null;
    transient ImageView collapseImage = null;
    transient ImageView expandImage = null;
    transient Spinner modeControl = null;
    transient Spinner cameraSelection = null;
    TextInputLayout textInputCameraSelection;

    private double aspectRatio;

    private boolean isExclusive = false;
    private int margin, elMargin;
    final String warningText;

    //Quite usual constructor...
    public DepthGuiElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
        this.self = this;

        margin = res.getDimensionPixelSize(R.dimen.graph_label_start_margin);
        elMargin = res.getDimensionPixelSize(R.dimen.expElementMargin);

        aspectRatio = 2.5;

        warningText = res.getString(R.string.remoteDepthGUIWarning).replace("'", "\\'");
    }

    //Interface to change the height of the graph
    public void setAspectRatio(double aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    @Override
    public String getUpdateMode() {
         return "none";
    }

    @Override
    //Create the actual view in Android
    public void createView(LinearLayout ll, Context c, Resources res, final ExpViewFragment parent, PhyphoxExperiment experiment){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;

        super.createView(ll, c, res, parent, experiment);

        Context ctx = c;
        Activity act = null;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                act = (Activity) ctx;
            }
            ctx = ((ContextWrapper)ctx).getBaseContext();
        }

        //Create a row consisting of label and value
        LinearLayout layout = new LinearLayout(c);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        RelativeLayout titleLine = new RelativeLayout(c);
        titleLine.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (!hasLabel())
            titleLine.setVisibility(GONE); //file format 1.21: without a label there is no title row, the preview takes the space
        layout.addView(titleLine);

        expandImage = new ImageView(c);
        expandImage.setId(ViewCompat.generateViewId());
        expandImage.setImageResource(R.drawable.ic_expand_arrow);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(elMargin, elMargin, elMargin, elMargin);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        expandImage.setLayoutParams(lp);
        titleLine.addView(expandImage);

        collapseImage = new ImageView(c);
        collapseImage.setImageResource(R.drawable.ic_collapse_arrow);
        collapseImage.setLayoutParams(lp);
        collapseImage.setVisibility(INVISIBLE);
        titleLine.addView(collapseImage);

        //Create the label as textView
        TextView labelView = new TextView(c);
        lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, 0, 0, 0);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.RIGHT_OF, expandImage.getId());
        labelView.setLayoutParams(lp);
        labelView.setText(this.label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
        titleLine.addView(labelView);

        //Create the preview view
        cv = new DepthPreview(c);
        cv.attachDepthInput(experiment.depthInput);
        cv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        cv.setAspectRatio(aspectRatio);
        layout.addView(cv);

        //Mode Controls
        modeControl = new Spinner(c, Spinner.MODE_DIALOG);
        modeControl.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        modeControl.setVisibility(GONE);
        class ModeItem {
            public final DepthInput.DepthExtractionMode key;
            public final String value;
            ModeItem(DepthInput.DepthExtractionMode key, String value) {
                this.key = key;
                this.value = value;
            }
            @Override
            public String toString() {
                return value;
            }
        }
        ArrayList<ModeItem> modeOptions = new ArrayList<>();
        int selection = 0;
        for (DepthInput.DepthExtractionMode mode : DepthInput.DepthExtractionMode.values()) {
            int id = parent.getResources().getIdentifier("depthAggregationMode" + mode.name().substring(0, 1).toUpperCase() + mode.name().substring(1), "string", act.getPackageName());
            modeOptions.add(new ModeItem(mode, res.getString(id)));
        }
        modeControl.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, modeOptions));
        for (int i = 0; i < modeOptions.size(); i++) {
            if (modeOptions.get(i).key == experiment.depthInput.getExtractionMode())
                modeControl.setSelection(i);
        }
        modeControl.setPromptId(R.string.depthAggregationMode);
        layout.addView(modeControl);
        modeControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                cv.setExtractionMode(modeOptions.get(position).key);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        textInputCameraSelection = new TextInputLayout(
                new ContextThemeWrapper(c, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox_ExposedDropdownMenu)
        );

        textInputCameraSelection.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        textInputCameraSelection.setGravity(Gravity.CENTER | Gravity.CENTER_VERTICAL);

        MaterialAutoCompleteTextView autoCompleteTvCameraSelection = new MaterialAutoCompleteTextView(c);
        autoCompleteTvCameraSelection.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        autoCompleteTvCameraSelection.setInputType(InputType.TYPE_NULL);
        autoCompleteTvCameraSelection.setGravity(Gravity.CENTER);

        textInputCameraSelection.addView(autoCompleteTvCameraSelection);
        layout.addView(textInputCameraSelection);

        textInputCameraSelection.setVisibility(GONE);
        class CameraItem {
            public final String key, value;
            CameraItem(String key, String value) {
                this.key = key;
                this.value = value;
            }
            @Override
            public String toString() {
                return value;
            }
        }
        ArrayList<CameraItem> camOptions = new ArrayList<>();
        String backCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_BACK);
        if (backCam != null)
            camOptions.add(new CameraItem(backCam, res.getString(R.string.cameraBackFacing)));
        String frontCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_FRONT);
        if (frontCam != null)
            camOptions.add(new CameraItem(frontCam, res.getString(R.string.cameraFrontFacing)));
        String extCam = DepthInput.findCamera(CameraCharacteristics.LENS_FACING_EXTERNAL);
        if (extCam != null)
            camOptions.add(new CameraItem(extCam, res.getString(R.string.cameraExternal)));

        String[] options = new String[camOptions.size()];
        for (int i = 0; i < camOptions.size(); i++) {
            options[i] = camOptions.get(i).value;
        }

        autoCompleteTvCameraSelection.setSimpleItems(options);

        for (int i = 0; i < camOptions.size(); i++) {
            if (camOptions.get(i).key.equals(experiment.depthInput.getCurrentCameraId()))
               autoCompleteTvCameraSelection.setSelection(i);
        }

        autoCompleteTvCameraSelection.setDropDownBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(c, Helper.isDarkTheme(res) ?
                R.color.phyphox_black_50 :
                R.color.phyphox_white_100)));


        autoCompleteTvCameraSelection.setText(options[0], false);

        autoCompleteTvCameraSelection.setOnItemClickListener((adapterView, view, i, l) -> cv.setCamera(camOptions.get(i).key));

        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (self.parent != null) {
                    if (isExclusive) {
                        self.requestLeaveExclusive();
                    } else {
                        cv.requestFocus();
                        self.parent.requestExclusive(self);
                    }
                }
            }
        });

        rootView = layout;
        rootView.setFocusableInTouchMode(false);
        ll.addView(rootView);

    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        super.onFragmentStop(experiment);
        cv.stop();
        cv = null;
    }

    @Override
    //Create the HTML markup. We do not stream the video to the web interface, so this is just a placeholder and notification
    protected String createViewHTML(){
        return "<div style=\"font-size: 105%;\" class=\"graphElement\" id=\"element" + htmlID + "\">" + (hasLabel() ? "<span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span>" : "") + "<div class=\"warningIcon\" onclick=\"alert('"+ warningText + "')\"></div></div>";
    }

    @Override
    public void restore() {
        super.restore();
        if (rootView != null && cv != null && parent != null) {
            isExclusive = false;

            if (expandImage != null && collapseImage != null) {
                expandImage.setVisibility(VISIBLE);
                collapseImage.setVisibility(INVISIBLE);
            }
            if (modeControl != null)
                modeControl.setVisibility(GONE);
            if (cameraSelection != null)
                cameraSelection.setVisibility(GONE);
            if(textInputCameraSelection != null)
                textInputCameraSelection.setVisibility(GONE);

            rootView.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
            rootView.requestLayout();

            cv.setInteractive(false);
        }
    }

    @Override
    public void maximize() {
        super.maximize();
        if (rootView != null && cv != null && parent != null) {
            isExclusive = true;

            if (expandImage != null && collapseImage != null) {
                expandImage.setVisibility(INVISIBLE);
                collapseImage.setVisibility(VISIBLE);
            }
            if (modeControl != null)
                modeControl.setVisibility(VISIBLE);
            if (cameraSelection != null)
                cameraSelection.setVisibility(VISIBLE);
            if(textInputCameraSelection != null)
                textInputCameraSelection.setVisibility(VISIBLE);

            rootView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
            rootView.requestLayout();

            cv.setInteractive(true);
        }
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
    }
}
