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

package org.nukisystems.hieroglyph.Settings;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SliderPreference;

import android.util.Log;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.Manager.StatusManager;
import org.nukisystems.hieroglyph.R;
import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.GlyphScheduleManager;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Services.BatterySaverService;

import static org.nukisystems.hieroglyph.Utils.InterfaceUtils.Preferences.getAllPreferences;
import static org.nukisystems.hieroglyph.Utils.InterfaceUtils.showDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.nukisystems.hieroglyph.Manager.StatusManager.GlyphPriority;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.Utils.ServiceUtils;
import org.nukisystems.hieroglyph.aidl.NanoGlyphManager;

public class SettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener, StatusManager.GlyphOwner {

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mBatterySaverPreference;

    private PrimarySwitchPreference mFlipPreference;
    private SwitchPreferenceCompat mAutoBrightnessPreference;
    private SliderPreference mBrightnessPreference;
    private PrimarySwitchPreference mNotifsPreference;
    private PrimarySwitchPreference mCallPreference;
    private PreferenceCategory mChargingCategory;
    private SwitchPreferenceCompat mChargingLevelPreference;
    private SwitchPreferenceCompat mChargingPowersharePreference;
    private PreferenceCategory mVolumeCategory;
    private PrimarySwitchPreference mVolumeLevelPreference;
    private PreferenceCategory mProgressCategory;
    private SwitchPreferenceCompat mProgressPreference;
    private SwitchPreferenceCompat mProgressMediaPreference;
    private MultiSelectListPreference mProgressMediaWhitelistPreference;
    private PreferenceCategory mRedLedCategory;
    private SwitchPreferenceCompat mMicActivityPreference;
    private MultiSelectListPreference mMicActivityWhitelistPreference;
    private ListPreference mRedLedModePreference;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;
    private Preference mSchedulePreference;

    private static final long BRIGHTNESS_PREVIEW_TIMEOUT_MS = 3000;
    private final Runnable mStopBrightnessPreview = () -> {
        AnimationManager.clearLEDs();
        StatusManager.release(this);
    };

    private Preference mUtilitiesPreference;

    private CompletableFuture<Boolean> pendingConfirmation;

    private Handler mHandler = new Handler();

    private Context context;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    String[] mediaPermissions = {
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
    };

    String[] micPermissions = {
            Manifest.permission.RECORD_AUDIO
    };

