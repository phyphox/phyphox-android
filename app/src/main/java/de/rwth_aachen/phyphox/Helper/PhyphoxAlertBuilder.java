package de.rwth_aachen.phyphox.Helper;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import java.util.ArrayList;

import de.rwth_aachen.phyphox.R;

public class PhyphoxAlertBuilder {

    private int alertTitleId = 0;
    private int alertMessageId = 0;
    private CharSequence alertMessage;
    private Context context;
    private int viewResId;
    private int themeResId;
    private int[] viewIds;
    private String positiveMessage = "";
    private String negativeMessage = "";
    private DialogInterface.OnClickListener positiveListener;
    private DialogInterface.OnClickListener negativeListener;
    private int viewIndex = 0;

    private int checkBoxId = 0;
    private CheckBox checkBox;
    private int textViewId = 0;
    private TextView textView;

    public PhyphoxAlertBuilder(Context context, int viewResId, int themeResId){
        this.context = context;
        this.viewResId = viewResId;
        this.themeResId = themeResId;

    }

    public PhyphoxAlertBuilder(Context context, int themeResId){
        this.context = context;
        this.themeResId = themeResId;
    }

    public PhyphoxAlertBuilder(){}

    public PhyphoxAlertBuilder setTitle(int titleId) {
        this.alertTitleId = titleId;
        return this;
    }

    public PhyphoxAlertBuilder setMessage(int messageId){
        this.alertMessageId = messageId;
        return this;
    }

    public PhyphoxAlertBuilder setMessage(CharSequence message){
        this.alertMessage = message;
        return this;
    }

    public PhyphoxAlertBuilder addView(int viewId){
        this.viewIds[viewIndex] = viewId;
        viewIndex++;
        return this;
    }

    public PhyphoxAlertBuilder addTextView(int viewId){
        this.textViewId = viewId;
        return this;
    }

    public PhyphoxAlertBuilder addCheckBox(int viewId){
        this.textViewId = viewId;
        return this;
    }

    public PhyphoxAlertBuilder addPositiveWithTitle(String title, DialogInterface.OnClickListener positiveListener){
        this.positiveMessage = title;
        this.positiveListener = positiveListener;
        return this;
    }

    public PhyphoxAlertBuilder addNegativeWithTitle(String title, DialogInterface.OnClickListener negativeListener){
        this.negativeMessage = title;
        this.negativeListener = negativeListener;
        return this;
    }

    public AlertDialog.Builder build() {
        ContextThemeWrapper ctw = new ContextThemeWrapper( context, themeResId);
        AlertDialog.Builder adb = new AlertDialog.Builder(ctw);
        LayoutInflater adbInflater = (LayoutInflater) ctw.getSystemService(LAYOUT_INFLATER_SERVICE);
        View inflatedLayout = adbInflater.inflate(viewResId, null);


        if(alertTitleId != 0){
            adb.setTitle(alertTitleId);
        }
        if(alertMessageId != 0){
            adb.setMessage(alertMessageId);
        }
        if(alertMessage != null){
            adb.setMessage(alertMessage);
        }
        if(positiveListener != null){
            adb.setPositiveButton(positiveMessage, positiveListener);
        }
        if(negativeListener != null){
            adb.setNegativeButton(negativeMessage, negativeListener);
        }
        if(checkBoxId != 0){
            checkBox = (CheckBox) inflatedLayout.findViewById(checkBoxId);
            adb.setView(checkBox);
        }
        if(textViewId != 0){
            textView = (TextView) inflatedLayout.findViewById(textViewId);
            adb.setView(textView);
        }
        return adb;
    }

    //Use it only after building the AlertBuilder
    public boolean getCheckBoxState() {
        if(checkBox != null)
            return checkBox.isChecked();
        else
            return false;
    }

    public TextView getTextView(){
        return textView;
    }

}
