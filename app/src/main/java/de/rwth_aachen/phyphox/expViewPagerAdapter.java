package de.rwth_aachen.phyphox;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;

public class expViewPagerAdapter extends FragmentPagerAdapter {

    private phyphoxExperiment experiment;

    public expViewPagerAdapter(FragmentManager fm, phyphoxExperiment experiment) {
        super(fm);
        this.experiment = experiment;
    }

    @Override
    public Fragment getItem(int position) {
        return expViewFragment.newInstance(position);
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
