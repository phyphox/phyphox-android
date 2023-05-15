package de.rwth_aachen.phyphox.Experiments.data.model;

import android.graphics.drawable.Drawable;

public class ExperimentDataModel {

    private String category;
    private int color;
    private Drawable icon;
    private String title;
    private String info;
    private String xmlFile;
    private String isTemp;
    private boolean isAsset;
    private Integer unavailableSensor;
    private String isLink;

    public ExperimentDataModel(String category, int color, Drawable icon, String title, String info, String xmlFile, String isTemp, boolean isAsset, Integer unavailableSensor, String isLink){
        this.category = category;
        this.color = color;
        this.icon = icon;
        this.title = title;
        this.info = info;
        this.xmlFile = xmlFile;
        this.isTemp = isTemp;
        this.isAsset = isAsset;
        this.unavailableSensor = unavailableSensor;
        this.isLink = isLink;
    }

    public String getCategory() {
        return category;
    }

    public Drawable getIcon() {
        return icon;
    }

    public int getColor() {
        return color;
    }

    public Integer getUnavailableSensor() {
        return unavailableSensor;
    }

    public String getInfo() {
        return info;
    }

    public String getIsLink() {
        return isLink;
    }

    public String getIsTemp() {
        return isTemp;
    }

    public String getTitle() {
        return title;
    }

    public String getXmlFile() {
        return xmlFile;
    }

    public boolean isAsset() {
        return isAsset;
    }
}
