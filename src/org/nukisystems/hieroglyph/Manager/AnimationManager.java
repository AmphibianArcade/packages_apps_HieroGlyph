/*
 * Copyright (C) 2022-2024 Paranoid Android
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

package org.nukisystems.hieroglyph.Manager;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public final class AnimationManager {

    private static final String TAG = "GlyphAnimationManager";
    private static final boolean DEBUG = true;
    private static PowerManager.WakeLock sWakeLock;

    private static final ExecutorService animationExecutor = Executors.newSingleThreadExecutor();

    private static void acquireWakeLock(Context context) {
        if (sWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            sWakeLock.acquire();
            if (DEBUG) Log.d(TAG, "Acquired wakelock");
        }
    }

    private static void releaseWakeLock() {
        if (sWakeLock != null) {
            sWakeLock.release();
            sWakeLock = null;
            if (DEBUG) Log.d(TAG, "Released wakelock");
        }
    }

    private static Future<?> submit(Runnable runnable) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        return executorService.submit(runnable);
    }

    private static boolean check(String name, boolean wait) {
        if (DEBUG) Log.d(TAG, "Playing animation | name: " + name + " | waiting: " + Boolean.toString(wait));

        if (StatusManager.isAllLedActive()) {
            if (DEBUG) Log.d(TAG, "All LEDs are active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isCallLedActive()) {
            if (DEBUG) Log.d(TAG, "Call animation is currently active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isAnimationActive()) {
            long start = System.currentTimeMillis();
            if (wait) {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, wait | name: " + name);
                while (StatusManager.isAnimationActive()) {
                    if (System.currentTimeMillis() - start >= 2500) return false;
                }
            } else {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, exiting | name: " + name);
                return false;
            }
        }

        return true;
    }

    private static boolean checkInterruption(String name) {
        return StatusManager.isAllLedActive()
                || (!Objects.equals(name, "call") && StatusManager.isCallLedEnabled())
                || (Objects.equals(name, "call") && !StatusManager.isCallLedEnabled())
                || (Objects.equals(name, "progress") && StatusManager.isVolumeAnimationActive());
    }

    public static void stream(Context ctx, String name) {
        streamToHardware(ctx, null, name, false, false, null);
    }

    public static void stream(Context ctx, String name, Runnable onComplete) {
        streamToHardware(ctx, null, name, false, false, onComplete);
    }

    public static void stream(Context ctx, String name, boolean reverse) {
        streamToHardware(ctx, null, name, reverse, false, null);
    }

    public static void stream(Context ctx, String name, boolean reverse, Runnable onComplete) {
        streamToHardware(ctx, null, name, reverse, false, onComplete);
    }

    public static void stream(Context ctx, String name, boolean reverse, boolean alternateOnce) {
        streamToHardware(ctx, null, name, reverse, alternateOnce, null);
    }

    public static void stream(Context ctx, String name, boolean reverse, boolean alternateOnce, Runnable onComplete) {
        streamToHardware(ctx, null, name, reverse, alternateOnce, onComplete);
    }

    public static void streamCsv(Context ctx, String csv, String name, boolean reverse, boolean alternateOnce) {
        streamToHardware(ctx, csv, name, reverse, alternateOnce, null);
    }

    public static void streamCsv(Context ctx, String csv, String name) {
        streamToHardware(ctx, csv, name, false, false, null);
    }

    public static void streamCsv(Context ctx, String csv, String name, Runnable onComplete) {
        streamToHardware(ctx, csv, name, false, false, onComplete);
    }

    public static void streamCsv(Context ctx, String csv, String name, boolean reverse) {
        streamToHardware(ctx, csv, name, reverse, false, null);
    }

    public static void streamCsv(Context ctx, String csv, String name, boolean reverse, Runnable onComplete) {
        streamToHardware(ctx, csv, name, reverse, false, onComplete);
    }

    private static void streamToHardware(Context ctx, String csv, String name,
                                        boolean reverse, boolean alternateOnce,
                                        final Runnable onComplete) {
        StatusManager.setAnimationActive(true);
        acquireWakeLock(ctx);
        animationExecutor.execute(() -> {
            int pixelCount = MatrixUtils.getMinFrameLength();

            List<int[]> frames = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(csv != null ? new StringReader(csv)
                    : new InputStreamReader(ResourceUtils.getAnimation(name)))) {
                Iterator<String> it = CSVUtils.iterateCsvLines(reader, reverse, alternateOnce);
                while (it.hasNext()) {
                    String[] split = it.next().split(",");
                    if (split.length != pixelCount) {
                        Log.w(TAG, "streamToHardware: line length " + split.length
                                + " != pixelCount " + pixelCount + ", skipping frame");
                        continue;
                    }
                    int[] frame = new int[pixelCount];
                    for (int i = 0; i < pixelCount; i++) {
                        frame[i] = Integer.parseInt(split[i]);
                    }
                    frames.add(frame);
                }
            } catch (Exception e) {
                Log.e(TAG, "streamToHardware: failed to read animation " + name, e);
                StatusManager.setAnimationActive(false);
                return;
            }

            if (frames.isEmpty()) {
                Log.w(TAG, "streamToHardware: no valid frames for " + name);
                StatusManager.setAnimationActive(false);
                return;
            }

            final int fps = 60;
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = {false};

            NanoGlyphManager.Java.Matrix.playPatternAndAwaitCompletion(frames, fps, result -> {
                success[0] = result;
                latch.countDown();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                NanoGlyphManager.Java.Matrix.stop(null);
            }

            if (!success[0]) {
                Log.w(TAG, "streamToHardware: animation ended abnormally");
            }

            if (onComplete != null) onComplete.run();
            stopHardwareAnimation();
        });
    }

    public static void stopHardwareAnimation() {
        StatusManager.setAnimationActive(false);
        releaseWakeLock();
        NanoGlyphManager.Java.Matrix.stop(null);
    }

    public static void playCharging(int batteryLevel, boolean wait) {
        if (!check("charging", wait))
            return;

        StatusManager.setAnimationActive(true);
        StatusManager.setChargingAnimationActive(true);

        int[] batteryArray = StatusManager.getBatteryArray();
        int amount = (int) Math.floor((batteryLevel / 100D) * batteryArray.length);
        int last = StatusManager.getChargingLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                        updateLedFrame(batteryArray);
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    updateLedFrame(batteryArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setChargingLedLast(0);
                batteryArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
                updateLedFrame(batteryArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setBatteryArray(batteryArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: charging");
        }
    }

    public static void dismissCharging() {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
        int[] batteryArray = StatusManager.getBatteryArray();

        if (Arrays.equals(emptyArray, batteryArray))
            return;

        if (!check("Dismiss charging", false))
            return;

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss charging")) throw new InterruptedException();
            for (int i = batteryArray.length - 1; i >= 0; i--) {
                if (checkInterruption("Dismiss charging")) throw new InterruptedException();
                if (batteryArray[i] != 0) {
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    updateLedFrame(batteryArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss charging");
            if (!StatusManager.isAllLedActive())
                updateLedFrame(new int[batteryArray.length]);
        } finally {
            StatusManager.setChargingLedLast(0);
            StatusManager.setChargingAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss charging");
        }
    }

    public static void playVolume(Context context, int volumeLevel, boolean wait) {
        if (!check("volume", wait))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);
        StatusManager.setVolumeAnimationActive(true);

        int[] volumeArray = StatusManager.getVolumeArray();
        if (volumeArray == null) {
            if (DEBUG) Log.d(TAG, "Volume array is null, cannot play animation");
            return;
        }

        int amount = (int) Math.round((volumeLevel / 100D) * volumeArray.length);
        int last = StatusManager.getVolumeLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setVolumeLedLast(0);
                volumeArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
                updateLedFrame(volumeArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setVolumeArray(volumeArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: volume");
            releaseWakeLock();
        }
    }

    public static void dismissVolume(Context context) {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int[] volumeArray = StatusManager.getVolumeArray();

        if (Arrays.equals(emptyArray, volumeArray)) {
            StatusManager.setVolumeAnimationActive(false);
            return;
        }

        if (!check("Dismiss volume", false))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss volume")) throw new InterruptedException();
            for (int i = volumeArray.length - 1; i >= 0; i--) {
                if (volumeArray[i] != 0) {
                    if (checkInterruption("Dismiss volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss volume");
            updateLedFrame(new int[volumeArray.length]);
        } finally {
            StatusManager.setVolumeLedLast(0);
            StatusManager.setVolumeAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss volume");
            releaseWakeLock();
        }
    }

    public static void playCall(String name, boolean reversed) {
        StatusManager.setCallLedEnabled(true);

        if (!check("call: " + name, true))
            return;

        StatusManager.setCallLedActive(true);

        while (StatusManager.isCallLedEnabled()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ResourceUtils.getCallAnimation(name)))) {
                Iterator<String> it = CSVUtils.iterateCsvLines(reader, reversed);
                while (it.hasNext()) {
                    if (checkInterruption("call")) throw new InterruptedException();
                    String[] pattern = it.next().split(",");
                    if (ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                        updateLedFrame(pattern);
                    } else {
                        if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + it.next());
                        throw new InterruptedException();
                    }
                    Thread.sleep(16, 666000);
                }
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
            } finally {
                if (StatusManager.isAllLedActive()) {
                    if (DEBUG) Log.d(TAG, "All LED active, pause playing animation | name: " + name);
                    while (StatusManager.isAllLedActive()) {}
                }
            }
        }
    }

    public static void stopCall() {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        StatusManager.setCallLedEnabled(false);
        clearLEDs();
        StatusManager.setCallLedActive(false);
        if (DEBUG) Log.d(TAG, "Done playing Call Animation");
    }

    public static void playEssential() {
        if (DEBUG) Log.d(TAG, "Playing Essential Animation");
        if (!StatusManager.isEssentialLedActive()) {
            submit(() -> {
                if (!check("essential", true))
                    return;

                StatusManager.setAnimationActive(true);

                try {
                    if (checkInterruption("essential")) throw new InterruptedException();
                    int[] steps = {12, 24, 36, 48, 60};
                    int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                    for (int i : steps) {
                        if (checkInterruption("essential")) throw new InterruptedException();
                        updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * i);
                        Thread.sleep(16, 666000);
                    }
                } catch (InterruptedException ignored) {}
                StatusManager.setAnimationActive(false);
                StatusManager.setEssentialLedActive(true);
                if (DEBUG) Log.d(TAG, "Done playing animation | name: essential");
            });
        } else {
                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * 60);
            }
        }
    }

    public static void stopEssential() {
        if (DEBUG) Log.d(TAG, "Disabling Essential Animation");
        StatusManager.setEssentialLedActive(false);
        if (!StatusManager.isAnimationActive() && !StatusManager.isAllLedActive()) {
            if (Constants.Device.isPhone3a()) {
                clearLEDs();
            } else {
                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                updateLedSingle(led, 0);
            }
        }
    }

    public static void updateLedFrame(int[] pattern) {
        NanoGlyphManager.Java.Matrix.setFrame(pattern);
    }

    private static void updateLedFrame(String[] pattern) {
        updateLedFrame(Arrays.stream(pattern)
                .mapToInt(Integer::parseInt)
                .toArray());
    }

    private static void updateLedFrame(float[] pattern) {
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();
        int[] newPattern = new int[pattern.length];

        for (int i = 0; i < pattern.length; i++) {
            newPattern[i] = Math.round(pattern[i] / maxPatternBrightness * currentBrightness);
        }

        updateLedFrame(newPattern);
    }

    private static void updateLedSingle(int led, String brightness) {
        updateLedSingle(led, Integer.parseInt(brightness));
    }

    private static void updateLedSingle(int led, float brightness) {
        updateLedSingle(led, Math.round(brightness));
    }

    private static void updateLedSingle(int led, int brightness) {
        if (led > (MatrixUtils.getMinFrameLength() - 1) || led < 0) {
            Log.w(TAG, "Invalid led index: " + led  + " in updateLedSingle");
            return;
        }
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();

        brightness = Math.round(brightness / maxPatternBrightness * currentBrightness);

        NanoGlyphManager.Java.Matrix.setSingle(led, brightness);

    }

    public static void playProgress(Context context, int progressPercent, int progressType, boolean wait) {
        if (!check("progress", wait))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);
        StatusManager.setProgressAnimationActive(true);
        StatusManager.setProgressType(progressType);

        int[] progressArray = StatusManager.getProgressArray();
        if (progressArray == null) {
            if (DEBUG) Log.d(TAG, "Progress array is null, cannot play animation");
            return;
        }

        int amount = (int) Math.round((progressPercent / 100D) * progressArray.length);
        int last = StatusManager.getProgressLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = 0;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: progress");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setProgressLedLast(0);
                progressArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
                updateLedFrame(progressArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setProgressArray(progressArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: progress");
            releaseWakeLock();
        }
    }

    public static void dismissProgress(Context context) {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int[] progressArray = StatusManager.getProgressArray();

        if (Arrays.equals(emptyArray, progressArray))
            return;

        if (!check("Dismiss progress", false))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss progress")) throw new InterruptedException();
            for (int i = progressArray.length - 1; i >= 0; i--) {
                if (progressArray[i] != 0) {
                    if (checkInterruption("Dismiss progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = 0;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss progress");
            if (!StatusManager.isAllLedActive()) {
                updateLedFrame(new int[progressArray.length]);
            }
        } finally {
            StatusManager.setProgressLedLast(0);
            StatusManager.setProgressAnimationActive(false);
            StatusManager.setProgressType(0);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss progress");
            releaseWakeLock();
        }
    }

    public static void clearLEDs() {
        NanoGlyphManager.Java.Matrix.setBrightness(0, null);
    }
}
