package de.rwth_aachen.phyphox.SettingsActivity;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Bundle;
import android.view.ViewGroup;


import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;


public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
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
        Helper.WindowInsetHelper.setWindowInsets(findViewById(R.id.settingsFrame), this, false, false);
        Helper.WindowInsetHelper.setToolbarWindowInset(findViewById(R.id.settingsToolbar), this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRootView), (v, inset) -> {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.bottomMargin = inset.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            return inset;
        });

    }
}
