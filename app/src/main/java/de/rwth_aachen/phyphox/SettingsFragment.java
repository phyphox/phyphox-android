package de.rwth_aachen.phyphox;

import android.app.LocaleConfig;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String GRAPH_SIZE_KEY = "graph_size_dialog";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceManager.setDefaultValues(getContext(),R.xml.settings, false);
        setPreferencesFromResource(R.xml.settings, rootKey);
        setupPortEditText();
        prepareLanguageList();
        updateCurrentLanguage();
        updateGraphView();
    }

    private void setupPortEditText() {
        EditTextPreference editTextPreference = findPreference("remoteAccessPort");
        if (editTextPreference != null) {
            editTextPreference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            editTextPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int v = Integer.parseInt(newValue.toString());
                if ((v >= 1024) && (v < 65536)) {
                    return true;
                } else {
                    Toast.makeText(getContext(), "Allowed range: 1024-65535", Toast.LENGTH_LONG).show();
                    return false;
                }
            });
        }
    }

    private void updateCurrentLanguage() {
        ListPreference lp = findPreference("language");
        if(lp != null){
            Locale locale = AppCompatDelegate.getApplicationLocales().get(0);
            if (locale == null)
                lp.setValue("*");
            else
                lp.setValue(locale.toString().replace("_", "-"));
        }
    }

    // This engine turns the rawValues from locale eg: ["en", "cs","de","el","es"]
    // to the actula name of language like : Czech, Dutch, English, German and so on

    private void prepareLanguageList() {
        ListPreference lp = findPreference("language");

        String[] rawValues = BuildConfig.LOCALE_ARRAY;
        List<String> valuesUsed = new ArrayList<>();

        for (String rawValue : rawValues) {
            if (rawValue.contains("+"))
                continue;
            valuesUsed.add(rawValue.replace("-r", "-"));
        }
        valuesUsed.add("*");

        int n = valuesUsed.size();
        String[] names = new String[n];
        String[] values = valuesUsed.toArray(new String[n]);
        Arrays.sort(values, (lhs, rhs) -> {
            if (lhs.equals("*"))
                return -1;
            if (rhs.equals("*"))
                return +1;
            Locale l1 = LocaleListCompat.forLanguageTags(lhs).get(0);
            Locale l2 = LocaleListCompat.forLanguageTags(rhs).get(0);
            if (l1 == null || l2 == null)
                return 0;
            String s1 = l1.getDisplayName();
            String s2 = l2.getDisplayName();
            return s1.compareTo(s2);
        });

        for (int i = 0; i < n; i++) {
            if (values[i].equals("*")) {
                names[i] = getContext().getResources().getString(R.string.settingsDefault);
                continue;
            }
            Locale locale = LocaleListCompat.forLanguageTags(values[i]).get(0);
            if (locale == null)
                names[i] = values[i];
            else
                names[i] = locale.getDisplayName();
        }

        lp.setEntries(names);
        lp.setEntryValues(values);

        lp.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.toString().equals("*"))
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
            else
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()));
            updateCurrentLanguage();
            return true;
        });
    }


    private void updateGraphView(){
        ListPreference lp = findPreference(GRAPH_SIZE_KEY);
        assert lp != null;
        lp.setOnPreferenceChangeListener((preference, newValue) -> {
            lp.setValue(newValue.toString());
            return true;
        });
    }

}
