package de.rwth_aachen.phyphox.Helper;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//A text watcher to allow local decimal points (i.e. comma) in edit fields
public class DecimalTextWatcher implements TextWatcher {

    List<Character> separators = new ArrayList<>(Arrays.asList('.', ','));
    Character locale_separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (editable == null)
            return;
        String str = editable.toString();
        StringBuilder sb = new StringBuilder();
        boolean foundOne = false;
        boolean modified = false;
        for (int i = 0; i < str.length(); i++) {
            if (separators.contains(str.charAt(i))) {
                if (!foundOne) {
                    foundOne = true;
                    if (str.charAt(i) != locale_separator)
                        modified = true;
                    sb.append(locale_separator);
                } else
                    modified = true;
            } else {
                sb.append(str.charAt(i));
            }
        }
        if (modified)
            editable.replace(0, editable.length(), sb.toString());
    }
}
