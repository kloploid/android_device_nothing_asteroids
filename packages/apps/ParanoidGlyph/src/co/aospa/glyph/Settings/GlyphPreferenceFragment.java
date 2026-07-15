/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Settings;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.widget.SettingsPreferenceGroupAdapter;

public abstract class GlyphPreferenceFragment extends PreferenceFragmentCompat {

    // Draws preferences as rounded groups; without this adapter Settings screens render flat.
    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new SettingsPreferenceGroupAdapter(preferenceScreen);
    }
}
