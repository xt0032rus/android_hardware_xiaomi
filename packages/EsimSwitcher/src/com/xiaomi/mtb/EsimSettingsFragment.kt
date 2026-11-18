/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.mtb

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EsimSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "EsimSettingsFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    private val esimController by lazy { EsimController.getInstance(requireContext()) }
    private val radioController by lazy { RadioConfigEsimController(requireContext()) }

    private val switchBar by lazy { findPreference<MainSwitchPreference>("esim_enable")!! }
    private val footerPref by lazy { findPreference<FooterPreference>("esim_footer")!! }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_esim, rootKey)

        switchBar.onPreferenceChangeListener = this
        footerPref.title = getString(R.string.esim_footer_note)
        if (resources.getBoolean(R.bool.config_use_radio_config)) {
            switchBar.isEnabled = false
            val controller = radioController
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching(controller::getEsimEnabled) }
                result.onSuccess { enabled ->
                    switchBar.isChecked = enabled
                    switchBar.isEnabled = true
                }.onFailure {
                    Log.w(TAG, "Unable to read the SIM type", it)
                    footerPref.setTitle(R.string.esim_status_unavailable)
                }
            }
        } else {
            switchBar.isChecked = esimController.getEsimEnabled()
            switchBar.isEnabled = true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference == switchBar) {
            val isChecked = newValue as Boolean
            if (DEBUG) Log.d(TAG, "onPreferenceChange: $isChecked")
            if (resources.getBoolean(R.bool.config_use_radio_config)) {
                switchRadioSimType(isChecked)
                return false
            }
            if (esimController.getEsimActive()) {
                if (!isChecked) showActiveEsimWarning()
                return false
            }
            esimController.setEsimEnabled(isChecked)
        }
        return true
    }

    private fun switchRadioSimType(enabled: Boolean) {
        switchBar.isEnabled = false
        val controller = radioController
        val subscriptions = esimController
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (subscriptions.getEsimActive()) {
                        false
                    } else {
                        controller.setEsimEnabled(enabled)
                        true
                    }
                }
            }
            result.onSuccess { changed ->
                if (changed) {
                    switchBar.isChecked = enabled
                    footerPref.setTitle(R.string.esim_footer_note)
                } else if (!enabled) {
                    showActiveEsimWarning()
                }
            }.onFailure {
                Log.w(TAG, "Unable to change the SIM type", it)
                footerPref.setTitle(R.string.esim_switch_failed)
            }
            switchBar.isEnabled = true
        }
    }

    private fun showActiveEsimWarning() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.esim_warning_title)
            .setMessage(R.string.esim_warning_message)
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(false)
            .show()
    }
}
