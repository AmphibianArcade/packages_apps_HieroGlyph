package org.nukisystems.hieroglyph.Settings.Toy;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.nukisystems.hieroglyph.R;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Data;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.Services.ToyService.ToyIntent;
import org.nukisystems.hieroglyph.Utils.InterfaceUtils;
import org.nukisystems.hieroglyph.Utils.ResourceUtils;
import org.nukisystems.hieroglyph.Utils.ServiceUtils;

import java.util.Map;


public class AodToySettingsFragment extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener {

        SharedPreferences prefs;

        MainSwitchPreference toySwitch;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
                prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                getActivity().setTitle(R.string.glyph_settings_always_on_toy_title);
                addPreferencesFromResource(R.xml.glyph_settings_aod_toy);
                loadInstalledAodToys();

                toySwitch = findPreference(Constants.Settings.Toys.AOD_TOY_ENABLE);
                toySwitch.setOnPreferenceChangeListener(this);

                SelectorWithWidgetPreference currentSelect =
                        findPreference(SettingsManager.Toys.getAODToy());
                if (currentSelect != null) currentSelect.setChecked(true);

        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (toySwitch == preference) {
                        if (!(Boolean) newValue) {
                                ServiceUtils.startToyService(ToyIntent.ACTION_STOP_AOD);
                        } else {
                                reloadAOD();
                        }
                }
               return true;
        }

        private void loadInstalledAodToys() {
            Map<ComponentName, Data.GlyphToy> toyData
                    = ResourceUtils.Toys.getAOD(Constants.CONTEXT);

            if (toyData == null || toyData.isEmpty()) return;

            for (Map.Entry<ComponentName, Data.GlyphToy> entry : toyData.entrySet()) {
                    Data.GlyphToy data = entry.getValue();
                addToyPref(entry.getKey().flattenToString(),
                        data.getTitle(), data.getSummary(), data.icon());
            }

        }

        private void reloadAOD() {
                if (SettingsManager.Toys.isAODToyEnabled()) {
                        ServiceUtils.startToyService(ToyIntent.ACTION_START_AOD);
                }
        }

        private void addToyPref(String key, String title, String summary, Drawable icon) {
                PreferenceScreen mScreen = getPreferenceScreen();
                SelectorWithWidgetPreference toyPref
                        = new SelectorWithWidgetPreference(mScreen.getContext());
                toyPref.setTitle(title);
                toyPref.setKey(key);
                toyPref.setSummary(summary);
                toyPref.setIcon(icon);
                toyPref.setOnClickListener(pref -> {
                        SettingsManager.Toys.setAODToy(pref.getKey());
                        for (Preference p
                                : InterfaceUtils.Preferences.getAllPreferences(mScreen)) {
                                if (p instanceof SelectorWithWidgetPreference swwp) {
                                        swwp.setChecked(swwp == pref);
                                }
                        }
                        reloadAOD();
                });
                mScreen.addPreference(toyPref);
        }



}
