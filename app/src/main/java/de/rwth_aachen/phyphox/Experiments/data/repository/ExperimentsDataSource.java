package de.rwth_aachen.phyphox.Experiments.data.repository;

public interface ExperimentsDataSource {

    interface Asset {
        void getExperiments(ExperimentsRepository.LoadExperimentCallback callback);

    }

}
