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

package org.nukisystems.hieroglyph.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Manager.StatusManager;
import org.nukisystems.hieroglyph.Sensors.FlipToGlyphSensor;

public class FlipToGlyphService extends Service {

    private static final String TAG = "FlipToGlyphService";
    private static final boolean DEBUG = true;

    private boolean isFlipped;
    private int ringerMode;

    private HandlerThread thread;
    private Handler mThreadHandler;

    private AudioManager mAudioManager;
    private FlipToGlyphSensor mFlipToGlyphSensor;
    private Context mContext;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        // Add a handler thread
        thread = new HandlerThread("FlipToGlyphService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        mContext = this;
        mFlipToGlyphSensor = new FlipToGlyphSensor(this, this::onFlip);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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
        thread.quit();
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
            if (SettingsManager.isGlyphFlipAnimationEnabled()
                    && StatusManager.isGlyphIdle()) {
                String animationName = SettingsManager.getGlyphFlipAnimation();
                boolean shouldReverse = SettingsManager.isGlyphFlipAnimationReversed();
                if (animationName.equals(Constants.Settings.Notification.ANIMATION_ALTERNATE)) {
                    AnimationManager.Coordinator.get().stream(mContext, SettingsManager.getGlyphNotifsAnimation(), false, true);
                } else if (shouldReverse) {
                    AnimationManager.Coordinator.get().stream(mContext, animationName, true);
                } else {
                    AnimationManager.Coordinator.get().stream(mContext, animationName);
                }
            }

            ringerMode = mAudioManager.getRingerModeInternal();
            int preferredMode = SettingsManager.getFlipRingerMode();
            if (DEBUG) Log.d(TAG, "Preferred ringer mode: " + preferredMode);
            
            if (preferredMode != -1) {
                if (DEBUG) Log.d(TAG, "Setting ringer mode to: " + preferredMode);
                mAudioManager.setRingerModeInternal(preferredMode);
            } else {
                if (DEBUG) Log.d(TAG, "Following system ringer mode: " + ringerMode);
            }
        } else {
            int preferredMode = SettingsManager.getFlipRingerMode();
            if (preferredMode != -1) {
                mAudioManager.setRingerModeInternal(ringerMode);
            }
        }
        isFlipped = flipped;
    }
}
