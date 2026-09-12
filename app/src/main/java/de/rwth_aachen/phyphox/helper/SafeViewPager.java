package de.rwth_aachen.phyphox.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

//ViewPager (up to and including 1.1.0) looks up its active pointer in every MOVE event and lets
//MotionEvent throw an IllegalArgumentException when that pointer is no longer part of the event,
//which happens on some devices when a child or a system gesture swallows a pointer. Nothing in
//the app can prevent that, so the event is dropped instead of crashing the experiment.
public class SafeViewPager extends ViewPager {
    public SafeViewPager(Context context) {
        super(context);
    }

    public SafeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
