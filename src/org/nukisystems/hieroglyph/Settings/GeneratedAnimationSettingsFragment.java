package org.nukisystems.hieroglyph.Settings;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;

import org.nukisystems.hieroglyph.R;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Data.CsvContent;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Preference.MatrixPreference;
import org.nukisystems.hieroglyph.Utils.MatrixUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.Utils.ServiceUtils;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GeneratedAnimationSettingsFragment
        extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private static final String FRAGMENT_TYPE_CHARGING = "CHARGING";
    private static final String FRAGMENT_TYPE_VOLUME = "VOLUME";

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService mAppExecutor =
            Executors.newSingleThreadExecutor();

    private String fragmentType = null;

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;

    private ListPreference mListPreference;

    private SwitchPreferenceCompat mShowCrossSwitch;
    private ListPreference mRotationPreference;

    private MatrixPreference mMatrixPreference;

    private String fragmentTitle = null;

    private String enableKey;

    private String animationPreviewKey;
    private String animationListKey;

    private String defaultAnimation;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Bundle args = getArguments();
        fragmentType = args.getString("type", "").toUpperCase();

        evaluatePrefs();

        getActivity().setTitle(fragmentTitle);
        mSwitchBar = findPreference(enableKey);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setOnPreferenceChangeListener(this);
        mSwitchBar.setChecked(isAnimationEnabled());

        mListPreference = findPreference(animationListKey);
        mListPreference.setOnPreferenceChangeListener(this);

        mMatrixPreference = findPreference(animationPreviewKey);

    }

    private void evaluatePrefs() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_CHARGING -> {
                // todo
            }
            case FRAGMENT_TYPE_VOLUME -> {
                addPreferencesFromResource(R.xml.glyph_volume_settings);
                fragmentTitle =
                        requireContext().getString(R.string.glyph_settings_volume_level_toggle_title);
                animationPreviewKey = Constants.Settings.Volume.SUB_PREVIEW;
                animationListKey = Constants.Settings.Volume.SUB_STYLE;
                enableKey = Constants.Settings.Volume.SUB_ENABLE;

                mRotationPreference = findPreference(Constants.Settings.Volume.SUB_ROTATION);
                mRotationPreference.setOnPreferenceChangeListener(this);

                mShowCrossSwitch = findPreference(Constants.Settings.Volume.SUB_SHOW_CROSS);
                mShowCrossSwitch.setOnPreferenceChangeListener(this);

            }
        }
    }

    private void reloadLayout(Preference p, Object newValue) {
        if (fragmentType.equals(FRAGMENT_TYPE_VOLUME)) {
            if (p == (Preference) mListPreference) {
                applyVolumeStyle((int) newValue);
            }
        }
    }

    private void reloadLayout() {
        if (fragmentType.equals(FRAGMENT_TYPE_VOLUME)) {
            applyVolumeStyle(SettingsManager.Volume.getStyle());
        }
    }

    private void applyVolumeStyle(int styleValue) {
        boolean showRotationPref =
                switch (MatrixUtils.Volume.Style.fromInt(styleValue)) {
                    case MatrixUtils.Volume.Style.LINEAR,
                         MatrixUtils.Volume.Style.CHECKERBOARD_LINEAR -> true;
                    default -> false;
                };
        mRotationPreference.setVisible(showRotationPref);
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMatrixPreference.updateAnimation(
                isAnimationEnabled(),
                getGlyphAnimation(),
                1500
        );
        reloadLayout();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();
        if (fragmentType.equals(FRAGMENT_TYPE_VOLUME)) {
            if (preference == (Preference) mListPreference) {
                int animationStyle = Integer.parseInt((String) newValue);
                mMatrixPreference.updateAnimation(
                        isAnimationEnabled(),
                        getGlyphAnimation(
                                animationStyle,
                                SettingsManager.Volume.getRotation(),
                                SettingsManager.Volume.showCrossWhenEmpty()),
                        1500
                );
                reloadLayout((Preference) mListPreference, animationStyle);
            } else if (preference == (Preference) mRotationPreference) {
                int rotation = Integer.parseInt((String) newValue);
                mMatrixPreference.updateAnimation(
                        isAnimationEnabled(),
                        getGlyphAnimation(
                                SettingsManager.Volume.getStyle(),
                                rotation,
                                SettingsManager.Volume.showCrossWhenEmpty()),
                        1500
                );
            } else if (preference == (Preference) mShowCrossSwitch) {
                boolean showCross = (Boolean) newValue;
                mMatrixPreference.updateAnimation(
                        isAnimationEnabled(),
                        getGlyphAnimation(
                                SettingsManager.Volume.getStyle(),
                                SettingsManager.Volume.getRotation(),
                                showCross),
                        1500
                );
            } else if (preference == (Preference) mSwitchBar) {
                setAnimationEnabled((Boolean) newValue);
                mHandler.post(ServiceUtils::checkGlyphService);
            }
        }
        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mMatrixPreference.updateAnimation(isChecked, getGlyphAnimation(), 1500);
    }

    private CsvContent generateVolumeCsv(MatrixUtils.Volume.Style style, int rotation, boolean withCross) {
        List<String> animation = new ArrayList<>();
        int repeatsPerFrame = 4;
        int repeatsForZero = 8;
        int maxValue = 100;

        for (int i = 0; i <= maxValue; i++) {
            int repeats = (i == 0) ? repeatsForZero : repeatsPerFrame;
            addVolumeFrame(animation, style, rotation, withCross, i, repeats);
        }

        for (int i = maxValue - 1; i >= 0; i--) {
            int repeats = (i == 0) ? repeatsForZero : repeatsPerFrame;
            addVolumeFrame(animation, style, rotation, withCross, i, repeats);
        }

        String csv = String.join("\n", animation);
        return MatrixUtils.trimToValidAnim(new CsvContent(csv));
    }

    private void addVolumeFrame(List<String> animation, MatrixUtils.Volume.Style style, int rotation,
                          boolean withCross, int i, int repeatsPerFrame) {
        int[] intFrame;
        if (i == 0 && withCross) {
            intFrame = MatrixUtils.Shape.Cross(
                    Constants.getMaxBrightness(),
                    (MatrixUtils.getGridSize() / 2) - 4);
        } else {
            intFrame = MatrixUtils.Volume.generateFrame(style, i, rotation);
        }

        String line = Arrays.stream(intFrame).mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        for (int r = 0; r < repeatsPerFrame; r++) {
            animation.add(line);
        }
    }

    private CsvContent getGlyphAnimation() {
        return getGlyphAnimation(
                SettingsManager.Volume.getStyle(),
                SettingsManager.Volume.getRotation(),
                SettingsManager.Volume.showCrossWhenEmpty()
        );
    }

    private CsvContent getGlyphAnimation(int styleVal, int rotationVal, boolean showCross) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_CHARGING -> // todo
            {
                return new CsvContent("todo");
            }
            case FRAGMENT_TYPE_VOLUME -> {
                return generateVolumeCsv(
                MatrixUtils.Volume.Style.fromInt(styleVal), rotationVal, showCross);
            }
        }
        return new CsvContent("null");
    }

    private void setAnimationEnabled(boolean state) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_CHARGING -> {
                // todo
            }
            case FRAGMENT_TYPE_VOLUME -> {
                SettingsManager.Volume.setEnabled(state);
            }
        }
    }

    private boolean isAnimationEnabled() {
        return switch (fragmentType) {
            case FRAGMENT_TYPE_CHARGING -> // todo
                    false;
            case FRAGMENT_TYPE_VOLUME -> SettingsManager.Volume.isEnabled();
            default -> throw new IllegalStateException("Unexpected value: " + fragmentType);
        };
    }

}
