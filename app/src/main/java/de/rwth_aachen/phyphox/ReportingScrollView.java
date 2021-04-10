package de.rwth_aachen.phyphox;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class ReportingScrollView extends ScrollView {

    public interface OnScrollChangedListener {
        void onScrollChanged(ReportingScrollView scrollView, int x, int y, int oldx, int oldy);
    }

    private OnScrollChangedListener onScrollChangedListener = null;

    public ReportingScrollView(Context ctx) {
        super(ctx);
    }

    public ReportingScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ReportingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnScrollChangedListener(OnScrollChangedListener onScrollChangedListener) {
        this.onScrollChangedListener = onScrollChangedListener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (onScrollChangedListener != null) {
            onScrollChangedListener.onScrollChanged(this, l, t, oldl, oldt);
        }
    }
}
