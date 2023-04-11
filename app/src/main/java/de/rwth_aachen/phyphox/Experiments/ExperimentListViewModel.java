package de.rwth_aachen.phyphox.Experiments;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.BaseActivity.BaseViewModel;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepository;

public class ExperimentListViewModel extends BaseViewModel {


    private MutableLiveData<List<ExperimentDataModel>> liveDataExperimentInfo = new MutableLiveData<>();
    private List<ExperimentsInCategory> categories = new ArrayList<ExperimentsInCategory>();


    private final ExperimentsRepository experimentsRepository;

    private final ExperimentsCallBack experimentsCallBack = new ExperimentsCallBack();


    ExperimentListViewModel(Application application, ExperimentsRepository experimentsRepository){
        super(application);
        this.experimentsRepository = experimentsRepository;
    }

    public void loadExperiments(){
        experimentsRepository.getExperiments(experimentsCallBack);
    }

    public void setExperimentsLiveData(List<ExperimentDataModel> experimentInfo){
        this.liveDataExperimentInfo.postValue(experimentInfo);

    }

    private class ExperimentsCallBack implements  ExperimentsRepository.LoadExperimentCallback {

        @Override
        public void onExperimentsLoaded(List<ExperimentDataModel> experimentInfo) {
            setExperimentsLiveData(experimentInfo);
        }

        @Override
        public void onDataNotAvailable() {

        }

        @Override
        public void onError() {

        }
    }

    public LiveData<List<ExperimentDataModel>> getExperimentsLiveData(){
        return this.liveDataExperimentInfo;
    }

    public List<ExperimentsInCategory> getCategories(){
        return this.categories;
    }
}
