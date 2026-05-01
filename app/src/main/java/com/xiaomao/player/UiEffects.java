package com.xiaomao.player;

import android.view.MotionEvent;
import android.view.View;

public final class UiEffects {
    private static final float PRESSED_SCALE = 0.96f;
    private static final float PRESSED_ALPHA = 0.9f;

    private UiEffects() {
    }

    public static void bindPressScale(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener((target, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                applyPressedState(target, true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                applyPressedState(target, false);
            }
            return false;
        });
    }

    public static void applyPressedState(View view, boolean pressed) {
        if (view == null) {
            return;
        }
        view.animate()
                .scaleX(pressed ? PRESSED_SCALE : 1f)
                .scaleY(pressed ? PRESSED_SCALE : 1f)
                .alpha(pressed ? PRESSED_ALPHA : 1f)
                .setDuration(pressed ? 80L : 140L)
                .start();
    }

    public static void reset(View view) {
        applyPressedState(view, false);
    }
}
