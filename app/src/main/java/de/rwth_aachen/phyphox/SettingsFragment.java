package de.rwth_aachen.phyphox;
import android.app.LocaleConfig;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.LocaleList;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        prepareLanguageList();
        updateCurrentLanguage();
    }

    private void updateCurrentLanguage() {
        ListPreference lp = (ListPreference) findPreference("language");
        Locale locale = AppCompatDelegate.getApplicationLocales().get(0);
        if (locale == null)
            lp.setValue("*");
        else
            lp.setValue(locale.toString().replace("_", "-"));
    }

    private void prepareLanguageList() {
        ListPreference lp = (ListPreference) findPreference("language");

        String[] rawValues = BuildConfig.LOCALE_ARRAY;
        List<String> valuesUsed = new ArrayList<>();

        for (int i = 0; i < rawValues.length; i++) {
            if (rawValues[i].contains("+"))
                continue;
            valuesUsed.add(rawValues[i].replace("-r", "-"));
        }
        valuesUsed.add("*");

        int n = valuesUsed.size();
        String[] names = new String[n];
        String[] values = valuesUsed.toArray(new String[n]);
        Arrays.sort(values, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
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
            }
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

        lp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue.toString().equals("*"))
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                else
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()));
                updateCurrentLanguage();
                return true;
            }
        });
    }
}
