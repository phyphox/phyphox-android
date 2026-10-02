package de.rwth_aachen.phyphox.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.viewpager.widget.ViewPager;

//ViewPager (up to and including 1.1.0) looks up its active pointer in every MOVE event and lets
//MotionEvent throw an IllegalArgumentException when that pointer is no longer part of the event,
//which happens on some devices when a child or a system gesture swallows a pointer. Nothing in
//the app can prevent that, so the event is dropped instead of crashing the experiment.
//While the lock holds (an element is maximized) the pager does not page; a swipe that would have
//paged is reported instead, so the activity can ask first and move afterwards.
public class SafeViewPager extends ViewPager {
    public interface PagingLock {
        boolean isLocked();
    }

    public interface BlockedSwipeListener {
        void onBlockedSwipe(int delta); //+1 towards the next page, -1 towards the previous
    }

    private PagingLock pagingLock = null;
    private BlockedSwipeListener blockedSwipeListener = null;
    private final int touchSlop;
    private float downX, downY;
    private boolean swipeReported = false;

    public SafeViewPager(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SafeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPagingLock(PagingLock lock, BlockedSwipeListener listener) {
        pagingLock = lock;
        blockedSwipeListener = listener;
    }

    private boolean locked() {
        return pagingLock != null && pagingLock.isLocked();
    }

    private void trackBlockedSwipe(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                swipeReported = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (swipeReported || blockedSwipeListener == null)
                    break;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (Math.abs(dx) > 2 * touchSlop && Math.abs(dx) > 2 * Math.abs(dy)) {
                    swipeReported = true;
                    blockedSwipeListener.onBlockedSwipe(dx < 0 ? 1 : -1);
                }
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (locked()) {
            trackBlockedSwipe(ev);
            return false;
        }
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (locked())
            return false;
        try {
            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
