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

import org.nukisystems.hieroglyph.Utils.MatrixUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

public final class StatusManager {

    private static final String TAG = "GlyphStatusManager";
    private static final boolean DEBUG = true;

    private static boolean batterySavingActive = false;
    private static int progressType = 0;
    private static int[] volumeArray;
    private static int[] progressArray;

    public enum GlyphPriority {
        TORCH(100),
        PREVIEW(90),
        CONTACT_MARQUEE(85),
        VOLUME(70),
        FLIP(60),
        CALL(45),
        NOTIFICATION(40),
        POWER(40),
        THIRD_PARTY(35),
        TOY(35),
        PROGRESS(20),
        ESSENTIAL(15),
        AOD(10),
        IDLE(0);

        public final int level;
        GlyphPriority(int level) { this.level = level; }
    }

    public interface GlyphOwner {
        void onSuspended();
        void onActivated();
    }

    private static final ReentrantLock lock = new ReentrantLock();

    private static final TreeMap<RequestKey, GlyphOwner> requests = new TreeMap<>();
    private static final Map<Object, RequestKey> keysByOwner = new HashMap<>();
    private static long sequence = 0;

    private static RequestKey activeKey = null;

    private record RequestKey(int priorityLevel, long seq, Object owner)
            implements Comparable<RequestKey> {
        public int compareTo(RequestKey o) {
            int c = Integer.compare(o.priorityLevel, this.priorityLevel);
            if (c != 0) return c;
            return Long.compare(this.seq, o.seq);
        }
    }

    private static final GlyphOwner NOOP = new GlyphOwner() {
        public void onSuspended() {}
        public void onActivated() {}
    };

    public static boolean acquire(Object owner, GlyphPriority priority, GlyphOwner callback) {
        lock.lock();
        try {
            GlyphOwner cb = (callback != null) ? callback : NOOP;

            RequestKey existing = keysByOwner.remove(owner);
            if (existing != null) requests.remove(existing);

            RequestKey key = new RequestKey(priority.level, sequence++, owner);
            requests.put(key, cb);
            keysByOwner.put(owner, key);

            recomputeActive();
            return activeKey != null && activeKey.owner() == owner;
        } finally {
            lock.unlock();
        }
    }

    public static void release(Object owner) {
        lock.lock();
        try {
            RequestKey key = keysByOwner.remove(owner);
            if (key == null) return;
            requests.remove(key);
            recomputeActive();
        } finally {
            lock.unlock();
        }
    }

    public static boolean isOwnedBy(Object owner) {
        lock.lock();
        try {
            return activeKey != null && activeKey.owner() == owner;
        } finally {
            lock.unlock();
        }
    }

    public static boolean isPriorityActive(GlyphPriority priority) {
        lock.lock();
        try {
            return activeKey != null && activeKey.priorityLevel() == priority.level;
        } finally {
            lock.unlock();
        }
    }

    public static boolean isAtLeastActive(GlyphPriority priority) {
        lock.lock();
        try {
            return activeKey != null && activeKey.priorityLevel() >= priority.level;
        } finally {
            lock.unlock();
        }
    }

    private static void recomputeActive() {
        RequestKey newTop = requests.isEmpty() ? null : requests.firstKey();
        if (Objects.equals(newTop, activeKey)) return;

        if (activeKey != null) {
            GlyphOwner prevCb = requests.get(activeKey);
            // prevCb may be null if it was removed already (released while active)
            if (prevCb != null) prevCb.onSuspended();
        }
        activeKey = newTop;
        if (activeKey != null) {
            requests.get(activeKey).onActivated();
        }
    }

    public static boolean tryAcquireNow(Object owner, GlyphPriority priority) {
        lock.lock();
        try {
            RequestKey existing = keysByOwner.remove(owner);
            if (existing != null) requests.remove(existing);

            RequestKey key = new RequestKey(priority.level, sequence++, owner);
            requests.put(key, new GlyphOwner() {
                public void onSuspended() {}
                public void onActivated() {}
            });
            keysByOwner.put(owner, key);

            boolean won = requests.firstKey().equals(key);

            if (!won) {
                requests.remove(key);
                keysByOwner.remove(owner);
                return false;
            }

            recomputeActive();
            return true;
        } finally {
            lock.unlock();
        }
    }


    public static boolean isBatterySavingActive() {
        return batterySavingActive;
    }

    public static void setBatterySavingActive(boolean status) {
        batterySavingActive = status;
    }

    public static int[] getVolumeArray() {
        if (volumeArray == null) {
            volumeArray =  new int[MatrixUtils.getMinFrameLength()];
        }
        return volumeArray;
    }

    public static void setVolumeArray(int[] volumeArrayNext) {
        volumeArray = volumeArrayNext;
    }


    public static int getProgressType() {
        return progressType;
    }

    public static void setProgressType(int type) {
        progressType = type;
    }

    public static int[] getProgressArray() {
        if (progressArray == null) {
            progressArray = new int[MatrixUtils.getMinFrameLength()];
        }
        return progressArray;
    }

    public static void setProgressArray(int[] progressArrayNext) {
        progressArray = progressArrayNext;
    }


    public static boolean isAnythingActive() {
        lock.lock();
        try {
            return activeKey != null;
        } finally {
            lock.unlock();
        }
    }
}
