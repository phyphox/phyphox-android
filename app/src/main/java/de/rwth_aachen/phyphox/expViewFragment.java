package de.rwth_aachen.phyphox;


import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

public class expViewFragment extends Fragment {
    private static final String ARG_INDEX = "index";

    private int index;
    private View root;

    public expViewFragment() {
        // Required empty public constructor
    }

    public static expViewFragment newInstance(int index) {
        expViewFragment fragment = new expViewFragment();
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

    public void recreateView() {
        if (root == null)
            return;
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.removeAllViews();

        if (((Experiment)getActivity()).experiment != null && ((Experiment)getActivity()).experiment.experimentViews.size() > index) {
            for (expView.expViewElement element : ((Experiment) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.createView(ll, getContext(), getResources());
            }
        }

        if (((Experiment)getActivity()).experiment != null)
            ((Experiment) getActivity()).experiment.updateViews(index, true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser) {
            if (getActivity() != null && ((Experiment)getActivity()).experiment != null)
                ((Experiment) getActivity()).experiment.updateViews(index, true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        root = inflater.inflate(R.layout.fragment_exp_view, container, false);

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
    public void onStart() {
        recreateView();
        super.onStart();
    }

    @Override
    public void onStop() {
        if (root == null)
            return;
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.experimentView);
        ll.removeAllViews();

        if (((Experiment)getActivity()).experiment != null && ((Experiment)getActivity()).experiment.experimentViews.size() > index) {
            for (expView.expViewElement element : ((Experiment) getActivity()).experiment.experimentViews.elementAt(index).elements) {
                element.cleanView();
            }
        }

        super.onStop();
    }

}
