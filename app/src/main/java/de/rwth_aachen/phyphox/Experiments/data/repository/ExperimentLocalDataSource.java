package de.rwth_aachen.phyphox.Experiments.data.repository;

import java.util.List;

import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.Experiments.data.xml.ExperimentDAO;
import de.rwth_aachen.phyphox.Experiments.data.xml.ExperimentInfoXMLParser;

public class ExperimentLocalDataSource implements ExperimentsDataSource.Local{



    private static ExperimentLocalDataSource instance;

    private ExperimentLocalDataSource(){

    }

    public static ExperimentLocalDataSource getInstance(){
        if(instance == null){
            instance = new ExperimentLocalDataSource();
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
