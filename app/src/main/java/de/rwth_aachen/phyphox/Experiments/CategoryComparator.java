package de.rwth_aachen.phyphox.Experiments;

import static de.rwth_aachen.phyphox.GlobalConfig.phyphoxCat;

import android.content.Context;

import java.util.Comparator;

import de.rwth_aachen.phyphox.R;

class CategoryComparator implements Comparator<ExperimentsInCategory> {

    Context context;
    CategoryComparator(Context context){
        this.context = context;
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
