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

    //Exclusive mode sets fillViewport: the content must fit the viewport exactly. ScrollView itself only stretches content
    //that is shorter, so a maximized graph whose toolbar is taller than a small viewport kept a plot of zero height.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!isFillViewport || childCount == 0 || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            return
        val child = getChildAt(0)
        val lp = child.layoutParams as MarginLayoutParams
        val height = measuredHeight - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin
        if (child.measuredHeight <= height)
            return
        val widthSpec = getChildMeasureSpec(widthMeasureSpec, paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin, lp.width)
        child.measure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
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
