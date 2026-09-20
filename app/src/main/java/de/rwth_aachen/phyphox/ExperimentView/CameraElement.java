package de.rwth_aachen.phyphox.ExperimentView;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentContainerView;

import java.io.Serializable;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.camera.CameraPreviewFragment;
import de.rwth_aachen.phyphox.camera.Scrollable;
import de.rwth_aachen.phyphox.camera.model.CameraSettingLevel;
import de.rwth_aachen.phyphox.camera.model.ShowCameraControls;
import de.rwth_aachen.phyphox.helper.RGB;

public class CameraElement extends ExpViewElement implements  Serializable {

    private CameraElement self;
    private boolean isExclusive = false;
    transient private CameraPreviewFragment cameraPreviewFragment = null;
    float height = 300; //dp, might be settable in the future

    boolean grayscale;
    RGB markOverexposure;
    RGB markUnderexposure;
    ShowCameraControls showCameraControls = ShowCameraControls.FullViewOnly;
    CameraSettingLevel cameraSettingLevel = CameraSettingLevel.ADVANCED;
    String lockedSettings;

    final String warningText;

    Scrollable scrollable = new Scrollable() {
        @Override
        public void enableScrollable() {
            parent.enableScrolling();
            rootView.getParent().requestDisallowInterceptTouchEvent(false);
        }

        @Override
        public void disableScrollable() {
            parent.disableScrolling();
            rootView.getParent().requestDisallowInterceptTouchEvent(true);
        }
    };


    public CameraElement(String label, String visibility, String valueOutput, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutput, inputs, res);
        warningText = res.getString(R.string.remoteCameraPreviewWarning).replace("'", "\\'");
    }

    public void applyControlSettings(ShowCameraControls showCameraControls, int exposureAdjustmentLevel) {
        this.showCameraControls = showCameraControls;
        this.lockedSettings = lockedSettings;
        switch (exposureAdjustmentLevel) {
            case 1: cameraSettingLevel = CameraSettingLevel.BASIC;
                    break;
            case 2: cameraSettingLevel = CameraSettingLevel.INTERMEDIATE;
                    break;
            default: cameraSettingLevel = CameraSettingLevel.ADVANCED;
                    break;
        }
    }

    public void setPreviewParameters(boolean grayscale, RGB markOverexposure, RGB markUnderexposure) {
        this.grayscale = grayscale;
        this.markOverexposure = markOverexposure;
        this.markUnderexposure = markUnderexposure;
    }

    protected boolean toggleExclusive() {
        if (self.parent != null) {
            if (isExclusive) {
                self.requestLeaveExclusive();
            } else {
                self.parent.requestExclusive(self);
            }
            return true;
        }
        return false;
    }

    @Override
    public void createView(LinearLayout ll, Context c, Resources res, ExpViewFragment parent, PhyphoxExperiment experiment) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
           return;

        super.createView(ll, c, res, parent, experiment);
        this.self = this;

        LayoutInflater inflater = LayoutInflater.from(c);
        rootView = inflater.inflate(R.layout.camera_layout, ll, false);
        rootView.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, parent.getResources().getDisplayMetrics());
        ll.addView(rootView);

        rootView.setOnClickListener(view -> toggleExclusive());
        rootView.setFocusableInTouchMode(false);

        FragmentContainerView containerView = rootView.findViewById(R.id.fragmentContainerView);
        if (cameraPreviewFragment == null)
            cameraPreviewFragment = new CameraPreviewFragment(experiment, scrollable, this::toggleExclusive, showCameraControls, cameraSettingLevel, grayscale, markOverexposure, markUnderexposure);

        parent.getChildFragmentManager().beginTransaction().add(containerView.getId(), cameraPreviewFragment).commit();
    }

    @Override
    public void destroyView() {
        if (parent != null && cameraPreviewFragment != null)
            parent.getChildFragmentManager().beginTransaction().remove(cameraPreviewFragment).commit();
        cameraPreviewFragment = null;

    }

    @Override
    //Create the HTML markup. We do not stream the video to the web interface, so this is just a placeholder and notification
    protected String createViewHTML(){
        return "<div style=\"font-size: 105%;\" class=\"cameraElement\" id=\"element" + htmlID + "\"><span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span><div class=\"warningIcon\" onclick=\"alert('"+ warningText + "')\"></div></div>";
    }

    @Override
    public String getUpdateMode() {
        return "none";
    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        super.onFragmentStop(experiment);

    }

    @Override
    public void restore() {
        super.restore();
        if (rootView != null && cameraPreviewFragment != null && parent != null) {
            isExclusive = false;

            rootView.getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, parent.getResources().getDisplayMetrics());
            rootView.requestLayout();

            cameraPreviewFragment.setInteractive(false);
        }
    }

    @Override
    public void maximize() {
        super.maximize();
        if (rootView != null && cameraPreviewFragment != null && parent != null) {
            isExclusive = true;

            rootView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
            rootView.requestLayout();

            cameraPreviewFragment.setInteractive(true);
        }
    }

    @Override
    public void onViewSelected(boolean parentViewIsVisible) {
        if (cameraPreviewFragment != null)
            cameraPreviewFragment.onPageVisibleToUser(parentViewIsVisible);
    }
}
