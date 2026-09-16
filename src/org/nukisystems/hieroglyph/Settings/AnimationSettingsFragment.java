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

package org.nukisystems.hieroglyph.Settings;

import static org.nukisystems.hieroglyph.Utils.InterfaceUtils.showDialog;
import static org.nukisystems.hieroglyph.Utils.InterfaceUtils.showToast;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.nukisystems.hieroglyph.Manager.AnimationManager;
import org.nukisystems.hieroglyph.R;
import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Preference.MatrixPreference;
import org.nukisystems.hieroglyph.Utils.CSVUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.Utils.ServiceUtils;

public class AnimationSettingsFragment
        extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private static final String FRAGMENT_TYPE_NOTIF = "NOTIFS";
    private static final String FRAGMENT_TYPE_CALL = "CALL";
    private static final String FRAGMENT_TYPE_FLIP = "FLIP";

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService mAppExecutor =
            Executors.newSingleThreadExecutor();

    private String fragmentType = null;
    private String fragmentPkg = null;

    PackageManager mPackageManager;

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mToneSyncSwitch;

    private List<String> mEssentialApps = new ArrayList<String>();
    private List<String> mEssentialAppsNames = new ArrayList<String>();

    private List<String> userAnimationList = new ArrayList<>();
    private List<String> bundledAnimationList = new ArrayList<>();

    private ListPreference mListPreference;
    private Preference mLivePreviewPreference;
    private MultiSelectListPreference mMultiSelectListPreference;
    private SwitchPreferenceCompat mReverseAnimationSwitch;
    private PreferenceCategory appListCategory;

    private SwitchPreferenceCompat mGlyphFlipAnimationSwitch;

    private MatrixPreference mMatrixPreference;

    private String fragmentTitle = null;

    private String animationPreviewKey;

    private String enableKey;

    private String animationListKey;

    private String livePreviewKey;

    private String defaultAnimation;

    private String userAnimationPrefix;
    private String reverseAnimationKey;

    private boolean shouldAlternate = false;

    private Consumer<Uri> mContactPickerAction;
    private boolean isContactSpecific = false;
    private String contactId;
    private String contactName;

    private boolean isAppSpecific = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Bundle args = getArguments();
        fragmentType = args.getString("type", "").toUpperCase();
        contactId = args.getString("contact_id", "");
        fragmentPkg = args.getString("package", "");

        isContactSpecific = !contactId.isEmpty();
        isAppSpecific = !fragmentPkg.isEmpty();

        if (isContactSpecific) {
            contactName = ResourceUtils.getContactName(requireContext(), contactId);
            if (contactName == null) {
                requireContext().getSharedPreferences(Constants.Settings.Call.CONTACT_PREF_PREFIX
                                + contactId, Context.MODE_PRIVATE)
                        .edit().clear().apply();
                showToast("Contact does not exist!");
            }
        }

        if (fragmentType.isEmpty() || !fragmentType.equals(FRAGMENT_TYPE_NOTIF)
                && !fragmentType.equals(FRAGMENT_TYPE_CALL)
                && !fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment type is invalid!");
            return;
        }

        if (isContactSpecific && !fragmentType.equals(FRAGMENT_TYPE_CALL)) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment parameters are invalid!");
            return;
        }

        mPackageManager = getActivity().getPackageManager();

        evaluatePrefs();

        getActivity().setTitle(fragmentTitle);

        if (!isContactSpecific) {
            mSwitchBar = findPreference(enableKey);
            mSwitchBar.addOnSwitchChangeListener(this);
            if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
                mSwitchBar.setChecked(SettingsManager.isGlyphFlipEnabled());
            } else {
                mSwitchBar.setChecked(isAnimationEnabled());
            }
        }

        mListPreference = findPreference(animationListKey);
        mListPreference.setOnPreferenceChangeListener(this);

        if (mToneSyncSwitch != null) {
            if (mToneSyncSwitch.isChecked()) {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title_sync_enabled);
            } else {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title);
            }
        }

        bundledAnimationList = getAnimations(0);
        userAnimationList = getAnimations(1);

        List<String[]> paired = new ArrayList<>();
        for (String name : bundledAnimationList) {
            paired.add(new String[]{name, name});
        }
        for (String name : userAnimationList) {
            paired.add(new String[]{name, userAnimationPrefix + name});
        }

        paired.sort(Comparator.comparing(p -> p[0]));

        List<String> animationEntryList
                = paired.stream().map(p -> p[0]).collect(Collectors.toList());
        List<String> animationEntryValues
                = paired.stream().map(p -> p[1]).collect(Collectors.toList());

        if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            boolean hasFlipCsv = ResourceUtils.hasFlipCsv();
            animationEntryList.addFirst(
                    getString(R.string.glyph_settings_flip_animation_option_follow_notification)
            );
            if (hasFlipCsv) {
                animationEntryList.addFirst(getString(R.string.glyph_settings_default_option));
            }
            animationEntryValues.addFirst(Constants.Settings.Notification.ANIMATION_ALTERNATE);
            if (hasFlipCsv) animationEntryValues.addFirst("flip");
        }
        mListPreference.setEntries(animationEntryList.toArray(new String[0]));
        mListPreference.setEntryValues(animationEntryValues.toArray(new String[0]));
            if (!animationEntryValues.contains(mListPreference.getValue())) {
                if (!fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
                    mListPreference.setValue(ResourceUtils.getString(defaultAnimation));
                } else {
                    mListPreference.setValue(SettingsManager.getGlyphFlipAnimation());
                }
        }

        mLivePreviewPreference = findPreference(livePreviewKey);
        mMatrixPreference = findPreference(animationPreviewKey);

        mReverseAnimationSwitch = findPreference(reverseAnimationKey);
        mReverseAnimationSwitch.setOnPreferenceChangeListener(this);

    }

    private void evaluatePrefs() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    getPreferenceManager().setSharedPreferencesName(Constants.Settings.Notification.APP_PREF_PREFIX
                            + fragmentPkg);
                    addPreferencesFromResource(R.xml.glyph_notifs_settings_app);
                    mScreen = getPreferenceScreen();
                    String pkgLabel = getPackageLabel(fragmentPkg);
                    fragmentTitle
                            = requireContext().getString(R.string.glyph_settings_notifs_toggle_title)
                            + " (" + pkgLabel + ")";
                    addDeletePref(Constants.Settings.Notification.APP_PREF_PREFIX + fragmentPkg, pkgLabel);
                } else {
                    addPreferencesFromResource(R.xml.glyph_notifs_settings);
                    fragmentTitle = requireContext().getString(R.string.glyph_settings_notifs_toggle_title);

                    appListCategory = findPreference(Constants.Settings.Notification.SUB_CATEGORY);
                    inflateAppLists();

                    mToneSyncSwitch = findPreference(Constants.Settings.Notification.TONE_SYNC);
                    mToneSyncSwitch.setOnPreferenceChangeListener(this);
                }

                animationPreviewKey = Constants.Settings.Notification.SUB_PREVIEW;

                enableKey = Constants.Settings.Notification.SUB_ENABLE;

                livePreviewKey = Constants.Settings.Notification.SUB_LIVE_PREVIEW;

                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;
                animationListKey = Constants.Settings.Notification.SUB_ANIMATIONS;

                defaultAnimation = "glyph_settings_notifs_animations_default";
                reverseAnimationKey = Constants.Settings.Notification.REVERSE_ANIMATION_ENABLE;
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    getPreferenceManager().setSharedPreferencesName(Constants.Settings.Call.APP_PREF_PREFIX
                            + fragmentPkg);
                    addPreferencesFromResource(R.xml.glyph_call_settings_app);
                    mScreen = getPreferenceScreen();
                    String pkgLabel = getPackageLabel(fragmentPkg);
                    fragmentTitle
                            = requireContext().getString(R.string.glyph_settings_call_toggle_title)
                            + " (" + pkgLabel + ")";

                    PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
                    Preference mDeletePreferences = new Preference(mScreen.getContext());
                    mDeletePreferences.setTitle(R.string.glyph_settings_delete_title);
                    mDeletePreferences.setOnPreferenceClickListener(pref -> {
                        showDialog(requireActivity(),
                                getString(R.string.glyph_settings_delete_title) + "?",
                                getString(R.string.glyph_settings_delete_confirm_message_start)
                                        + " " + pkgLabel + "?",
                                android.R.string.ok,
                                () -> {
                                    requireContext().deleteSharedPreferences(
                                            Constants.Settings.Call.APP_PREF_PREFIX + fragmentPkg);
                                    getActivity().finish();
                                },
                                android.R.string.cancel, null);
                        return true;
                    });
                    mDeletePreferences.setIcon(R.drawable.ic_delete_forever);
                    mScreen.addPreference(mCategory);
                    mCategory.addPreference(mDeletePreferences);

                } else if (isContactSpecific) {
                    getPreferenceManager().setSharedPreferencesName(
                            Constants.Settings.Call.CONTACT_PREF_PREFIX + contactId);
                    addPreferencesFromResource(R.xml.glyph_call_settings_generic);
                    mScreen = getPreferenceScreen();

                    fragmentTitle
                            = requireContext().getString(R.string.glyph_settings_call_toggle_title)
                                    + " (" + contactName + ")";

                    addDeletePref(Constants.Settings.Call.CONTACT_PREF_PREFIX + contactId, contactName);
                } else {
                    addPreferencesFromResource(R.xml.glyph_call_settings);
                    fragmentTitle =
                            requireContext().getString(R.string.glyph_settings_call_toggle_title);

                    Preference mContactSelectPreference =
                            findPreference(Constants.Settings.Call.SUB_CONTACT_SELECT);

                    mContactSelectPreference.setOnPreferenceClickListener(pref -> {
                           mContactPickerAction = uri -> {
                                if (uri != null) {
                                    Cursor cursor = requireContext().getContentResolver().query(
                                            uri,
                                            new String[]{ContactsContract.Contacts._ID},
                                            null, null, null);

                                    if (cursor != null && cursor.moveToFirst()) {
                                        String contact = cursor.getString(0);
                                        cursor.close();
                                        Intent intent = new Intent(requireContext(),
                                                AnimationSettingsActivity.class);
                                        intent.putExtra("type", fragmentType);
                                        intent.putExtra("contact_id", contact);
                                        startActivity(intent);
                                    } else {
                                        if (cursor != null) cursor.close();
                                    }
                                }
                           };
                           mContactPicker.launch(null);
                           return true;
                    });
                    appListCategory = findPreference(Constants.Settings.Call.SUB_CATEGORY);
                    inflateAppLists();

                    // mToneSyncSwitch = findPreference(Constants.Settings.Call.TONE_SYNC);
                    // mToneSyncSwitch.setOnPreferenceChangeListener(this);

                }

                animationPreviewKey = Constants.Settings.Call.SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_CALL_CSV_PREFIX;
                enableKey = Constants.Settings.Call.SUB_ENABLE;
                animationListKey = Constants.Settings.Call.SUB_ANIMATIONS;
                defaultAnimation = "glyph_settings_call_animations_default";
                livePreviewKey = Constants.Settings.Call.SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.Settings.Call.REVERSE_ANIMATION_ENABLE;

            }
            case FRAGMENT_TYPE_FLIP -> {
                addPreferencesFromResource(R.xml.glyph_flip_settings);
                fragmentTitle =
                        requireContext().getString(R.string.glyph_settings_flip_toggle_title);
                animationPreviewKey = Constants.Settings.Flip.SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;
                animationListKey = Constants.Settings.Flip.SUB_ANIMATIONS;
                enableKey = Constants.Settings.Flip.SUB_ENABLE;
                livePreviewKey = Constants.Settings.Flip.SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.Settings.Flip.REVERSE_ANIMATION_ENABLE;

                mGlyphFlipAnimationSwitch = findPreference(Constants.Settings.Flip.SUB_ANIMATION_ENABLE);
                mGlyphFlipAnimationSwitch.setOnPreferenceChangeListener(this);

            }
        }
    }

    private record AppEntry(ApplicationInfo app, PackageInfo packageInfo, String label) { }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                && mListPreference.getValue().equals(Constants.Settings.Notification.ANIMATION_ALTERNATE);
        boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

        mReverseAnimationSwitch.setVisible(!shouldAlternate);

        if (mListPreference.getValue().startsWith(userAnimationPrefix)) {
            String animationName = mListPreference.getValue();
            CSVUtils.checkUserAnimation(animationName);
        }
        mMatrixPreference.updateAnimation(
                    isAnimationEnabled(),
                    getGlyphAnimation(),
                    1500,
                    shouldReverse,
                    shouldAlternate
        );
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(animationListKey)) {
            String animationName = newValue.toString();

            shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && animationName.equals(Constants.Settings.Notification.ANIMATION_ALTERNATE);
            boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

            mReverseAnimationSwitch.setVisible(!shouldAlternate);

            if (shouldAlternate) animationName = SettingsManager.getGlyphNotifsAnimation();
            if (animationName.startsWith(userAnimationPrefix)) {
                if (!CSVUtils.checkUserAnimation(animationName)) return false;
            }
            mMatrixPreference.updateAnimation(
                    isAnimationEnabled(),
                    animationName,
                    1500,
                    shouldReverse,
                    shouldAlternate
            );
            endLivePreview();
            resolveAppSummaries(animationName);
            return true;
        }

        if (preferenceKey.equals(Constants.Settings.Flip.SUB_ANIMATION_ENABLE)) {
            shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && getGlyphAnimation().equals(Constants.Settings.Notification.ANIMATION_ALTERNATE);
            boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

            mMatrixPreference.updateAnimation((Boolean) newValue, 1500, shouldReverse);
        }

        if (preferenceKey.equals(reverseAnimationKey)) {
            mMatrixPreference.updateAnimation(isAnimationEnabled(), 1500, (Boolean) newValue);
        }

        if (preferenceKey.equals(Constants.Settings.Notification.TONE_SYNC)) {
            if ((Boolean) newValue) {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title_sync_enabled);
            } else {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title);
            }
            mHandler.post(ServiceUtils::checkGlyphService);

        }

        return true;
    }

    private final ActivityResultLauncher<Void> mContactPicker
            = registerForActivityResult(
                    new ActivityResultContracts.PickContact(), uri -> {
                if (uri != null && mContactPickerAction != null) {
                    mContactPickerAction.accept(uri);
                }
            }
    );

    private void inflateAppLists() {

        if (fragmentType.equals(FRAGMENT_TYPE_CALL)) {
            String[] callPermissions = {"android.permission.MANAGE_OWN_CALLS"};
            List<String> callApps =
                    new ArrayList<>(Arrays.asList(
                            ResourceUtils.getApplicationsWithPermission(false, callPermissions)));

            callApps.remove(getDefaultDialer());

            if (callApps.isEmpty()) {
                getPreferenceScreen().removePreference(appListCategory);
                return;
            }

            for (String pkg : callApps) addAppPreference(pkg);

        }

        if (fragmentType.equals(FRAGMENT_TYPE_NOTIF)) loadNotificationApps();

    }

    private void addAppPreference(String pkg) {
        String label = getPackageLabel(pkg);
        PrimarySwitchPreference mSwitchPreference
                = new PrimarySwitchPreference(getPreferenceScreen().getContext());
        mSwitchPreference.setKey(pkg);
        mSwitchPreference.setTitle(" " + label);
        try {
            mSwitchPreference.setIcon(mPackageManager.getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException e) {
            mSwitchPreference.setIcon(mPackageManager.getDefaultActivityIcon());
        }
        mSwitchPreference.setChecked(isAnimationEnabled(pkg));
        mSwitchPreference.setOnPreferenceClickListener(preference -> {
            String key = preference.getKey();
            Intent intent = new Intent(requireContext(),
                    AnimationSettingsActivity.class);
            intent.putExtra("type", fragmentType);
            intent.putExtra("package", key);
            startActivity(intent);
            return true;
        });
        mSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String key = preference.getKey();
            setAnimationEnabled(key, (Boolean) newValue);
            return true;
        });
        resolveAppSummary(mSwitchPreference, pkg);
        appListCategory.addPreference(mSwitchPreference);

    }

    private void addDeletePref(String sharedPref, String msgLabel) {
        PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
        Preference mDeletePreferences = new Preference(mScreen.getContext());
        mDeletePreferences.setTitle(R.string.glyph_settings_delete_title);
        mDeletePreferences.setOnPreferenceClickListener(pref -> {
            showDialog(requireActivity(),
                    getString(R.string.glyph_settings_delete_title) + "?",
                    getString(R.string.glyph_settings_delete_confirm_message_start)
                            + " " + msgLabel + "?",
                    android.R.string.ok,
                    () -> {
                        requireContext().deleteSharedPreferences(
                                sharedPref);
                        getActivity().finish();
                    },
                    android.R.string.cancel, null);
            return true;
        });
        mDeletePreferences.setIcon(R.drawable.ic_delete_forever);
        mScreen.addPreference(mCategory);
        mCategory.addPreference(mDeletePreferences);
    }

    private void resolveAppSummary(PrimarySwitchPreference pref, String pkg, String comp) {
        boolean isReversed = isAppAnimationReversed(pkg);
        boolean hasConfig = comp == null ? appHasConfig(pkg) : appHasConfig(pkg, comp);
        if (hasConfig) {
            if (isReversed) {
                pref.setSummary(" " + getGlyphAnimation(pkg, false)
                        + " (" + getString(R.string.glyph_settings_animation_is_reversed) + ")");
            } else {
                pref.setSummary(" " + getGlyphAnimation(pkg, false));
            }
        } else if (!TextUtils.isEmpty(pref.getSummary())) {
            pref.setSummary(null);
        }
    }

    private void resolveAppSummary(PrimarySwitchPreference pref, String pkg) {
       resolveAppSummary(pref, pkg, null);
    }

    private String getPackageLabel(String packageName) {
        try {
            ApplicationInfo info = mPackageManager
                    .getApplicationInfo(packageName, 0);
            return mPackageManager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName; // fall back to package name if not found
        }
    }

    private void loadNotificationApps() {
        mAppExecutor.execute(() -> {
            List<AppEntry> apps = getNotificationApps();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                populateNotificationApps(apps);
            });
        });
    }

    private List<AppEntry> getNotificationApps() {
        List<ApplicationInfo> installedApps =
                mPackageManager.getInstalledApplications(0);


        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launcherApps =
                mPackageManager.queryIntentActivities(launcherIntent, 0);

        Set<String> launchablePackages = new HashSet<>();

        for (ResolveInfo resolveInfo : launcherApps) {
            if (resolveInfo.activityInfo != null) {
                launchablePackages.add(
                        resolveInfo.activityInfo.packageName);
            }
        }

        List<PackageInfo> packageInfos =
                mPackageManager.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS);

        Map<String, PackageInfo> packageInfoMap = new HashMap<>();

        for (PackageInfo info : packageInfos) {
            packageInfoMap.put(info.packageName, info);
        }

        List<AppEntry> result = new ArrayList<>();

        for (ApplicationInfo app : installedApps) {
            String packageName = app.packageName;

            if (!launchablePackages.contains(packageName)) continue;
            if (ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)) continue;

            boolean canNotify = true;

            if (app.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
                PackageInfo info = packageInfoMap.get(packageName);

                if (info == null || info.requestedPermissions == null) {
                    canNotify = false;
                } else {
                    canNotify = Arrays.asList(info.requestedPermissions)
                            .contains(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            if (!canNotify) continue;
            String label = app.loadLabel(mPackageManager).toString();
            result.add(new AppEntry(app, packageInfoMap.get(packageName), label));
        }

        result.sort(Comparator.comparing(
                entry -> entry.label,
                String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    private void populateNotificationApps(List<AppEntry> apps) {
        mEssentialApps.clear();
        mEssentialAppsNames.clear();

        for (AppEntry entry : apps) {
            addAppPreference(entry);
        }

        mMultiSelectListPreference =
                findPreference(Constants.Settings.Notification.SUB_ESSENTIAL);

        if (mMultiSelectListPreference != null) {
            mMultiSelectListPreference.setOnPreferenceChangeListener(this);

            mMultiSelectListPreference.setEntries(
                    mEssentialAppsNames.toArray(new CharSequence[0]));

            mMultiSelectListPreference.setEntryValues(
                    mEssentialApps.toArray(new CharSequence[0]));
        }
    }

    private void addAppPreference(AppEntry entry) {
        ApplicationInfo app = entry.app;
        String pkg = app.packageName;

        PrimarySwitchPreference preference =
                new PrimarySwitchPreference(
                        getPreferenceScreen().getContext());

        preference.setKey(pkg);
        preference.setTitle(" " + entry.label);

        try {
            preference.setIcon(app.loadIcon(mPackageManager));
        } catch (Exception e) {
            preference.setIcon(mPackageManager.getDefaultActivityIcon());
        }

        preference.setChecked(isAnimationEnabled(pkg));

        preference.setOnPreferenceClickListener(pref -> {
            String key = pref.getKey();

            Intent intent = new Intent(requireContext(), AnimationSettingsActivity.class);

            intent.putExtra("type", fragmentType);
            intent.putExtra("package", key);

            startActivity(intent);

            return true;
        });

        preference.setOnPreferenceChangeListener(
            (pref, newValue) -> {
                String key = pref.getKey();
                setAnimationEnabled(key, (Boolean) newValue);
                return true;
            }
        );

        resolveAppSummary(preference, pkg);

        appListCategory.addPreference(preference);

        mEssentialApps.add(pkg);
        mEssentialAppsNames.add(entry.label);
    }

    private String getDefaultDialer() {
        String packageName = "";
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(dialerIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null) {
            packageName = resolveInfo.activityInfo.packageName;
        }
        return packageName;
    }

    private List<String> getAnimations(int domain) {
        switch (domain) {
            case 0 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getBundledNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getBundledCallAnimations();
                    }
                }
            }
            case 1 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getUserNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getUserCallAnimations();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private String getGlyphAnimation() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    return SettingsManager.getGlyphNotifsAnimation(fragmentPkg);
                } else {
                    return SettingsManager.getGlyphNotifsAnimation();
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isContactSpecific) {
                    return SettingsManager.getGlyphCallAnimation(Integer.parseInt(contactId));
                } else if (isAppSpecific) {
                    return SettingsManager.getGlyphCallAnimation(fragmentPkg);
                } else {
                    return SettingsManager.getGlyphCallAnimation();
                }
            }

            case FRAGMENT_TYPE_FLIP -> {
                String value = SettingsManager.getGlyphFlipAnimation();
                if (value.equals(Constants.Settings.Notification.ANIMATION_ALTERNATE)) {
                    return SettingsManager.getGlyphNotifsAnimation();
                } else {
                    return value;
                }
            }
        }
        return "";
    }

    private String getGlyphAnimation(String pkg, boolean internal) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                String anim = SettingsManager.getGlyphNotifsAnimation(pkg);
                if (internal) {
                    return anim;
                } else {
                    return anim.replace(Constants.GLYPH_USER_NOTIF_CSV_PREFIX, "");
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                String anim = SettingsManager.getGlyphCallAnimation(pkg);
                if (internal) {
                    return anim;
                } else {
                    return anim.replace(Constants.GLYPH_USER_CALL_CSV_PREFIX, "");
                }
            }
        }
        return "";
    }

    private boolean isAnimationEnabled() {
        return isAnimationEnabled(null);
    }
    
    private boolean isAnimationEnabled(String checkPkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    return SettingsManager.isGlyphNotifsEnabled(fragmentPkg);
                } else if (checkPkg != null) {
                    return SettingsManager.isGlyphNotifsEnabled(checkPkg);
                } else {
                    return SettingsManager.isGlyphNotifsEnabled();
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    return SettingsManager.isGlyphCallEnabled(fragmentPkg);
                } else if (checkPkg != null) {
                    SettingsManager.isGlyphCallEnabled(checkPkg);
                } else {
                    return SettingsManager.isGlyphCallEnabled();
                }
            }

            case FRAGMENT_TYPE_FLIP -> {
                return SettingsManager.isGlyphFlipEnabled()
                        && SettingsManager.isGlyphFlipAnimationEnabled();
            }
        }
        return false;
    }

    private void setAnimationEnabled(boolean state) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    SettingsManager.setGlyphNotifsEnabled(fragmentPkg, state);
                } else {
                    SettingsManager.setGlyphNotifsEnabled(state);
                }
            }
            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    SettingsManager.setGlyphCallEnabled(fragmentPkg, state);
                } else {
                    SettingsManager.setGlyphCallEnabled(state);
                }
            }
            case FRAGMENT_TYPE_FLIP -> {
                SettingsManager.setGlyphFlipEnabled(state);
            }
        }
    }

    private void setAnimationEnabled(String pkg, boolean state) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                    SettingsManager.setGlyphNotifsEnabled(pkg, state);
            }
            case FRAGMENT_TYPE_CALL -> {
                    SettingsManager.setGlyphCallEnabled(pkg, state);
            }
        }
    }

    private boolean appHasConfig(String pkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.appHasGlyphNotifsConfig(pkg);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.appHasGlyphCallConfig(pkg);
            }
        }
        return false;
    }

    private boolean appHasConfig(String pkg, String comp) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.appHasGlyphNotifsConfig(pkg, comp);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.appHasGlyphCallConfig(pkg, comp);
            }
        }
        return false;
    }

    private boolean isAppAnimationReversed(String pkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.isGlyphNotifsAnimationReversed(pkg);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.isGlyphCallAnimationReversed(pkg);
            }
        }
        return false;
    }


    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (livePreviewKey.equals(preference.getKey())) {
            beginLivePreview();
        }
        return true;
    }

    private void beginLivePreview() {
        mLivePreviewPreference.setEnabled(false);
        mLivePreviewPreference.setSummary(
            R.string.glyph_settings_animations_live_preview_summary_playing
        );
        shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && mListPreference.getValue().equals(Constants.Settings.Notification.ANIMATION_ALTERNATE);
        boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;
        mHandler.postDelayed(() -> {
            AnimationManager.Coordinator.get().stream(
                requireContext(),
                getGlyphAnimation(),
                shouldReverse,
                shouldAlternate,
                () -> { if (isAdded()) getActivity().runOnUiThread(this::resetLivePreviewPref); }
            );
        }, 1000);
    }

    private void endLivePreview() {
        AnimationManager.Coordinator.get().cancelCurrent();
        resetLivePreviewPref();
    }

    private void resetLivePreviewPref() {
        if (isAdded() && getActivity() != null) {
            mLivePreviewPreference.setEnabled(true);
            mLivePreviewPreference.setSummary(
                R.string.glyph_settings_animations_live_preview_summary
            );
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setAnimationEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mMatrixPreference.updateAnimation(isChecked, getGlyphAnimation(), 1500);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePrimarySwitches();
        endLivePreview();
    }

    @Override
    public void onPause() {
        super.onPause();
        endLivePreview();
    }

    private void updatePrimarySwitches() {
        if ((fragmentType.equals(FRAGMENT_TYPE_CALL)
                || fragmentType.equals(FRAGMENT_TYPE_NOTIF))
                && !isAppSpecific
                && !isContactSpecific) {
            for (int i = 0; i < appListCategory.getPreferenceCount(); i++) {
                Preference pref = appListCategory.getPreference(i);
                if (pref instanceof PrimarySwitchPreference) {
                    PrimarySwitchPreference switchPref = (PrimarySwitchPreference) pref;
                    switch (fragmentType) {
                        case FRAGMENT_TYPE_NOTIF ->
                                switchPref.setChecked(SettingsManager.isGlyphNotifsEnabled(pref.getKey()));
                        case FRAGMENT_TYPE_CALL ->
                                switchPref.setChecked(SettingsManager.isGlyphCallEnabled(pref.getKey()));
                    }
                    resolveAppSummary(switchPref, pref.getKey());
                }
            }
        }
    }

    private void resolveAppSummaries(String comp) {
        if ((fragmentType.equals(FRAGMENT_TYPE_CALL)
                || fragmentType.equals(FRAGMENT_TYPE_NOTIF))
                && !isAppSpecific
                && !isContactSpecific) {
            for (int i = 0; i < appListCategory.getPreferenceCount(); i++) {
                Preference pref = appListCategory.getPreference(i);
                if (pref instanceof PrimarySwitchPreference) {
                    PrimarySwitchPreference switchPref = (PrimarySwitchPreference) pref;
                    resolveAppSummary(switchPref, pref.getKey(), comp);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        endLivePreview();
    }

}
