package de.rwth_aachen.phyphox.Experiments.data.repository;

import java.io.File;
import java.util.List;

import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;

public class ExperimentsRepositoryImpl implements ExperimentsRepository{

    private final ExperimentsDataSource.Asset experimentAsset;

    private static ExperimentsRepositoryImpl instance;

    private ExperimentsRepositoryImpl(ExperimentAssetDataSource experimentAsset ){
        this.experimentAsset = experimentAsset;
    }

    public static ExperimentsRepositoryImpl getInstance(ExperimentAssetDataSource assetDataSource) {
        if(instance == null){
            instance = new ExperimentsRepositoryImpl(assetDataSource);
        }
        return instance;
    }

    @Override
    public void getExperiments(LoadExperimentCallback callback) {
        if(callback == null) return;

        experimentAsset.getExperiments(new LoadExperimentCallback() {
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
