package de.rwth_aachen.phyphox

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ScrollView

/*When the overlay passapert is updated, it is interruped with the scrolling activity.
* This class controlls the scrolling from the overlay touch event
* Only used for Camera View
*  */
class CustomScrollableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private var isSwipeEnabled = true

    fun setScrollEnabled(enabled: Boolean) {
        isSwipeEnabled = enabled
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isSwipeEnabled) {
            super.onTouchEvent(event);
        } else {
            false
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return if (isSwipeEnabled) {
            super.onInterceptTouchEvent(event);
        } else {
            false
        }
    }
}
