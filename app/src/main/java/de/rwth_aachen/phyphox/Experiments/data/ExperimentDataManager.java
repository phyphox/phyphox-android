package de.rwth_aachen.phyphox.Experiments.data;

import android.content.Context;

import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentLocalDataSource;
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

        ExperimentLocalDataSource localDataSource = ExperimentLocalDataSource.getInstance();

        return ExperimentsRepositoryImpl.getInstance(localDataSource);
    }
}
