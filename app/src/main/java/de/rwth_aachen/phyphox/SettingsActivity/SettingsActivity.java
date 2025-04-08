package de.rwth_aachen.phyphox.SettingsActivity;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;


public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SettingsFragment settingsFragment = new SettingsFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.settingsFrame, settingsFragment).commit();

        Toolbar toolbar = (Toolbar) findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowTitleEnabled(true);
        }

        final Map<Helper.InsetUtils.AppViewElement, View> appViewElements = new HashMap<>();
        appViewElements.put(Helper.InsetUtils.AppViewElement.HEADER, findViewById(R.id.settingsToolbar));
        appViewElements.put(Helper.InsetUtils.AppViewElement.BODY, findViewById(R.id.settingsFrame));

        Helper.InsetUtils.setWindowInsetListenerForSystemBar(appViewElements);
    }
}
