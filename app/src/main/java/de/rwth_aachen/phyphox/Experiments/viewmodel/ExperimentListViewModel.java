package de.rwth_aachen.phyphox.Experiments.viewmodel;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.App;
import de.rwth_aachen.phyphox.BaseActivity.BaseViewModel;
import de.rwth_aachen.phyphox.Experiments.data.model.ExperimentDataModel;
import de.rwth_aachen.phyphox.Experiments.data.repository.ExperimentsRepository;
import de.rwth_aachen.phyphox.Experiments.data.xml.ExperimentInfoXMLParser;
import de.rwth_aachen.phyphox.Experiments.utils.Utility;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentsInCategory;

public class ExperimentListViewModel extends BaseViewModel {


    private final MutableLiveData<List<ExperimentDataModel>> liveDataExperimentInfo = new MutableLiveData<>();

    private final List<ExperimentsInCategory> categories = new ArrayList<>();

    private final MutableLiveData<String> zipExtractResult = new MutableLiveData<>();

    private final ExperimentsRepository experimentsRepository;

    private final ExperimentsCallBack experimentsCallBack = new ExperimentsCallBack();


    ExperimentListViewModel(Application application, ExperimentsRepository experimentsRepository){
        super(application);
        this.experimentsRepository = experimentsRepository;
    }

    //For experiment from Assets
    public void loadExperiments(){
        experimentsRepository.getExperiments(experimentsCallBack);
    }
    public void setExperimentsLiveData(List<ExperimentDataModel> experimentInfo){
        this.liveDataExperimentInfo.postValue(experimentInfo);
    }
    public LiveData<List<ExperimentDataModel>> getExperimentsLiveData(){
        return this.liveDataExperimentInfo;
    }


    //For experiment from Zip
    public List<ExperimentDataModel> loadExperimentOnZipRead(File[] files){
        List<ExperimentDataModel> experimentZipData = new ArrayList<>();
        //Load experiments from local files
        for (File file : files) {
            //Load details for each experiment
            try {
                InputStream input = new FileInputStream(file);
                ExperimentInfoXMLParser parser =  new ExperimentInfoXMLParser(input, file.getName(), "temp_zip",false, null, null, null);
                experimentZipData.add(parser.getExperimentDataModel());
                input.close();
            } catch (IOException e) {
                Log.e("zip", e.getMessage());
                Toast.makeText(App.getContext(), "Error: Could not load experiment \"" + file + "\" from zip file.", Toast.LENGTH_LONG).show();
            }
        }

        return experimentZipData;
    }

    public  void setZipExtractResult(String result){
        this.zipExtractResult.postValue(result);
    }
    public LiveData<String> getZipExtractedResult() {
        return this.zipExtractResult;
    }


    public List<ExperimentsInCategory> getCategories(){
        return this.categories;
    }

    public void addCategories(List<ExperimentsInCategory> categories){
        this.categories.addAll(categories);
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



}
