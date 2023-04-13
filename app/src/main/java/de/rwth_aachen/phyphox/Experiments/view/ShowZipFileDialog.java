package de.rwth_aachen.phyphox.Experiments.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import de.rwth_aachen.phyphox.R;

public class ShowZipFileDialog extends AlertDialog {

    private Context context;
    private View view;

    protected ShowZipFileDialog(@NonNull Context context) {
        super(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    }

    @Override
    public void setView(View view) {
        this.view = view;
    }


}
