package de.rwth_aachen.phyphox.ExperimentList.handler;

import static de.rwth_aachen.phyphox.ExperimentList.model.Const.phyphoxCat;

import android.content.res.Resources;

import java.util.Comparator;

import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;
import de.rwth_aachen.phyphox.R;

public class CategoryComparator implements Comparator<ExperimentsInCategory> {
    Resources res;

    public CategoryComparator(Resources res) {
        this.res = res;
    }

    public int compare(ExperimentsInCategory a, ExperimentsInCategory b) {
        if (a.name.equals(res.getString(R.string.categoryRawSensor)))
            return -1;
        if (b.name.equals(res.getString(R.string.categoryRawSensor)))
            return 1;
        if (a.name.equals(res.getString(R.string.save_state_category)))
            return -1;
        if (b.name.equals(res.getString(R.string.save_state_category)))
            return 1;
        if (a.name.equals(phyphoxCat))
            return 1;
        if (b.name.equals(phyphoxCat))
            return -1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    }
}
