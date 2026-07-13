/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.asteroids;

import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class ActionButtonSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String[] KEYS = {
        KeyHandler.KEY_SHORT, KeyHandler.KEY_LONG, KeyHandler.KEY_DOUBLE
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.action_button_settings, rootKey);
        for (String key : KEYS) {
            ListPreference pref = findPreference(key);
            if (pref == null) continue;
            int def = Integer.parseInt(pref.getValue());
            int cur = Settings.System.getInt(
                    requireContext().getContentResolver(), key, def);
            pref.setValue(Integer.toString(cur));
            pref.setSummary(pref.getEntry());
            pref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        int value = Integer.parseInt((String) newValue);
        Settings.System.putInt(requireContext().getContentResolver(), key, value);
        ListPreference pref = (ListPreference) preference;
        int idx = pref.findIndexOfValue((String) newValue);
        if (idx >= 0) pref.setSummary(pref.getEntries()[idx]);
        return true;
    }
}