    private BroadcastReceiver mScheduleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("org.nukisystems.hieroglyph.UPDATE_MAIN_SWITCH".equals(intent.getAction())) {
                updateMainSwitchState();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        context = getPreferenceManager().getContext();

        addPreferencesFromResource(R.xml.glyph_settings);

        mHandler.post(() -> {
            File callAnimPath
                    = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_CALL_CSV_PATH);
            File notifAnimPath
                    = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_NOTIF_CSV_PATH);
            if (!callAnimPath.exists()) callAnimPath.mkdirs();
            if (!notifAnimPath.exists()) notifAnimPath.mkdirs();
        });

        mContentResolver = getActivity().getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);

        boolean glyphEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.Settings.GLYPH_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(glyphEnabled);

        mBatterySaverPreference = (SwitchPreferenceCompat) findPreference(Constants.Settings.BatterySaver.ENABLE);
        mBatterySaverPreference.setOnPreferenceChangeListener(this);

        mFlipPreference = findPreference(Constants.Settings.Flip.ENABLE);
        mFlipPreference.setSwitchEnabled(glyphEnabled);
        mFlipPreference.setEnabled(glyphEnabled);
        mFlipPreference.setChecked(SettingsManager.isGlyphFlipEnabled());
        mFlipPreference.setOnPreferenceChangeListener(this);

        mAutoBrightnessPreference = (SwitchPreferenceCompat) findPreference(Constants.Settings.Brightness.AUTO_BRIGHTNESS_ENABLE);
        mAutoBrightnessPreference.setEnabled(glyphEnabled);
        mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        mAutoBrightnessPreference.setChecked(SettingsManager.isGlyphAutoBrightnessEnabled());
        if (ResourceUtils.getString("glyph_light_sensor").isBlank()) {
            getPreferenceScreen().removePreference(mAutoBrightnessPreference);
        }

        mBrightnessPreference = (SliderPreference) findPreference(Constants.Settings.Brightness.BRIGHTNESS);
        if (mAutoBrightnessPreference.isChecked()) {
            mBrightnessPreference.setEnabled(false);
        } else {
            mBrightnessPreference.setEnabled(glyphEnabled);
        }
        mBrightnessPreference.setMin(1);
        mBrightnessPreference.setMax(Constants.getBrightnessLevels().length);
        mBrightnessPreference.setValue(SettingsManager.getGlyphBrightnessSetting());
        mBrightnessPreference.setUpdatesContinuously(true);
        mBrightnessPreference.setSliderIncrement(1);
        mBrightnessPreference.setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        mBrightnessPreference.setTickVisible(true);
        mBrightnessPreference.setOnPreferenceChangeListener(this);

        mNotifsPreference = (PrimarySwitchPreference) findPreference(Constants.Settings.Notification.ENABLE);
        mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled()
                && checkNotificationService(false, null));
        mNotifsPreference.setEnabled(glyphEnabled);
        mNotifsPreference.setSwitchEnabled(glyphEnabled
                && checkNotificationService(false, null));
        mNotifsPreference.setOnPreferenceChangeListener(this);

        mCallPreference = (PrimarySwitchPreference) findPreference(Constants.Settings.Call.ENABLE);
        mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
        mCallPreference.setEnabled(glyphEnabled);
        mCallPreference.setSwitchEnabled(glyphEnabled);
        mCallPreference.setOnPreferenceChangeListener(this);

        mChargingCategory = (PreferenceCategory) findPreference(Constants.Settings.Charging.CATEGORY);
        mChargingLevelPreference = (SwitchPreferenceCompat)
                findPreference(Constants.Settings.Charging.LEVEL_ENABLE);

        mChargingLevelPreference.setEnabled(glyphEnabled);
        mChargingLevelPreference.setOnPreferenceChangeListener(this);

        mChargingPowersharePreference = (SwitchPreferenceCompat) findPreference(Constants.Settings.Charging.POWERSHARE_ENABLE);

        if (Constants.isPowershareSupported()) {
           mChargingPowersharePreference.setEnabled(glyphEnabled);
           mChargingPowersharePreference.setOnPreferenceChangeListener(this);
        } else {
           mChargingPowersharePreference.setVisible(false);
        }

        mVolumeCategory = findPreference(Constants.Settings.Volume.CATEGORY);

        mVolumeLevelPreference = (PrimarySwitchPreference) findPreference(Constants.Settings.Volume.LEVEL_ENABLE);
        mVolumeLevelPreference.setEnabled(glyphEnabled);
        mVolumeLevelPreference.setOnPreferenceChangeListener(this);

        mSchedulePreference = (Preference) findPreference(Constants.Settings.Schedule.GLYPH_SCHEDULE);
        updateScheduleSummary();

        mProgressCategory = findPreference(Constants.Settings.Progress.CATEGORY);

        mProgressPreference = (SwitchPreferenceCompat) findPreference(Constants.Settings.Progress.ENABLE);
        mProgressPreference.setEnabled(glyphEnabled);
        mProgressPreference.setOnPreferenceChangeListener(this);

        mProgressMediaPreference = (SwitchPreferenceCompat) findPreference(Constants.Settings.Progress.MEDIA_ENABLE);
        mProgressMediaPreference.setEnabled(glyphEnabled && mProgressPreference.isChecked());
        mProgressMediaPreference.setOnPreferenceChangeListener(this);

        mProgressMediaWhitelistPreference = findPreference(Constants.Settings.Progress.MEDIA_WHITELIST);
        mProgressMediaWhitelistPreference.setEntries(
                ResourceUtils.getApplicationsWithPermission(true, mediaPermissions));
        mProgressMediaWhitelistPreference.setEntryValues(
                ResourceUtils.getApplicationsWithPermission(false, mediaPermissions));

        mRedLedCategory = findPreference(Constants.Settings.RedLED.CATEGORY);

        mMicActivityPreference = findPreference(Constants.Settings.RedLED.MIC_ACTIVITY_ENABLE);
        mMicActivityPreference.setOnPreferenceChangeListener(this);
        mMicActivityPreference.setEnabled(glyphEnabled);

        mMicActivityWhitelistPreference = findPreference(Constants.Settings.RedLED.MIC_ACTIVITY_WHITELIST);
        mMicActivityWhitelistPreference.setEntries(
                ResourceUtils.getApplicationsWithPermission(true, micPermissions));
        mMicActivityWhitelistPreference.setEntryValues(
                ResourceUtils.getApplicationsWithPermission(false, micPermissions));
        mMicActivityWhitelistPreference.setOnPreferenceChangeListener(this);
        mMicActivityWhitelistPreference.setEnabled(glyphEnabled);

        mRedLedModePreference = findPreference(Constants.Settings.RedLED.MODE);
        mRedLedModePreference.setOnPreferenceChangeListener(this);
        mRedLedModePreference.setEnabled(glyphEnabled);

        mUtilitiesPreference = findPreference(Constants.Settings.Utilities.UTILITIES);

        IntentFilter filter = new IntentFilter("org.nukisystems.hieroglyph.UPDATE_MAIN_SWITCH");
        requireContext().registerReceiver(mScheduleUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        tryNanoGlyph();
        updatePrimarySwitches();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        switch (preferenceKey) {
            case Constants.Settings.Flip.ENABLE -> {
                SettingsManager.setGlyphFlipEnabled((Boolean) newValue);
            }
            case Constants.Settings.Call.ENABLE -> {
                SettingsManager.setGlyphCallEnabled((Boolean) newValue);
            }
            case Constants.Settings.Notification.ENABLE -> {
                if (!checkNotificationService(true, preference)
                        && (Boolean) newValue) {
                    mHandler.post(() -> mNotifsPreference.setChecked(false));
                    return false;
                }
                SettingsManager.setGlyphNotifsEnabled((Boolean) newValue);
            }
            case Constants.Settings.Brightness.AUTO_BRIGHTNESS_ENABLE -> {
                mBrightnessPreference.setEnabled(!(Boolean) newValue);
            }
            case Constants.Settings.Brightness.BRIGHTNESS -> {
                if (SettingsManager.isGlyphEnabled()) {
                    int settingValue = (Integer) newValue;
                    int[] levels = Constants.getBrightnessLevels();
                    int rawBrightness = levels[settingValue - 1];

                    mHandler.removeCallbacks(mStopBrightnessPreview);
                    mHandler.post(() -> {
                        if (StatusManager.acquire(this, GlyphPriority.PREVIEW, null)) {
                            NanoGlyphManager.Java.Matrix.setBrightness(rawBrightness);
                            mHandler.postDelayed(mStopBrightnessPreview, BRIGHTNESS_PREVIEW_TIMEOUT_MS);
                        }
                    });
                }
            }
            case Constants.Settings.Progress.ENABLE -> {
                boolean enabled = (Boolean) newValue;

                if (enabled && !checkNotificationService(true, preference)) return false;

                mProgressMediaPreference.setEnabled(enabled && SettingsManager.isGlyphEnabled());

                if (enabled) {
                    ServiceUtils.startProgressService();
                    mHandler.postDelayed(ServiceUtils::checkGlyphService, 250);
                } else {
                    ServiceUtils.checkGlyphService();
                }
                return true;
            }
            case Constants.Settings.Progress.MEDIA_ENABLE -> {
                mHandler.postDelayed(ServiceUtils::checkGlyphService, 100);
                return true;

            }
            case Constants.Settings.BatterySaver.ENABLE -> {
                updateBatterySaver((Boolean) newValue);
            }
            case Constants.Settings.Volume.LEVEL_ENABLE -> {
                SettingsManager.Volume.setEnabled((Boolean) newValue);
            }
        }

        mHandler.post(ServiceUtils::checkGlyphService);

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SettingsManager.enableGlyph(isChecked);

        List<Preference> allPrefs = getAllPreferences(getPreferenceScreen());

        for (Preference pref : allPrefs) {

            if (pref instanceof PrimarySwitchPreference p) {
                p.setSwitchEnabled(isChecked);
                p.setEnabled(isChecked);
            } else if (pref == (Preference) mBrightnessPreference) {
                pref.setEnabled(!mAutoBrightnessPreference.isChecked() && isChecked);
            } else if (pref == (Preference) mBatterySaverPreference || pref == mSchedulePreference
                    || pref == mUtilitiesPreference) {
                ; // skip
            } else if (pref instanceof MainSwitchPreference m) {
                ; // skip
            } else if (pref instanceof PreferenceCategory c) {
                ; // skip
            } else {
                pref.setEnabled(isChecked);
            }
        }

        mHandler.post(() -> {
            ServiceUtils.checkGlyphService();
            updateTorchTile();
            updateMainSwitchState();
        });
    }

    private void tryNanoGlyph() {
        mExecutor.execute(() -> {
            boolean connected = NanoGlyphManager.Java.tryConnect(500);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (!connected) {
                    showDialog(
                            requireActivity(),
                            R.string.glyph_settings_nanoglyph_failure_title,
                            R.string.glyph_settings_nanoglyph_failure_message,
                            android.R.string.ok, () -> {
                                requireActivity().finish();
                            });
                }
            });
            ServiceUtils.checkGlyphService(false);
        });
    }

    private void updateTorchTile() {
        try {
            Intent intent = new Intent("org.nukisystems.hieroglyph.UPDATE_TORCH_TILE");
            requireContext().sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

    private void updateBatterySaver(boolean state) {
        try {
            Intent intent = new Intent(requireContext(), BatterySaverService.class);
            intent.setAction("org.nukisystems.hieroglyph.UPDATE_BATTERY_SAVER");
            intent.putExtra("status", state);
            requireContext().startService(intent);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Constants.Settings.Notification.ENABLE.equals(preference.getKey())) {
            if (!checkNotificationService(true, preference, true)) return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean checkNotificationService(boolean showDialog, Preference targetPref,
                                             boolean treeClick) {
        if (!ServiceUtils.isNotificationServiceEnabled(context)) {
           if (showDialog) {
               showDialog(
                   requireActivity(),
                   R.string.glyph_settings_notifs_permission_dialog_title,
                   R.string.glyph_settings_notifs_permission_dialog_message,
                   android.R.string.ok, () -> {
                       Intent intent
                               = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                       requireContext().startActivity(intent);
                       pendingConfirmation = new CompletableFuture<>();
                       pendingConfirmation.thenAccept(confirmed -> {
                           if (confirmed) {
                               if (treeClick) {
                                   mHandler.post(() -> {
                                       Intent treeIntent = targetPref.getIntent();
                                       if (treeIntent != null) requireContext().startActivity(treeIntent);
                                   });
                               } else {
                                   targetPref.getSharedPreferences()
                                           .edit()
                                           .putBoolean(targetPref.getKey(), true)
                                           .apply();
                                   if (targetPref instanceof TwoStatePreference p) {
                                       p.setChecked(true);
                                   }
                               }
                               if (!treeClick) mHandler.post(ServiceUtils::checkGlyphService);
                           }
                       });
                   },
                   android.R.string.cancel, null);
           }
            return false;
        }
        return true;
    }

    private boolean checkNotificationService(boolean showDialog, Preference targetPref) {
        return checkNotificationService(showDialog, targetPref, false);
    }

    @Override
    public void onDestroy() {
        mSettingObserver.unregister(mContentResolver);
        try {
            requireContext().unregisterReceiver(mScheduleUpdateReceiver);
        } catch (Exception e) {
            // Receiver not registered
        }
        if (mHandler.hasCallbacks(mStopBrightnessPreview)) {
            mHandler.removeCallbacks(mStopBrightnessPreview);
            mStopBrightnessPreview.run();
            StatusManager.release(this);
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mNotifsPreference.setSwitchEnabled(mSwitchBar.isChecked()
                && checkNotificationService(false, null));
        if (pendingConfirmation != null && !pendingConfirmation.isDone()) {
            if (checkNotificationService(false, null)) {
                pendingConfirmation.complete(true);
                pendingConfirmation = null;
            }
        }
        updateScheduleSummary();
        updateMainSwitchState();
        updatePrimarySwitches();
    }

    public void updatePrimarySwitches() {
        mVolumeLevelPreference.setChecked(SettingsManager.Volume.isEnabled());
    }

    private void updateScheduleSummary() {
        if (mSchedulePreference != null) {
            String summary = GlyphScheduleManager.getScheduleSummary(requireContext());
            mSchedulePreference.setSummary(summary);
        }
    }

    private void updateMainSwitchState() {
        if (mSwitchBar != null) {
            boolean baseEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();
            boolean effectiveEnabled = SettingsManager.isGlyphEnabled();
            boolean batterySavingActive = StatusManager.isBatterySavingActive();
            
            mSwitchBar.setChecked(baseEnabled);
            
            if (baseEnabled && !effectiveEnabled) {
                mSwitchBar.setSummary(getString(R.string.glyph_settings_summary_schedule));
            } else if (batterySavingActive) {
                mSwitchBar.setSummary(getString(R.string.glyph_settings_summary_battery_saving));
            } else {
                 mSwitchBar.setSummary("");
            }
        }
    }

    @Override
    public void onSuspended() {
        StatusManager.release(this);
    }

    @Override
    public void onActivated() {}

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.Settings.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.Settings.Call.ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.Settings.Notification.ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.Settings.Flip.ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.Secure.getUriFor(Constants.Settings.GLYPH_ENABLE))
                    && mSwitchBar != null) {
                mSwitchBar.setChecked(SettingsManager.isGlyphEnabledIgnoreSchedule());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.Settings.Flip.ENABLE))
                    && mFlipPreference != null) {
                mFlipPreference.setChecked(SettingsManager.isGlyphFlipEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.Settings.Call.ENABLE))
                    && mCallPreference != null) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.Settings.Notification.ENABLE))
                    && mNotifsPreference != null) {
                mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
            }
        }
    }
}
