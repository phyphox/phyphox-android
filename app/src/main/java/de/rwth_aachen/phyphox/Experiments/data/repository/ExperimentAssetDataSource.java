package de.rwth_aachen.phyphox.Experiments.data.repository;

import de.rwth_aachen.phyphox.Experiments.data.xml.ExperimentDAO;

public class ExperimentAssetDataSource implements ExperimentsDataSource.Asset {

    private static ExperimentAssetDataSource instance;

    private ExperimentAssetDataSource(){

    }

    public static ExperimentAssetDataSource getInstance(){
        if(instance == null){
            instance = new ExperimentAssetDataSource();
        }
        return instance;
    }

    @Override
    public void getExperiments(ExperimentsRepository.LoadExperimentCallback callback) {
        // Get data from the XML parser here
        ExperimentDAO experimentDAO = new ExperimentDAO();
        callback.onExperimentsLoaded(experimentDAO.getExperimentsFromAsset());
    }


}
