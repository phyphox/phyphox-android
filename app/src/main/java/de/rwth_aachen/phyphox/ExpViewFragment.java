package de.rwth_aachen.phyphox;


import android.animation.LayoutTransition;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import de.rwth_aachen.phyphox.ExperimentView.ExpView;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;

public class ExpViewFragment extends Fragment {
    private static final String ARG_INDEX = "index";

    private int index;
    public CustomScrollableView root;
    boolean hasExclusive;
    private Runnable afterLeave = null; //what a held-back tab change does once exclusive mode is gone

    //This falls under graphElement-> SpectroscopyCalibrationManager

    public ExpViewFragment() {
        // Required empty public constructor
    }

    //Apply zoom to all graphs on the current page.
    public void applyZoom(double min, double max, boolean follow, Unit unit, String buffer, boolean yAxis, boolean absoluteTime) {
        for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).flatElements()) {
            if (element.getClass() == GraphElement.class) {
                GraphElement ge = (GraphElement)element;
                ge.applyZoom(min, max, follow, unit, buffer, yAxis, absoluteTime);
            }
        }
    }


    public void disableScrolling(){
        root.setScrollEnabled(false);
    }

    public void enableScrolling(){
        root.setScrollEnabled(true);
    }

    public boolean hasExclusive() {
        return hasExclusive;
    }

    private void notifyExclusiveChanged() {
        if (getActivity() instanceof ExperimentActivity)
            ((ExperimentActivity) getActivity()).updateBackCallbackState();
    }

    public void requestExclusive(ExpViewElement caller) {
        if (root == null)
            return;
        hasExclusive = true;
        notifyExclusiveChanged();
        root.setFillViewport(true);
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(150);
        layoutTransition.setStartDelay(LayoutTransition.DISAPPEARING, 0);
        layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setStartDelay(LayoutTransition.CHANGING, 0);
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.setLayoutTransition(layoutTransition);
        //The top-level element holding the caller stretches to the full height (groups pass this down to the leaf), all others hide
        for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
            if (element.contains(caller)) {
                element.maximizePath(caller);
            } else {
                element.hide();
            }
        }
        ll.setLayoutTransition(null);
    }

    //Leave exclusive mode (e.g. via back), letting the maximized element intercept (a graph asks how to apply a temporary zoom)
    public void requestLeaveExclusive() {
        if (!hasExclusive)
            return;
        afterLeave = null;
        requestLeaveExclusiveKeepingAction();
    }

    //The same, running the action once exclusive mode is gone; nothing happens if the user cancels
    public void requestLeaveExclusive(Runnable afterLeave) {
        if (!hasExclusive)
            return;
        this.afterLeave = afterLeave;
        requestLeaveExclusiveKeepingAction();
    }

    public void leaveRequestCancelled() {
        afterLeave = null;
    }

    private void requestLeaveExclusiveKeepingAction() {
        if (getActivity() instanceof ExperimentActivity && ((ExperimentActivity) getActivity()).experiment != null && ((ExperimentActivity) getActivity()).experiment.experimentViews.size() > index) {
            for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).flatElements()) {
                if (element.state == ExpView.State.maximized && element.getChildren() == null) { //the leaf, not a group on the path to it
                    element.requestLeaveExclusive();
                    return;
                }
            }
        }
        leaveExclusive();
    }

    public void leaveExclusive() {
        hasExclusive = false;
        notifyExclusiveChanged();
        if (root == null)
            return;
        root.setFillViewport(false);
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(150);
        layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0);
        layoutTransition.setStartDelay(LayoutTransition.CHANGE_APPEARING, 0);
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setStartDelay(LayoutTransition.CHANGING, 0);
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.setLayoutTransition(layoutTransition);
        for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
            element.restore();
        }
        ll.setLayoutTransition(null);
        Runnable action = afterLeave;
        afterLeave = null;
        if (action != null)
            action.run();
    }

    public static ExpViewFragment newInstance(int index) {
        ExpViewFragment fragment = new ExpViewFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX, index);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            index = getArguments().getInt(ARG_INDEX);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void recreateView() {
        if (root == null)
            return;
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        if (((ExperimentActivity)getActivity()).experiment != null && ((ExperimentActivity)getActivity()).experiment.experimentViews.size() > index) {
            for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.destroyView();
            }
        }
        ll.removeAllViews();

        root.setFillViewport(false);
        hasExclusive = false;

        if (((ExperimentActivity)getActivity()).experiment != null && ((ExperimentActivity)getActivity()).experiment.experimentViews.size() > index) {
            for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.createView(ll, getContext(), getResources(), this, ((ExperimentActivity) getActivity()).experiment);
            }
        }

        if (((ExperimentActivity)getActivity()).experiment != null)
            ((ExperimentActivity) getActivity()).experiment.updateViews(index, true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser) {
            if (getActivity() != null && ((ExperimentActivity)getActivity()).experiment != null)
                ((ExperimentActivity) getActivity()).experiment.updateViews(index, true);
        } else if (hasExclusive) {
            leaveExclusive(); //the fragment really goes away; a user's tab change is held back in Experiment.selectPage
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        root = (CustomScrollableView)inflater.inflate(R.layout.fragment_exp_view, container, false);

        final LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(ll.getWindowToken(), 0);
                }
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(getActivity() == null){
            return;
        }
        recreateView();
    }

    public void onPause() {
        super.onPause();
        if(getActivity() == null){
            return;
        }
        if (((ExperimentActivity)getActivity()).experiment != null && ((ExperimentActivity)getActivity()).experiment.experimentViews.size() > index) {
            for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.destroyView();
            }
        }
    }

    @Override
    public void onStop() {
        if (root == null)
            return;
        root.setFillViewport(false);

        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.removeAllViews();

        if (((ExperimentActivity)getActivity()).experiment != null && ((ExperimentActivity)getActivity()).experiment.experimentViews.size() > index) {
            for (ExpViewElement element : ((ExperimentActivity) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.onFragmentStop(((ExperimentActivity) getActivity()).experiment);
            }
        }

        super.onStop();
    }

}
