/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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

package org.nukisystems.hieroglyph.Utils;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Manager.StatusManager;
import org.nukisystems.hieroglyph.Services.*;

import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public final class ServiceUtils {

    private static final String TAG = "GlyphServiceUtils";
    private static final boolean DEBUG = true;
    private static Context context;

    private static Context getContext() {
        if (context == null) {
            context = Constants.CONTEXT;
            if (context == null) {
                throw new IllegalStateException("Constants.CONTEXT is not initialized");
            }
        }
        return context;
    }

    public static boolean isNotificationServiceEnabled(Context ctx) {
        String pkgName = ctx.getPackageName();
        final String flat = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        if (flat != null) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void startCallReceiverService() {
        if (DEBUG) Log.d(TAG, "Starting Glyph call receiver service");
        getContext().startServiceAsUser(new Intent(getContext(), CallReceiverService.class),
                UserHandle.CURRENT);
    }

    private static void startToneHelperService() {
        if (DEBUG) Log.d(TAG, "Starting Tone helper service");
        getContext().startServiceAsUser(new Intent(getContext(), ToneHelperService.class),
                UserHandle.CURRENT);
    }

    private static void stopToneHelperService() {
        if (DEBUG) Log.d(TAG, "Stopping Tone helper service");
        getContext().stopServiceAsUser(new Intent(getContext(), ToneHelperService.class),
                UserHandle.CURRENT);
    }

    private static void startBatterySaverService(){
        if (DEBUG) Log.d(TAG, "Starting Glyph battery saver service");
        getContext().startServiceAsUser(new Intent(getContext(), BatterySaverService.class),
                UserHandle.CURRENT);
    }

    private static void stopBatterySaverService(){
        if (DEBUG) Log.d(TAG, "Starting Glyph battery saver service");
        getContext().stopServiceAsUser(new Intent(getContext(), BatterySaverService.class),
                UserHandle.CURRENT);
    }

    private static void stopCallReceiverService() {
        if (DEBUG) Log.d(TAG, "Stopping Glyph call receiver service");
        getContext().stopServiceAsUser(new Intent(getContext(), CallReceiverService.class),
                UserHandle.CURRENT);
    }

    private static void startChargingService() {
        if (DEBUG) Log.d(TAG, "Starting Glyph charging service");
        getContext().startServiceAsUser(new Intent(getContext(), ChargingService.class),
                UserHandle.CURRENT);
    }

    private static void stopChargingService() {
        if (DEBUG) Log.d(TAG, "Stopping Glyph charging service");
        getContext().stopServiceAsUser(new Intent(getContext(), ChargingService.class),
                UserHandle.CURRENT);
    }

    private static void startFlipToGlyphService() {
        if (DEBUG) Log.d(TAG, "Starting Flip to Glyph service");
        getContext().startServiceAsUser(new Intent(getContext(), FlipToGlyphService.class),
                UserHandle.CURRENT);
    }

    private static void stopFlipToGlyphService() {
        if (DEBUG) Log.d(TAG, "Stopping Flip to Glyph service");
        getContext().stopServiceAsUser(new Intent(getContext(), FlipToGlyphService.class),
                UserHandle.CURRENT);
    }

    private static void startPowershareService() {
        if (Constants.isPowershareSupported()) {
            if (DEBUG) Log.d(TAG, "Starting Glyph powershare service");
            getContext().startServiceAsUser(new Intent(getContext(), PowershareService.class),
                    UserHandle.CURRENT);
        }
    }

    private static void stopPowershareService() {
        if (Constants.isPowershareSupported()) {
            if (DEBUG) Log.d(TAG, "Stopping Glyph powershare service");
            getContext().stopServiceAsUser(new Intent(getContext(), PowershareService.class),
                    UserHandle.CURRENT);
        }
    }

    public static void startVolumeLevelService() {
        if (DEBUG) Log.d(TAG, "Starting Volume Level service");
        getContext().startServiceAsUser(new Intent(getContext(), VolumeLevelService.class),
                UserHandle.CURRENT);
    }

    protected static void stopVolumeLevelService() {
        if (DEBUG) Log.d(TAG, "Stopping Volume Listener service");
        getContext().stopServiceAsUser(new Intent(getContext(), VolumeLevelService.class),
                UserHandle.CURRENT);
    }

    private static void startAutoBrightnessService() {
        if (DEBUG) Log.d(TAG, "Starting Auto Brightness service");
        getContext().startServiceAsUser(new Intent(getContext(), AutoBrightnessService.class),
                UserHandle.CURRENT);
    }

    private static void stopAutoBrightnessService() {
        if (DEBUG) Log.d(TAG, "Stopping Auto Brightness service");
        getContext().stopServiceAsUser(new Intent(getContext(), AutoBrightnessService.class),
                UserHandle.CURRENT);
    }

    public static void startThirdPartyService() {
        if (DEBUG) Log.d(TAG, "Starting ThirdParty service");
        getContext().startServiceAsUser(new Intent(getContext(), ThirdPartyService.class),
                UserHandle.CURRENT);
    }

    protected static void stopThirdPartyService() {
        if (DEBUG) Log.d(TAG, "Stopping ThirdParty service");
        getContext().stopServiceAsUser(new Intent(getContext(), ThirdPartyService.class),
                UserHandle.CURRENT);
    }

    public static void startProgressService() {
        if (DEBUG) Log.d(TAG, "Starting Progress service");
        getContext().startServiceAsUser(new Intent(getContext(), ProgressService.class),
                UserHandle.CURRENT);
    }

    public static void stopProgressService() {
        if (DEBUG) Log.d(TAG, "Stopping Progress service");
        getContext().stopServiceAsUser(new Intent(getContext(), ProgressService.class),
                UserHandle.CURRENT);
    }

    public static void startMicActivityService() {
        if (DEBUG) Log.d(TAG, "Starting Mic activity service");
        getContext().startServiceAsUser(new Intent(getContext(), MicActivityService.class),
                UserHandle.CURRENT);
    }

    public static void stopMicActivityService() {
        if (DEBUG) Log.d(TAG, "Starting Mic activity service");
        getContext().stopServiceAsUser(new Intent(getContext(), MicActivityService.class),
                UserHandle.CURRENT);
    }

    public static void startTorchService() {
        if (DEBUG) Log.d(TAG, "Starting Torch service");
        getContext().startServiceAsUser(new Intent(getContext(), TorchService.class),
                UserHandle.CURRENT);
    }

    public static void stopTorchService() {
        if (DEBUG) Log.d(TAG, "Stopping Torch service");
        getContext().stopServiceAsUser(new Intent(getContext(), TorchService.class),
                UserHandle.CURRENT);
    }

    public static void checkGlyphService(boolean checkNano) {

        boolean glyphEnabled = SettingsManager.isGlyphEnabled();
        boolean glyphBaseEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();

        if ((checkNano && !NanoGlyphManager.Java.tryConnect(150))|| StatusManager.isBatterySavingActive()) {
            stopGlyphServices();
            return;
        }

        if (SettingsManager.getGlyphBrightness() != Constants.getBrightness()) {
            Constants.setBrightness(SettingsManager.getGlyphBrightness());
            startThirdPartyService();
            // if (StatusManager.isEssentialLedActive()) AnimationManager.playEssential();
        }
        
        if (glyphBaseEnabled) {
            startThirdPartyService();
            startBatterySaverService();
        } else {
            stopThirdPartyService();
            stopBatterySaverService();
        }

        if (glyphEnabled) {
            MatrixUtils.init();

            if (SettingsManager.isGlyphChargingEnabled()) {
                startChargingService();
            } else {
                stopChargingService();
            }
            if (SettingsManager.isGlyphPowershareEnabled()) {
                startPowershareService();
            } else {
                stopPowershareService();
            }
            if (SettingsManager.isGlyphMicActivityEnabled()) {
                startMicActivityService();
            } else {
                stopMicActivityService();
            }
            if (SettingsManager.isGlyphFlipEnabled()) {
                startFlipToGlyphService();
            } else {
                stopFlipToGlyphService();
            }
            if (SettingsManager.isGlyphNotifsEnabled()
                    && SettingsManager.isGlyphNotifsSyncEnabled()) {
                startToneHelperService();
            } else {
                stopToneHelperService();
            }
            if (SettingsManager.isGlyphVolumeLevelEnabled()) {
                startVolumeLevelService();
            } else {
                stopVolumeLevelService();
            }
            if (SettingsManager.isGlyphAutoBrightnessEnabled()) {
                startAutoBrightnessService();
            } else {
                stopAutoBrightnessService();
            }
            if (SettingsManager.isGlyphProgressEnabled()) {
                startProgressService();
            } else {
                stopProgressService();
            }
        } else {
            stopGlyphServices();
        }
    }

    public static void checkGlyphService() {
        checkGlyphService(false);
    }

    public static void stopGlyphServices() {
        stopChargingService();
        stopPowershareService();
        stopCallReceiverService();
        stopToneHelperService();
        stopMicActivityService();
        stopFlipToGlyphService();
        stopVolumeLevelService();
        stopAutoBrightnessService();
        stopProgressService();
    }
}
