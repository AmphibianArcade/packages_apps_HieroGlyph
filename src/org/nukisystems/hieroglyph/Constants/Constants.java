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

package org.nukisystems.hieroglyph.Constants;

import android.content.Context;

import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;

public final class Constants {

    private static final String TAG = "GlyphConstants";
    private static final boolean DEBUG = true;

    public static Context CONTEXT;
    public static final int MAX_PATTERN_BRIGHTNESS = 4095;

    private static String device = null;

    private static int brightness = -1;
    private static int brightnessMax = -1;
    private static int[] brightnessLevels = null;
    private static int[] supportedAnimationPatternLengths = null;
    
    public static final class Settings {

        public static final String PREFIX = "glyph_settings_";

        public static final String GLYPH_ENABLE = "glyph_enable";

        public static final class BatterySaver {
            public static final String ENABLE = PREFIX + "battery_saver_toggle";
        }

        public static final class Brightness {
            public static final String AUTO_BRIGHTNESS_ENABLE = PREFIX + "auto_brightness_toggle";
            public static final String BRIGHTNESS = PREFIX + "brightness";
        }

        public static final class Call {
            public static final String CATEGORY = PREFIX + "call";
            // public static final String TONE_SYNC = PREFIX + "call_ogg_sync_toggle";
            public static final String ENABLE = PREFIX + "call_toggle";
            public static final String REVERSE_ANIMATION_ENABLE = PREFIX + "call_sub_animations_reverse_toggle";
            public static final String SUB_PREVIEW = PREFIX + "call_sub_preview";
            public static final String SUB_ANIMATIONS = PREFIX + "call_sub_animations";
            public static final String SUB_CONTACT_SELECT = PREFIX + "call_sub_contact_select";
            public static final String SUB_LIVE_PREVIEW = PREFIX + "call_sub_animations_live_preview";
            public static final String SUB_ENABLE = PREFIX + "call_sub_toggle";
            public static final String SUB_CATEGORY = PREFIX + "call_sub";

            public static final String CONTACT_PREF_PREFIX = "call_contact_";
            public static final String APP_PREF_PREFIX = "call_";
        }
        
        public static final class Charging {
            public static final String CATEGORY = PREFIX + "charging";
            public static final String LEVEL_ENABLE = PREFIX + "charging_level";
            public static final String POWERSHARE_ENABLE = PREFIX + "charging_powershare";
        }

        public static final class Flip {
            public static final String ENABLE = PREFIX + "flip_toggle";
            public static final String SUB_ENABLE = PREFIX + "flip_sub_toggle";
            public static final String SUB_PREVIEW = PREFIX + "flip_sub_preview";
            public static final String SUB_ANIMATIONS = PREFIX + "flip_sub_animations";
            public static final String SUB_ANIMATION_ENABLE = PREFIX + "flip_sub_animation_toggle";
            public static final String SUB_LIVE_PREVIEW = PREFIX + "flip_sub_live_preview";
            public static final String SUB_RINGER_MODE = PREFIX + "flip_sub_ringer_mode";
            public static final String REVERSE_ANIMATION_ENABLE = PREFIX + "flip_sub_animations_reverse_toggle";
        }

        public static final class Notification {
            public static final String ENABLE = PREFIX + "notifs_toggle";
            public static final String TONE_SYNC = PREFIX + "notifs_ogg_sync_toggle";
            public static final String SUB_PREVIEW = PREFIX + "notifs_sub_preview";
            public static final String SUB_ANIMATIONS = PREFIX + "notifs_sub_animations";
            public static final String SUB_LIVE_PREVIEW = PREFIX + "notifs_sub_animations_live_preview";
            public static final String SUB_ESSENTIAL = PREFIX + "notifs_sub_essential";
            public static final String SUB_CATEGORY = PREFIX + "notifs_sub";
            public static final String SUB_ENABLE = PREFIX + "notifs_sub_toggle";
            public static final String REVERSE_ANIMATION_ENABLE = PREFIX + "notifs_sub_animations_reverse_toggle";

            public static final String ANIMATION_ALTERNATE = "notif_alternate";
            public static final String APP_PREF_PREFIX = "notif_";
        }

        public static final class Progress {
            public static final String ENABLE = PREFIX + "progress_toggle";
            public static final String MEDIA_ENABLE = PREFIX + "progress_media_toggle";
            public static final String MEDIA_WHITELIST = PREFIX + "progress_media_app_whitelist";
            public static final String CATEGORY = PREFIX + "progress";
        }

