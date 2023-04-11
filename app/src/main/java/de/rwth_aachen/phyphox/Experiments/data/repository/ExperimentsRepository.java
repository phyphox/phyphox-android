package de.rwth_aachen.phyphox.Experiments.data.repository;

import java.util.List;

import de.rwth_aachen.phyphox.Experiments.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;

public interface ExperimentsRepository {

    interface LoadExperimentCallback {
        void onExperimentsLoaded(List<ExperimentDataModel> experimentInfo);
        void onDataNotAvailable();
        void onError();
    }

    void getExperiments(LoadExperimentCallback callback);
}
