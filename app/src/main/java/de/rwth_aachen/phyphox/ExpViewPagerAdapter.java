package de.rwth_aachen.phyphox;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class ExpViewPagerAdapter extends FragmentPagerAdapter {

    private PhyphoxExperiment experiment;

    public ExpViewPagerAdapter(FragmentManager fm, PhyphoxExperiment experiment) {
        super(fm);
        this.experiment = experiment;
    }

    @Override
    public Fragment getItem(int position) {
        return ExpViewFragment.newInstance(position);
    }

    @Override
    public int getCount() {
        if (experiment != null)
            return experiment.experimentViews.size();
        else
            return 0;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return experiment.experimentViews.get(position).name;
    }

}
