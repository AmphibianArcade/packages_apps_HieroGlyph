package org.nukisystems.hieroglyph.Tiles;

import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;

import org.nukisystems.hieroglyph.Constants.Constants;
import org.nukisystems.hieroglyph.Manager.SettingsManager;
import org.nukisystems.hieroglyph.R;
import org.nukisystems.hieroglyph.Utils.ServiceUtils;

public class AlwaysOnGlyphToyTileService extends TileService {

    Uri glyphMainUri = Settings.Secure.getUriFor(Constants.Settings.GLYPH_ENABLE);

    SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sharedPrefs, key) -> {
                if (Constants.Settings.Flip.ENABLE.equals(key)) {
                    updateState();
                }
            };

    ContentObserver glyphMainObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };
    
    @Override
    public void onStartListening() {
        super.onStartListening();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setObservers(true);
        updateState();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        setObservers(false);
        prefs = null;
    }

    private void updateState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (getAvailable()) {
            boolean enabled = getEnabled();
            tile.setSubtitle(enabled ?
                    getString(R.string.glyph_accessibility_quick_settings_on) :
                    getString(R.string.glyph_accessibility_quick_settings_off));
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        } else {
            tile.setSubtitle(getString(R.string.glyph_accessibility_quick_settings_unavailable));
            tile.setState(Tile.STATE_UNAVAILABLE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        setEnabled(!getEnabled());
        updateState();
    }

    private boolean getEnabled() {
        return SettingsManager.Toys.isAODToyEnabled();
    }

    private boolean getAvailable() {
        return Constants.CONTEXT != null
                && SettingsManager.isGlyphEnabled()
                && SettingsManager.isGlyphFlipEnabled();
    }

    private void setEnabled(boolean enabled) {
        SettingsManager.Toys.setAODToyEnabled(enabled);
        ServiceUtils.checkGlyphService();
    }

    private void setObservers(boolean state) {
        if (state) {
            getContentResolver().registerContentObserver(glyphMainUri, false, glyphMainObserver);
            if (prefs != null) prefs.registerOnSharedPreferenceChangeListener(listener);
        } else {
            if (glyphMainObserver != null) getContentResolver().unregisterContentObserver(glyphMainObserver);
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
