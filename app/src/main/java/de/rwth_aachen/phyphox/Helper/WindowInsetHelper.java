package de.rwth_aachen.phyphox.Helper;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class WindowInsetHelper {

    public enum ApplyTo {
        MARGIN,
        PADDING,
        IGNORE
    }

    // From Android 15 (SDK 35), because of edge-to-edge UI, there should be inset at status bar
    public static void setInsets(View view, ApplyTo left, ApplyTo top, ApplyTo right, ApplyTo bottom) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {

            Insets innerPadding = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

            if (left == ApplyTo.MARGIN)
                mlp.leftMargin = innerPadding.left;
            if (top == ApplyTo.MARGIN)
             mlp.topMargin = innerPadding.top;
            if (right == ApplyTo.MARGIN)
                mlp.rightMargin = innerPadding.right;
            if (bottom == ApplyTo.MARGIN)
                mlp.bottomMargin = innerPadding.bottom;

            v.setLayoutParams(mlp);

            v.setPadding(
                    left == ApplyTo.PADDING ? innerPadding.left : v.getPaddingLeft(),
                    top == ApplyTo.PADDING ? innerPadding.top : v.getPaddingTop(),
                    right == ApplyTo.PADDING ? innerPadding.right : v.getPaddingRight(),
                    bottom == ApplyTo.PADDING ? innerPadding.bottom : v.getPaddingBottom()
            );

            return WindowInsetsCompat.CONSUMED;
        });
    }
}
