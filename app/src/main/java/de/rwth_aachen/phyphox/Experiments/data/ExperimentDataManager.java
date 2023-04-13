package de.rwth_aachen.phyphox.Experiments.data;

import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentAssetDataSource;
import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepository;
import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepositoryImpl;

public class ExperimentDataManager {

    private static ExperimentDataManager sInstance;

    private ExperimentDataManager(){

    }

    public static synchronized  ExperimentDataManager getInstance(){
        if(sInstance == null){
            sInstance = new ExperimentDataManager();
        }
        return sInstance;
    }

    public ExperimentsRepository getExperimentRepository(){

        ExperimentAssetDataSource assetDataSource = ExperimentAssetDataSource.getInstance();

        return ExperimentsRepositoryImpl.getInstance(assetDataSource);
    }
}
