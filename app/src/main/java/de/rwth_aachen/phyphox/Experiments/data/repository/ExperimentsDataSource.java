package de.rwth_aachen.phyphox.Experiments.data.repository;

public interface ExperimentsDataSource {

    interface Local {
        void getExperiments(ExperimentsRepository.LoadExperimentCallback callback);

    }
}
