package de.rwth_aachen.phyphox.BaseActivity;

import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.viewbinding.ViewBinding;

import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.SettingsFragment;

public abstract class BaseActivity<BINDING extends ViewBinding, VM extends  BaseViewModel> extends AppCompatActivity {

    protected VM viewModel;
    protected BINDING binding;

    @NonNull
    protected abstract VM createViewModel();

    @NonNull
    protected abstract BINDING createViewBinding(LayoutInflater layoutInflater);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = createViewBinding(LayoutInflater.from(this));
        setContentView(binding.getRoot());
        viewModel = createViewModel();

        setTheme(R.style.Theme_Phyphox_DayNight);
        String themePreference = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(getString(R.string.setting_dark_mode_key), SettingsFragment.DARK_MODE_ON);
        SettingsFragment.setApplicationTheme(themePreference);
    }
}
