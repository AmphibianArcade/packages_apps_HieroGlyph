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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public final class AnimationManager {

    private static final AtomicBoolean stopped = new AtomicBoolean(false);

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
        if (sWakeLock != null && sWakeLock.isHeld()) {
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

    public static void stopHardwareAnimation() {
        if (stopped.compareAndSet(false, true)) {
            StatusManager.setAnimationActive(false);
            NanoGlyphManager.Java.Matrix.stop();
            stopped.set(false);
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

    public static void updateLedFrame(int[] pattern) {
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();
        int[] newPattern = new int[pattern.length];

        for (int i = 0; i < pattern.length; i++) {
            newPattern[i] = Math.round(pattern[i] / maxPatternBrightness * currentBrightness);
        }
        try {
            NanoGlyphManager.Java.Matrix.setFrame(MatrixUtils.trimToValidFrame(newPattern));
        } catch (Exception e) {
            Log.w (TAG, "Unable to trim pattern:" + e.getMessage());
        }
    }

    private static void updateLedFrame(String[] pattern) {
        updateLedFrame(Arrays.stream(pattern)
                .mapToInt(Integer::parseInt)
                .toArray());
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

    public static void clearLEDs() {
        NanoGlyphManager.Java.Matrix.setBrightness(0, null);
    }

    public static final class Coordinator {
        private static final String TAG = "AnimationCoordinator";
        private static final Coordinator INSTANCE = new Coordinator();

        public static Coordinator get() { return INSTANCE; }

        private final Object lock = new Object();
        private Thread currentWorker;
        private long currentGeneration = 0; 

        private Coordinator() {}

        public void stream(Context ctx, String name) {
            streamToHardware(ctx, null, name, false, false, null);
        }

        public void stream(Context ctx, String name, Runnable onComplete) {
            streamToHardware(ctx, null, name, false, false, onComplete);
        }

        public void stream(Context ctx, String name, boolean reverse) {
            streamToHardware(ctx, null, name, reverse, false, null);
        }

        public void stream(Context ctx, String name, boolean reverse, Runnable onComplete) {
            streamToHardware(ctx, null, name, reverse, false, onComplete);
        }

        public void stream(Context ctx, String name, boolean reverse, boolean alternateOnce) {
            streamToHardware(ctx, null, name, reverse, alternateOnce, null);
        }

        public void stream(Context ctx, String name, boolean reverse, boolean alternateOnce, Runnable onComplete) {
            streamToHardware(ctx, null, name, reverse, alternateOnce, onComplete);
        }

        public void streamCsv(Context ctx, String csv, String name, boolean reverse, boolean alternateOnce) {
            streamToHardware(ctx, csv, name, reverse, alternateOnce, null);
        }

        public void streamCsv(Context ctx, String csv, String name) {
            streamToHardware(ctx, csv, name, false, false, null);
        }

        public void streamCsv(Context ctx, String csv, String name, Runnable onComplete) {
            streamToHardware(ctx, csv, name, false, false, onComplete);
        }

        public void streamCsv(Context ctx, String csv, String name, boolean reverse) {
            streamToHardware(ctx, csv, name, reverse, false, null);
        }

        public void streamCsv(Context ctx, String csv, String name, boolean reverse, Runnable onComplete) {
            streamToHardware(ctx, csv, name, reverse, false, onComplete);
        }

        public void streamToHardware(Context ctx, String csv, String name,
                                boolean reverse, boolean alternateOnce,
                                Runnable onComplete) {
            final long myGeneration;
            synchronized (lock) {
                if (currentWorker != null) {
                    Log.d(TAG, "cancelling previous animation run");
                    currentWorker.interrupt();
                }
                myGeneration = ++currentGeneration;

                StatusManager.setAnimationActive(true);
                acquireWakeLock(ctx);

                Thread worker = new Thread(() ->
                        runWorker(csv, name, reverse, alternateOnce, onComplete, myGeneration),
                        "anim-stream");
                currentWorker = worker;
                worker.start();
            }
        }

        private void runWorker(String csv, String name,
                            boolean reverse, boolean alternateOnce,
                            Runnable onComplete, long myGeneration) {
            try {
                List<int[]> frames = readFrames(csv, name, reverse, alternateOnce);
                if (frames == null || frames.isEmpty()) {
                    Log.w(TAG, "no valid frames for " + name);
                    return;
                }
                if (Thread.currentThread().isInterrupted()) return;

                NanoGlyphManager.Java.Matrix.playPatternAndAwaitCompletion(
                    frames, 60, result -> {
                        if (!result) {
                            Log.w(TAG, "animation ended abnormally");
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                );

            } catch (Throwable t) {
                Log.e(TAG, "worker failed", t);
            } finally {
                stopHardwareAnimation();
                synchronized (lock) {
                    if (currentGeneration == myGeneration) {
                        currentWorker = null;
                    }
                }
            }
        }

        private List<int[]> readFrames(String csv, String name,
                                       boolean reverse, boolean alternateOnce) {
            int pixelCount = MatrixUtils.getMinFrameLength();
            List<int[]> frames = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(csv != null
                    ? new StringReader(csv)
                    : new InputStreamReader(ResourceUtils.getAnimation(name)))) {
                Iterator<String> it = CSVUtils.iterateCsvLines(reader, reverse, alternateOnce);
                while (it.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    String[] split = it.next().split(",");
                    if (split.length != pixelCount) {
                        Log.w(TAG, "line length " + split.length + " != " + pixelCount + ", skipping");
                        continue;
                    }
                    int[] frame = new int[pixelCount];
                    for (int i = 0; i < pixelCount; i++) frame[i] = Integer.parseInt(split[i]);
                    frames.add(frame);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to read animation " + name, e);
                return null;
            }
            return frames;
        }

        public void cancelCurrent() {
            synchronized (lock) {
                if (currentWorker != null) {
                    Log.d(TAG, "cancelling current animation run");
                    currentWorker.interrupt();
                }
            }
        }
    }
}
