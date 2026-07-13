/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.asteroids;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

/**
 * Handles the Nothing Phone (3a) Action Button (scancode 250 -> KEY_ASSIST).
 * Three configurable gestures: short press, long press, double press.
 * Each gesture maps to an action id stored in Settings.System; value 0 = disabled.
 */
public class KeyHandler implements DeviceKeyHandler {

    private static final int SCANCODE_ACTION_BUTTON = 250;

    private static final long LONG_PRESS_MS = 500;
    private static final long DOUBLE_PRESS_MS = 400;

    // Persisted keys (see ActionButtonSettingsFragment)
    static final String KEY_SHORT = "action_button_short";
    static final String KEY_LONG = "action_button_long";
    static final String KEY_DOUBLE = "action_button_double";

    // Action ids (must match arrays.xml values)
    static final int ACTION_NONE = 0;
    static final int ACTION_TORCH = 1;
    static final int ACTION_CAMERA = 2;
    static final int ACTION_SCREENSHOT = 3;
    static final int ACTION_DND = 4;
    static final int ACTION_ASSISTANT = 5;
    static final int ACTION_PLAY_PAUSE = 6;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private long mDownTime = 0;
    private long mLastUpTime = 0;
    private boolean mLongFired = false;
    private boolean mTorchOn = false;
    private String mCameraId = null;

    private final Runnable mLongPressRunnable = () -> {
        mLongFired = true;
        runAction(getAction(KEY_LONG));
    };

    private final Runnable mPendingShortRunnable = () -> runAction(getAction(KEY_SHORT));

    public KeyHandler(Context context) {
        mContext = context;
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getScanCode() != SCANCODE_ACTION_BUTTON) {
            return event; // not ours, pass through
        }

        final int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            mDownTime = SystemClock.uptimeMillis();
            mLongFired = false;
            if (getAction(KEY_LONG) != ACTION_NONE) {
                mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_MS);
            }
        } else if (action == KeyEvent.ACTION_UP) {
            mHandler.removeCallbacks(mLongPressRunnable);
            if (mLongFired) {
                // long press already handled
                mLastUpTime = 0;
            } else {
                final long now = SystemClock.uptimeMillis();
                final boolean doubleEnabled = getAction(KEY_DOUBLE) != ACTION_NONE;
                if (doubleEnabled && mLastUpTime != 0
                        && (now - mLastUpTime) < DOUBLE_PRESS_MS) {
                    // second tap -> double press
                    mHandler.removeCallbacks(mPendingShortRunnable);
                    mLastUpTime = 0;
                    runAction(getAction(KEY_DOUBLE));
                } else if (doubleEnabled) {
                    // wait to see if a second tap arrives
                    mLastUpTime = now;
                    mHandler.postDelayed(mPendingShortRunnable, DOUBLE_PRESS_MS);
                } else {
                    // no double configured: fire short immediately
                    mLastUpTime = 0;
                    runAction(getAction(KEY_SHORT));
                }
            }
        }
        return null; // consume the event
    }

    private int getAction(String key) {
        return Settings.System.getInt(mContext.getContentResolver(), key,
                getDefault(key));
    }

    private int getDefault(String key) {
        switch (key) {
            case KEY_SHORT: return ACTION_TORCH;
            case KEY_LONG: return ACTION_ASSISTANT;
            default: return ACTION_NONE; // double
        }
    }

    private void runAction(int id) {
        switch (id) {
            case ACTION_TORCH: toggleTorch(); break;
            case ACTION_CAMERA: launchCamera(); break;
            case ACTION_SCREENSHOT: takeScreenshot(); break;
            case ACTION_DND: toggleDnd(); break;
            case ACTION_ASSISTANT: launchAssistant(); break;
            case ACTION_PLAY_PAUSE: mediaPlayPause(); break;
            default: return; // ACTION_NONE
        }
        vibrate();
    }

    private void vibrate() {
        Vibrator v = mContext.getSystemService(Vibrator.class);
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void toggleTorch() {
        CameraManager cm = mContext.getSystemService(CameraManager.class);
        if (cm == null) return;
        try {
            if (mCameraId == null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean has = cm.getCameraCharacteristics(id)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (Boolean.TRUE.equals(has)) { mCameraId = id; break; }
                }
            }
            if (mCameraId != null) {
                mTorchOn = !mTorchOn;
                cm.setTorchMode(mCameraId, mTorchOn);
            }
        } catch (Exception ignored) { }
    }

    private void launchCamera() {
        Intent i = new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { mContext.startActivity(i); } catch (Exception ignored) { }
    }

    private void launchAssistant() {
        Intent i = new Intent(Intent.ACTION_VOICE_COMMAND);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { mContext.startActivity(i); } catch (Exception ignored) { }
    }

    private void toggleDnd() {
        Intent i = new Intent("android.settings.ZEN_MODE_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { mContext.startActivity(i); } catch (Exception ignored) { }
    }

    private void mediaPlayPause() {
        AudioManager am = mContext.getSystemService(AudioManager.class);
        if (am == null) return;
        long time = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(time, time,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(time, time,
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
    }

    private void takeScreenshot() {
        // Delegated via global action broadcast is not public; use accessibility-like intent.
        Intent i = new Intent("com.android.systemui.action.SCREENSHOT");
        i.setPackage("com.android.systemui");
        try { mContext.sendBroadcast(i); } catch (Exception ignored) { }
    }
}
