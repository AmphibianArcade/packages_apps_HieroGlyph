package org.nukisystems.hieroglyph.Settings.Toy;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.nukisystems.hieroglyph.R;

public class AodToyExtraSettingsFragment extends SettingsBasePreferenceFragment {

    SharedPreferences prefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        getActivity().setTitle(R.string.glyph_settings_always_on_toy_title);
        addPreferencesFromResource(R.xml.glyph_settings_aod_toy_extra);

    }

}

