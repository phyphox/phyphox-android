package de.rwth_aachen.phyphox.ExperimentList.datasource;

import android.widget.LinearLayout;

import java.util.Collections;

import de.rwth_aachen.phyphox.ExperimentList.handler.CategoryComparator;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.R;

public class ExperimentRepository{

    ExperimentListEnvironment environment;
    private final AssetExperimentLoader assetExperimentLoader;

    public ExperimentRepository(ExperimentListEnvironment environment) {
        this.environment = environment;

        assetExperimentLoader = new AssetExperimentLoader(environment, this);
    }

    public AssetExperimentLoader getAssetExperimentLoader(){
        return this.assetExperimentLoader;
    }

    public void loadExperimentList(){

        //Clear the old list first
        assetExperimentLoader.categories.clear();
        assetExperimentLoader.showCurrentCameraAvailability();

        assetExperimentLoader.loadAndAddExperimentFromLocalFile();
        assetExperimentLoader.loadAndAddExperimentFromAsset();

        addExperimentCategoryToParent();

        assetExperimentLoader.loadAndAddExperimentFromHiddenBluetooth();

    }

    public void addExperimentCategoryToParent(){
        Collections.sort(getAssetExperimentLoader().categories, new CategoryComparator(assetExperimentLoader.environment.resources));

        LinearLayout parentLayout = environment.parent.findViewById(R.id.experimentList);
        parentLayout.removeAllViews();

        for (ExperimentsInCategory cat : getAssetExperimentLoader().categories) {
            cat.addToParent(parentLayout);
        }
    }
}


