package com.xiaomao.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

public class XiaomaoJzvdStd extends JzvdStd {
    private static final float[] SPEEDS = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final float HOLD_SPEED = 2.0f;

    private TextView speedButton;
    private TextView speedHud;
    private float selectedSpeed = 1.0f;
    private float speedBeforeHold = 1.0f;
    private boolean holdingSpeed = false;
    private float holdDownX;
    private float holdDownY;
    private final Runnable startHoldSpeed = () -> {
        if (!canApplySpeed()) {
            return;
        }
        holdingSpeed = true;
        speedBeforeHold = selectedSpeed;
        applySpeed(HOLD_SPEED, false);
        showSpeedHud("长按加速 " + formatSpeed(HOLD_SPEED));
    };

    public XiaomaoJzvdStd(Context context) {
        super(context);
        ensureSpeedControls();
    }

    public XiaomaoJzvdStd(Context context, AttributeSet attrs) {
        super(context, attrs);
        ensureSpeedControls();
    }

    @Override
    public void init(Context context) {
        super.init(context);
        post(this::ensureSpeedControls);
    }

    @Override
    public void onStatePlaying() {
        super.onStatePlaying();
        applySpeed(selectedSpeed, false);
    }

    @Override
    public void onStatePreparing() {
        super.onStatePreparing();
        holdingSpeed = false;
        updateSpeedLabel();
    }

    @Override
    public void onStateNormal() {
        super.onStateNormal();
        holdingSpeed = false;
        selectedSpeed = 1.0f;
        updateSpeedLabel();
    }

    @Override
    public void onStateAutoComplete() {
        super.onStateAutoComplete();
        holdingSpeed = false;
        selectedSpeed = 1.0f;
        updateSpeedLabel();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean handled = super.onTouch(v, event);
        if (v == textureViewContainer) {
            handleHoldSpeed(event);
        }
        return handled;
    }

    private void ensureSpeedControls() {
        if (speedButton != null && speedButton.getParent() != null) {
            return;
        }

        speedButton = makePill(getContext());
        speedButton.setTextColor(Color.WHITE);
        speedButton.setTextSize(12f);
        speedButton.setTypeface(Typeface.DEFAULT_BOLD);
        speedButton.setOnClickListener(v -> cycleSpeed());
        updateSpeedLabel();
        FrameLayout.LayoutParams speedParams = new FrameLayout.LayoutParams(dp(58), dp(32), Gravity.BOTTOM | Gravity.END);
        speedParams.setMargins(0, 0, dp(12), dp(46));
        addView(speedButton, speedParams);

        speedHud = makePill(getContext());
        speedHud.setTextColor(Color.WHITE);
        speedHud.setTextSize(15f);
        speedHud.setTypeface(Typeface.DEFAULT_BOLD);
        speedHud.setVisibility(GONE);
        speedHud.setAlpha(0f);
        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(dp(150), dp(46), Gravity.CENTER);
        addView(speedHud, hudParams);
    }

    private TextView makePill(Context context) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#99000000"));
        background.setStroke(dp(1), Color.parseColor("#55FFFFFF"));
        background.setCornerRadius(dp(18));
        view.setBackground(background);
        return view;
    }

    private void cycleSpeed() {
        int nextIndex = 0;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - selectedSpeed) < 0.01f) {
                nextIndex = (i + 1) % SPEEDS.length;
                break;
            }
        }
        selectedSpeed = SPEEDS[nextIndex];
        applySpeed(selectedSpeed, true);
    }

    private void handleHoldSpeed(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holdDownX = event.getX();
                holdDownY = event.getY();
                removeCallbacks(startHoldSpeed);
                postDelayed(startHoldSpeed, 450L);
                break;
            case MotionEvent.ACTION_MOVE:
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(event.getX() - holdDownX) > slop || Math.abs(event.getY() - holdDownY) > slop) {
                    cancelHoldSpeed(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelHoldSpeed(true);
                break;
            default:
                break;
        }
    }

    private void cancelHoldSpeed(boolean restore) {
        removeCallbacks(startHoldSpeed);
        if (holdingSpeed) {
            holdingSpeed = false;
            if (restore) {
                applySpeed(speedBeforeHold, false);
                showSpeedHud("恢复 " + formatSpeed(speedBeforeHold));
            }
        }
    }

    private void applySpeed(float speed, boolean notify) {
        selectedSpeed = speed;
        updateSpeedLabel();
        if (!canApplySpeed()) {
            if (notify) {
                Toast.makeText(getContext(), "开始播放后生效：" + formatSpeed(speed), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            mediaInterface.setSpeed(speed);
            if (notify) {
                showSpeedHud("倍速 " + formatSpeed(speed));
            }
        } catch (Throwable ignored) {
            if (notify) {
                Toast.makeText(getContext(), "当前播放内核不支持倍速", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean canApplySpeed() {
        return mediaInterface != null
                && (state == Jzvd.STATE_PLAYING
                || state == Jzvd.STATE_PAUSE);
    }

    private void updateSpeedLabel() {
        if (speedButton != null) {
            speedButton.setText(formatSpeed(selectedSpeed));
        }
    }

    private void showSpeedHud(String text) {
        if (speedHud == null) {
            return;
        }
        speedHud.setText(text);
        speedHud.setVisibility(VISIBLE);
        speedHud.animate().cancel();
        speedHud.setAlpha(1f);
        speedHud.animate().alpha(0f).setStartDelay(650L).setDuration(240L).withEndAction(() -> speedHud.setVisibility(GONE)).start();
    }

    private String formatSpeed(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.01f) {
            return String.format(Locale.US, "%dx", Math.round(speed));
        }
        return String.format(Locale.US, "%.2fx", speed).replace(".00", "").replace("0x", "x");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
