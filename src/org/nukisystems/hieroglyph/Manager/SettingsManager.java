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

import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import com.android.internal.util.ArrayUtils;

import java.util.HashSet;
import java.util.Set;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;

public final class SettingsManager {

    private static final String TAG = "GlyphSettingsManager";
    private static final boolean DEBUG = true;
    private static Context context;

    private static Context getContext() {
        if (context == null) {
            if (Constants.CONTEXT == null) {
                throw new IllegalStateException("Constants.CONTEXT is not initialized");
            }
            context = Constants.CONTEXT;
        }
        return context;
    }

    public static boolean enableGlyph(boolean enable) {
        Context ctx = getContext();
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putBoolean(Constants.Settings.GLYPH_ENABLE, enable).apply();

        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.Settings.GLYPH_ENABLE, enable ? 1 : 0);
    }

    public static boolean isGlyphEnabled() {
        Context ctx = getContext();
        boolean baseEnabled = (Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.Settings.GLYPH_ENABLE, 0) != 0
            || PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.GLYPH_ENABLE, false));
        
        if (GlyphScheduleManager.isScheduleEnabled(ctx) && 
            GlyphScheduleManager.isScheduleCurrentlyActive(ctx)) {
            return false;
        }
        
        return baseEnabled;
    }

    public static boolean isGlyphEnabledIgnoreSchedule() {
        Context ctx = getContext();
        return (Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.Settings.GLYPH_ENABLE, 0) != 0
            || PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.GLYPH_ENABLE, false));
    }

    public static boolean isGlyphFlipEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.Settings.Flip.ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static void setGlyphFlipEnabled(boolean enable) {
        Context ctx = getContext();
        Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.Settings.Flip.ENABLE, enable ? 1 : 0);
    }

    public static boolean isGlyphFlipAnimationEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Flip.SUB_ANIMATION_ENABLE, true) && isGlyphEnabled();
    }

    public static int getGlyphBrightness() {
        int[] levels = Constants.getBrightnessLevels();
        int brightnessSetting = getGlyphBrightnessSetting();
        return levels[brightnessSetting - 1];
    }

    public static int getGlyphBrightnessSetting() {
        Context ctx = getContext();
        int d = 3;
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getInt(Constants.Settings.Brightness.BRIGHTNESS, d);
    }

    public static boolean isGlyphBatterySaverEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.BatterySaver.ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphChargingEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Charging.LEVEL_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphPowershareEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Charging.POWERSHARE_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphCallEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.Settings.Call.ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static boolean isGlyphCallEnabled(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Call.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.Settings.Call.ENABLE, true);
    }

    public static boolean setGlyphCallEnabled(boolean enable) {
        Context ctx = getContext();
        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.Settings.Call.ENABLE, enable ? 1 : 0);
    }

    public static void setGlyphCallEnabled(String pkg, boolean enable) {
        Context ctx = getContext();
        ctx.getSharedPreferences(Constants.Settings.Call.APP_PREF_PREFIX + pkg
                        , Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.Settings.Call.ENABLE, enable)
                .apply();
    }

    public static boolean contactHasGlyphCallConfig(int contactId) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(Constants.Settings.Call.CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getAll().isEmpty();
    }

    public static String getGlyphCallAnimation() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.Settings.Call.SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static String getGlyphCallAnimation(int contactId) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Call.CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getString(Constants.Settings.Call.SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static boolean appHasGlyphCallConfig(String pkg) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.Settings.Call.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphCallAnimationReversed(pkg)
                || !getGlyphCallAnimation(pkg).equals(getGlyphCallAnimation()));
    }

    public static boolean appHasGlyphCallConfig(String pkg, String comp) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.Settings.Call.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphCallAnimationReversed(pkg)
                || !getGlyphCallAnimation(pkg).equals(comp));
    }


    public static String getGlyphCallAnimation(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Call.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getString(Constants.Settings.Call.SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

//    public static boolean isGlyphRingtoneSyncEnabled() {
//        Context ctx = getContext();
//        return PreferenceManager.getDefaultSharedPreferences(ctx)
//                .getBoolean(Constants.Settings.Call.TONE_SYNC, false);
//    }

    public static boolean isGlyphNotifsSyncEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Notification.TONE_SYNC, false);
    }

    public static String getGlyphFlipAnimation() {
        Context ctx = getContext();
        String defaultValue = ResourceUtils.hasFlipCsv() ? "flip" : Constants.Settings.Notification.ANIMATION_ALTERNATE;

        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.Settings.Flip.SUB_ANIMATIONS,
                        defaultValue);
    }

    public static boolean isGlyphFlipAnimationReversed() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Flip.REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed() {
        Context ctx = getContext();
         return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Call.REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed(int contactId) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Call.CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getBoolean(Constants.Settings.Call.REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Call.APP_PREF_PREFIX
                        + pkg, Context.MODE_PRIVATE)
                .getBoolean(Constants.Settings.Call.REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static class Volume {

        public static boolean isEnabled() {
            Context ctx = getContext();
            return PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getBoolean(Constants.Settings.Volume.LEVEL_ENABLE, false) && isGlyphEnabled();
        }

        public static int getStyle() {
            Context ctx = getContext();
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(Constants.Settings.Volume.SUB_STYLE, "0"));
        }

        public static int getRotation() {
            Context ctx = getContext();
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(Constants.Settings.Volume.SUB_ROTATION, "0"));
        }

        public static boolean showCrossWhenEmpty() {
            Context ctx = getContext();
            return PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getBoolean(Constants.Settings.Volume.SUB_SHOW_CROSS, true);
        }

        public static void setEnabled(boolean enable) {
            Context ctx = getContext();
            PreferenceManager.getDefaultSharedPreferences(ctx)
                    .edit()
                    .putBoolean(Constants.Settings.Volume.LEVEL_ENABLE, enable)
                    .apply();
        }
    }

    public static boolean isGlyphNotifsEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.Settings.Notification.ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static boolean appHasGlyphNotifsConfig(String pkg) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                Constants.Settings.Notification.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphNotifsAnimationReversed(pkg)
                || !getGlyphNotifsAnimation(pkg).equals(getGlyphNotifsAnimation()));
    }

    public static boolean appHasGlyphNotifsConfig(String pkg, String comp) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.Settings.Notification.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphNotifsAnimationReversed(pkg)
                || !getGlyphNotifsAnimation(pkg).equals(comp));
    }

    public static boolean isGlyphNotifsEnabled(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Notification.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.Settings.Notification.SUB_ENABLE, true);
    }

    public static void setGlyphNotifsEnabled(boolean enable) {
        Context ctx = getContext();
        Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.Settings.Notification.ENABLE, enable ? 1 : 0);
    }

    public static void setGlyphNotifsEnabled(String pkg, boolean enable) {
        Context ctx = getContext();
        ctx.getSharedPreferences(Constants.Settings.Notification.APP_PREF_PREFIX + pkg
                        , Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.Settings.Notification.SUB_ENABLE, enable)
                .apply();
    }

    public static String getGlyphNotifsAnimation() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.Settings.Notification.SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static String getGlyphNotifsAnimation(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Notification.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getString(Constants.Settings.Notification.SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static boolean isGlyphNotifsAnimationReversed(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.Settings.Notification.APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.Settings.Notification.REVERSE_ANIMATION_ENABLE, false);
    }

    public static boolean isGlyphNotifsAnimationReversed() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Notification.REVERSE_ANIMATION_ENABLE, false);
    }

    public static boolean isGlyphNotifsAppEssential(String app) {
        Context ctx = getContext();
        Set<String> selectedValues = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.Settings.Notification.SUB_ESSENTIAL , new HashSet<String>());
        return selectedValues.contains(app) && isGlyphNotifsEnabled();
    }

    public static boolean isGlyphAutoBrightnessEnabled() {
        Context ctx = getContext();
        return !ResourceUtils.getString("glyph_light_sensor").isBlank() 
            && PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(Constants.Settings.Brightness.AUTO_BRIGHTNESS_ENABLE, false)
            && isGlyphEnabled();
    }

    public static int getFlipRingerMode() {
        Context ctx = getContext();
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.Settings.Flip.SUB_RINGER_MODE,
                        String.valueOf(AudioManager.RINGER_MODE_VIBRATE)));
    }

    public static boolean isGlyphProgressEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Progress.ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphProgressMediaEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.Progress.MEDIA_ENABLE, false) && isGlyphProgressEnabled();
    }

    public static boolean isMediaPackageWhitelisted(String name) {
        Context ctx = getContext();
        Set<String> pkgList = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.Settings.Progress.MEDIA_WHITELIST, new HashSet<>());
        return pkgList.contains(name);
    }

    public static boolean isGlyphMicActivityEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.Settings.RedLED.MIC_ACTIVITY_ENABLE, false) && isGlyphEnabled();
    }

    public static int getGlyphRedLedMode() {
        Context ctx = getContext();
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.Settings.RedLED.MODE, "0"));
    }

    public static Set<String> getMonitoredMicApps() {
        Context ctx = getContext();
        Set<String> pkgList = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.Settings.RedLED.MIC_ACTIVITY_WHITELIST, new HashSet<>());
        return pkgList;
    }

    public static void setIntSecure(String key, boolean state) {
        Context ctx = getContext();
        int currentUser = ActivityManager.getCurrentUser();
        Settings.Secure.putIntForUser(ctx.getContentResolver(),
                key, state ? 1 : 0, currentUser);
    }
}
