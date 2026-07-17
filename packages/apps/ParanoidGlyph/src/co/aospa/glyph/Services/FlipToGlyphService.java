/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
 *               2020-2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.glyph.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import android.os.Handler;
import android.os.Looper;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Sensors.FlipToGlyphSensor;

public class FlipToGlyphService extends Service {

    private static final String TAG = "FlipToGlyphService";
    private static final boolean DEBUG = true;

    // Settle time: the phone must stay face-down this long before we engage
    private static final long FLIP_SETTLE_MS = 1500;

    private boolean isFlipped;
    private int ringerMode;

    private final Handler mFlipHandler = new Handler(Looper.getMainLooper());
    private final Runnable mEngageFlip = this::engageFlip;

    private AudioManager mAudioManager;
    private FlipToGlyphSensor mFlipToGlyphSensor;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;
    private BatteryManager mBatteryManager;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mFlipToGlyphSensor = new FlipToGlyphSensor(this, this::onFlip);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mBatteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        mFlipToGlyphSensor.enable();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        mFlipToGlyphSensor.disable();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onFlip(boolean flipped) {
        if (flipped == isFlipped) return;
        if (DEBUG) Log.d(TAG, "Flipped: " + flipped);
        if (flipped) {
            // The screen is usually off here, so hold the AP awake across the
            // settle delay -- postDelayed runs on uptime, which does not
            // advance while suspended, and engageFlip would otherwise never run.
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire(FLIP_SETTLE_MS + 1000);
            }
            // Wait for the phone to settle on the table before engaging
            mFlipHandler.removeCallbacks(mEngageFlip);
            mFlipHandler.postDelayed(mEngageFlip, FLIP_SETTLE_MS);
        } else {
            mFlipHandler.removeCallbacks(mEngageFlip);
            if (isFlipped) {
                mAudioManager.setRingerModeInternal(ringerMode);
                isFlipped = false;
            }
        }
    }

    private void engageFlip() {
        if (isFlipped) return;
        if (DEBUG) Log.d(TAG, "Flip settled, engaging");
        // Cover the flip animation plus an optional battery readout after it.
        mWakeLock.acquire(4000);
        AnimationManager.playCsv("flip");
        // When charging, show the level right after the flip -- playCharging
        // waits on the flip animation, so the two play in sequence rather than
        // fighting over the LEDs.
        if (mBatteryManager.isCharging()
                && SettingsManager.isGlyphChargingEnabled()) {
            AnimationManager.playCharging(mBatteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY), true);
        }
        ringerMode = mAudioManager.getRingerModeInternal();
        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
        isFlipped = true;
    }
}
