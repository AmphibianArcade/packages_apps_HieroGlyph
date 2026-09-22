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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils.Volume.Style;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public final class AnimationManager {

    private static AtomicBoolean stopped = new AtomicBoolean(false);

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


    public static void stopHardwareAnimation() {
        if (stopped.compareAndSet(false, true)) {
            NanoGlyphManager.Java.Matrix.stop();
            stopped.set(false);
            releaseWakeLock();
        }
    }

    public static void playCall(Object owner, String name, boolean reversed) {
        boolean gotIt = StatusManager.acquire(owner, StatusManager.GlyphPriority.CALL, null);
        if (!gotIt) return;

        stopped.set(false);
        try {
            runAnimationLoop(owner, name, reversed);
        } finally {
            StatusManager.release(owner);
        }
    }

    public static void stop() {
        stopped.set(true);
    }

    private static void runAnimationLoop(Object owner, String name, boolean reversed) {
        while (!stopped.get() && StatusManager.isOwnedBy(owner)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ResourceUtils.getCallAnimation(name)))) {
                Iterator<String> it = CSVUtils.iterateCsvLines(reader, reversed);
                while (it.hasNext() && !stopped.get() && StatusManager.isOwnedBy(owner)) {
                    String line = it.next();
                    String[] pattern = line.split(",");
                    if (!ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                        if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + line);
                        return;
                    }
                    updateLedFrame(pattern);
                    Thread.sleep(16, 666000);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
                return;
            }
        }
    }

    public static void stopCall(Object owner) {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        if (StatusManager.isOwnedBy(owner)) {
            clearLEDs();
        }
        StatusManager.release(owner);
    }

    public static void updateLedFrame(int[] pattern) {
        float currentBrightness = (float) Constants.getBrightness();
        int[] newPattern = new int[pattern.length];

        for (int i = 0; i < pattern.length; i++) {
            newPattern[i] = applyDimmer(pattern[i],  Math.round(currentBrightness));
        }
        updateLedFrameRaw(newPattern);
    }

    public static void updateLedFrameRaw(int[] pattern) {
        try {
            NanoGlyphManager.Java.Matrix.setFrame(MatrixUtils.trimToValidFrame(pattern));
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

        brightness = applyDimmer(brightness, Constants.MAX_PATTERN_BRIGHTNESS);

        NanoGlyphManager.Java.Matrix.setSingle(led, brightness);

    }

    public static void playVolume(Context context, int volumeLevel) {

        acquireWakeLock(context);
        Style animStyle = Style.fromInt(SettingsManager.Volume.getStyle());
        int rotation = SettingsManager.Volume.getRotation();
        boolean showCross = SettingsManager.Volume.showCrossWhenEmpty();
        int[] volumeMatrixFrame = new int[MatrixUtils.getMaxFrameLength()];

        try {
            if (!StatusManager.isPriorityActive(GlyphPriority.VOLUME)) return;

            if (volumeLevel > 0) {
                volumeMatrixFrame = MatrixUtils.Volume.generateFrame(animStyle, volumeLevel, rotation);
            } else if (showCross) {
                volumeMatrixFrame = MatrixUtils.Shape.Cross(
                        Constants.getMaxBrightness(), (MatrixUtils.getGridSize() / 2) - 4);
            } else {
                Arrays.fill(volumeMatrixFrame, 0);
            }

            StatusManager.setVolumeArray(volumeMatrixFrame);
            updateLedFrame(volumeMatrixFrame);

            Thread.sleep(16, 666000);
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "volume animation interrupted");
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "invalid volume frame: " + e);
        } finally {
            releaseWakeLock();
            if (DEBUG) Log.d(TAG, "done playing volume animation");
        }
    }

    public static int applyDimmer(int rawValue, int globalBrightness) {
        rawValue = Math.clamp(rawValue, 0, Constants.MAX_PATTERN_BRIGHTNESS);
        globalBrightness = Math.clamp(globalBrightness, 0, Constants.MAX_PATTERN_BRIGHTNESS);
        return (rawValue * globalBrightness + (Constants.MAX_PATTERN_BRIGHTNESS / 2)) / Constants.MAX_PATTERN_BRIGHTNESS;
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
        private Object currentOwner;
        private long currentGeneration = 0;

        public void stream(Context ctx, Object owner, GlyphPriority priority, String name) {
            stream(ctx, owner, priority, null, name, false, false, null);
        }

        public void stream(Context ctx, Object owner, GlyphPriority priority, String name, Runnable onComplete) {
            stream(ctx, owner, priority, null, name, false, false, onComplete);
        }

        public void streamCsv(Context ctx, Object owner, GlyphPriority priority, String csv, String name) {
            stream(ctx, owner, priority, csv, name, false, false, null);
        }

        public void stream(Context ctx, Object owner, GlyphPriority priority, String name, boolean reverse) {
            stream(ctx, owner, priority, null, name, reverse, false, null);
        }

        public void stream(Context ctx, Object owner, GlyphPriority priority, String name, boolean reverse, boolean alternateOnce) {
            stream(ctx, owner, priority, null, name, reverse, alternateOnce, null);
        }


        public void stream(Context ctx, Object owner, GlyphPriority priority, String csv, String name,
                           boolean reverse, boolean alternateOnce, Runnable onComplete) {
            final long myGeneration;
            synchronized (lock) {
                // Cancel and release whatever was running before, regardless of who owned it.
                interruptCurrentLocked();

                boolean gotIt = StatusManager.acquire(owner, priority, null);
                if (!gotIt) {
                    if (DEBUG) Log.d(TAG, "denied lock for " + name + " at priority " + priority);
                    return;
                }

                myGeneration = ++currentGeneration;
                currentOwner = owner;

                acquireWakeLock(ctx);

                Thread worker = new Thread(() ->
                        runWorker(owner, csv, name, reverse, alternateOnce, onComplete, myGeneration),
                        "anim-stream");
                currentWorker = worker;
                worker.start();
            }
        }

        private void runWorker(Object owner, String csv, String name,
                               boolean reverse, boolean alternateOnce,
                               Runnable onComplete, long myGeneration) {
            try {
                if (!StatusManager.isOwnedBy(owner)) return; // preempted before we even started drawing

                List<int[]> frames = readFrames(csv, name, reverse, alternateOnce);
                if (frames == null || frames.isEmpty()) {
                    Log.w(TAG, "no valid frames for " + name);
                    return;
                }
                if (Thread.currentThread().isInterrupted() || !StatusManager.isOwnedBy(owner)) return;

                NanoGlyphManager.Java.Matrix.playPatternAndAwaitCompletion(
                        frames, 60, result -> {
                            if (!result) Log.w(TAG, "animation ended abnormally");
                            if (onComplete != null) onComplete.run();
                        }
                );
            } catch (Throwable t) {
                Log.e(TAG, "worker failed", t);
            } finally {
                synchronized (lock) {
                    // Only clean up if we're still the current generation — an interrupting
                    // caller already did cleanup for us otherwise.
                    if (currentGeneration == myGeneration) {
                        NanoGlyphManager.Java.Matrix.stop();
                        releaseWakeLock();
                        StatusManager.release(owner);
                        currentWorker = null;
                        currentOwner = null;
                    }
                }
            }
        }

        // must be called while holding `lock`
        private void interruptCurrentLocked() {
            if (currentWorker != null) {
                Log.d(TAG, "cancelling previous animation run");
                currentWorker.interrupt();
                if (currentOwner != null) {
                    NanoGlyphManager.Java.Matrix.stop();
                    releaseWakeLock();
                    StatusManager.release(currentOwner);
                }
                currentGeneration++; 
                currentWorker = null;
                currentOwner = null;
            }
        }

        public void cancelCurrent() {
            synchronized (lock) {
                interruptCurrentLocked();
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
                for (int[] arr : frames) {
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = (applyDimmer(arr[i], Constants.getBrightness()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to read animation " + name, e);
                return null;
            }
            return frames;
        }
    }
}
