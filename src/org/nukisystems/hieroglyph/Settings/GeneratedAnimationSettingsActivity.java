
package org.nukisystems.hieroglyph.Settings;

import androidx.fragment.app.Fragment;
import android.os.Bundle;

import org.nukisystems.hieroglyph.Constants.Constants;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class GeneratedAnimationSettingsActivity extends CollapsingToolbarBaseActivity {

    private GeneratedAnimationSettingsFragment mGeneratedAnimationSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }

        String type = getIntent().getStringExtra("type");
        if (type == null) {
            finish();
            return;
        }

        Bundle args = new Bundle();
        args.putString("type", type);


        Fragment fragment = getSupportFragmentManager().findFragmentById(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame
        );
        if (fragment == null) {
            mGeneratedAnimationSettingsFragment = new GeneratedAnimationSettingsFragment();
            mGeneratedAnimationSettingsFragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .add(
                            com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            mGeneratedAnimationSettingsFragment
                    )
                    .commit();
        } else {
            mGeneratedAnimationSettingsFragment = (GeneratedAnimationSettingsFragment) fragment;
        }
    }
}
