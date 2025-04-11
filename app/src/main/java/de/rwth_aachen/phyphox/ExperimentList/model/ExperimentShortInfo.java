package de.rwth_aachen.phyphox.ExperimentList.model;

import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.Helper.RGB;

public class ExperimentShortInfo {
    public RGB color;
    public Drawable icon;
    public String title;
    public String description;
    public String fullDescription;
    public Set<String> resources;
    public String xmlFile;
    public String isTemp;
    public boolean isAsset;
    public int unavailableSensor;
    public String isLink;
    public Map<String, String> links;
    public String categoryName;

    public final Set<String> bluetoothDeviceNames = new HashSet<>();
    public final Set<UUID> bluetoothDeviceUUIDs = new HashSet<>();
}
