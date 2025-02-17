package de.rwth_aachen.phyphox.ExperimentList.model;

import java.io.InputStream;

public class ExperimentLoadInfoData {
    public InputStream input;
    public String experimentXML;
    public String isTemp;
    public boolean isAsset;

    public ExperimentLoadInfoData(InputStream input, String experimentXML, String isTemp, boolean isAsset) {
        this.input = input;
        this.experimentXML = experimentXML;
        this.isTemp = isTemp;
        this.isAsset = isAsset;
    }
}