        public static final class RedLED {
            public static final String CATEGORY = PREFIX + "red_led";
            public static final String MODE = PREFIX + "red_led_mode";
            public static final String MIC_ACTIVITY_ENABLE = PREFIX + "mic_activity_toggle";
            public static final String MIC_ACTIVITY_WHITELIST = PREFIX + "mic_activity_whitelist";
        }

        public static final class Schedule {
            public static final String GLYPH_SCHEDULE = PREFIX + "schedule";
        }

        public static final class Utilities {
            public static final String UTILITIES = PREFIX + "utilities";
            public static final String VALIDATE_CSV = "glyph_utilities_validate_csv";
            public static final String READ_OGG = "glyph_utilities_read_ogg";
            public static final String OGG_EXPORT_CSV = "glyph_utilities_export_csv_from_ogg";
            public static final String OGG_LIVE_PREVIEW = "glyph_utilities_ogg_live_preview";
        }

        public static final class Volume {
            public static final String CATEGORY = PREFIX + "volume";
            public static final String LEVEL_ENABLE = PREFIX + "volume_level_toggle";

        }
    }

    public static final String ACTION_TORCH_ENABLE = "torch_enable";
    public static final String ACTION_TORCH_DISABLE = "torch_disable";

    public static final String GLYPH_USER_NOTIF_CSV_PATH = "Glyph/Notifications";
    public static final String GLYPH_USER_CALL_CSV_PATH = "Glyph/Call";

    public static final String GLYPH_USER_CALL_CSV_PREFIX = "user_call_";
    public static final String GLYPH_USER_NOTIF_CSV_PREFIX = "user_notif_";

    public static class Device {

        public static final String PHONE4A_PRO = "phone4apro";
        public static final String PHONE3 = "phone3";

        public static String getDevice() {
            if (device == null) device = ResourceUtils.getString(Settings.PREFIX + "device");
            return device;
        }

        public static boolean isPhone4aPro() {
            return getDevice().equals(PHONE4A_PRO);
        }

        public static boolean isPhone3() {
            return getDevice().equals(PHONE3);
        }
    }

    public static class Res  {
        public static String INT_ARRAY_MATRIX_ROWS = "glyph_matrix_row_leds";
        public static String INT_ARRAY_BRIGHTNESS_LEVELS = "glyph_matrix_brightness_levels";
        public static String INT_ARRAY_AUTO_BRIGHTNESS_LEVELS = "glyph_auto_brightness_levels";
        
        public static String INT_BRIGHTNESS_MAX = Settings.PREFIX + "brightness_max";
        public static String INT_GLYPH_BUTTON_SCANCODE = "glyph_button_scancode";

        public static String STRING_POWERSHARE_STATUS_PATH = Settings.PREFIX + "paths_powershare_active_absolute";
        public static String STRING_POWERSHARE_ENABLED_PATH = Settings.PREFIX + "paths_powershare_enabled_absolute";
        public static String STRING_LIGHT_SENSOR = "glyph_light_sensor";
    }
    
    public static final String[] APPS_TO_IGNORE = {
        "android",
        "com.android.traceur",
        "com.google.android.setupwizard",
        "dev.kdrag0n.dyntheme.privileged.sys"
    };
    
    public static final String[] NOTIFS_TO_IGNORE = {
        "com.google.android.dialer:phone_incoming_call",
        "com.google.android.dialer:phone_ongoing_call",
        "com.android.systemui:BAT"
    };

    public static boolean isPowershareSupported() {
       return !ResourceUtils.getString(Res.STRING_POWERSHARE_STATUS_PATH).isEmpty();
    }

    public static boolean setBrightness(int b) {
        if (b > getMaxBrightness())
            return false;

        brightness = b;
        return true;
    }

    public static int getBrightness() {
        if (brightness == -1)
            brightness = getMaxBrightness();

        return brightness;
    }

    public static int getMaxBrightness() {
        if (brightnessMax == -1)
            brightnessMax = ResourceUtils.getInteger(Res.INT_BRIGHTNESS_MAX);

        return brightnessMax;
    }

    public static int[] getBrightnessLevels() {
        if (brightnessLevels == null)
            brightnessLevels = ResourceUtils.getIntArray(Res.INT_ARRAY_BRIGHTNESS_LEVELS);

        return brightnessLevels;
    }

    public static int[] getSupportedAnimationPatternLengths() {
        if (supportedAnimationPatternLengths == null) {
            supportedAnimationPatternLengths = 
                new int[]{MatrixUtils.getMinFrameLength(), MatrixUtils.getMaxFrameLength()};
        }

        return supportedAnimationPatternLengths;
    }

}
