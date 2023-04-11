package de.rwth_aachen.phyphox.Experiments.data.repository;

import java.util.List;

import de.rwth_aachen.phyphox.Experiments.ExperimentsInCategory;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;

public class ExperimentsRepositoryImpl implements ExperimentsRepository{

    private final ExperimentsDataSource.Local experimentLocal;

    private static ExperimentsRepositoryImpl instance;

    private ExperimentsRepositoryImpl(ExperimentLocalDataSource experimentLocal){
        this.experimentLocal = experimentLocal;
    }

    public static ExperimentsRepositoryImpl getInstance(ExperimentLocalDataSource experimentLocal) {
        if(instance == null){
            instance = new ExperimentsRepositoryImpl(experimentLocal);
        }
        return instance;
    }

    @Override
    public void getExperiments(LoadExperimentCallback callback) {
        if(callback == null) return;

        experimentLocal.getExperiments(new LoadExperimentCallback() {
            @Override
            public void onExperimentsLoaded(List<ExperimentDataModel> experimentInfo) {
                callback.onExperimentsLoaded(experimentInfo);
            }

            @Override
            public void onDataNotAvailable() {

            }

            @Override
            public void onError() {

            }
        });

    }
}
