package de.rwth_aachen.phyphox.Experiments;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepository;

public class ExperimentViewModelFactory  implements ViewModelProvider.Factory {

    private final ExperimentsRepository experimentsRepository;
    private Application application;

    public ExperimentViewModelFactory(Application application, ExperimentsRepository experimentsRepository){
        this.application = application;
        this.experimentsRepository = experimentsRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ExperimentListViewModel.class)) {
            return (T) new ExperimentListViewModel(application, experimentsRepository);
        }

        throw new IllegalArgumentException("Unknown ViewModel class");
    }

}
