package com.example.chronox.common;

import android.view.MotionEvent;
import android.view.View;

public class AnimUtils {

    /**
     * Applies a subtle, fast 3D physical press animation (scale down) to a button.
     * Duration: 150ms. Scale: 0.95f.
     */
    public static void applyButtonPressAnimation(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                     .scaleX(0.95f)
                     .scaleY(0.95f)
                     .setDuration(100)
                     .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                     .scaleX(1.0f)
                     .scaleY(1.0f)
                     .setDuration(150)
                     .start();
                    break;
            }
            return false; // let the original click listener handle the click
        });
    }
}
