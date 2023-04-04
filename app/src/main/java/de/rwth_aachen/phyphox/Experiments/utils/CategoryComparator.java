package de.rwth_aachen.phyphox.Experiments.utils;

import static de.rwth_aachen.phyphox.GlobalConfig.phyphoxCat;

import android.content.Context;

import java.util.Comparator;

import de.rwth_aachen.phyphox.App;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentsInCategory;
import de.rwth_aachen.phyphox.R;

public class CategoryComparator implements Comparator<ExperimentsInCategory> {

    Context context;
    public CategoryComparator(){
        this.context = App.getContext();
    }
    public int compare(ExperimentsInCategory a, ExperimentsInCategory b) {
        if (a.name.equals(context.getString(R.string.categoryRawSensor)))
            return -1;
        if (b.name.equals(context.getString(R.string.categoryRawSensor)))
            return 1;
        if (a.name.equals(context.getString(R.string.save_state_category)))
            return -1;
        if (b.name.equals(context.getString(R.string.save_state_category)))
            return 1;
        if (a.name.equals(phyphoxCat))
            return 1;
        if (b.name.equals(phyphoxCat))
            return -1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    }
}
