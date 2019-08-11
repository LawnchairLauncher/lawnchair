/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.smartspace

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.DiscoveryBounce

// TODO: add event for after installing a new Icon Pack with apply activity as intent
class OnboardingProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller),
        SharedPreferences.OnSharedPreferenceChangeListener {

    private val deviceKeys = arrayOf(PREF_HAS_OPENED_SETTINGS)
    private val prefKeys = arrayOf(DiscoveryBounce.HOME_BOUNCE_SEEN)

    private val devicePrefs = Utilities.getDevicePrefs(context)
    private val prefs = Utilities.getPrefs(context)

    override fun startListening() {
        super.startListening()
        devicePrefs.registerOnSharedPreferenceChangeListener(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        update()
    }

    override fun stopListening() {
        super.stopListening()
        devicePrefs.unregisterOnSharedPreferenceChangeListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val keys = when (sharedPreferences) {
            devicePrefs -> deviceKeys
            prefs -> prefKeys
            else -> return
        }
        if (key in keys) {
            update()
        }
    }

    private fun update() {
        val card = when {
            !prefs.getBoolean(DiscoveryBounce.HOME_BOUNCE_SEEN, false) -> LawnchairSmartspaceController.CardData(
                    lines = listOf(LawnchairSmartspaceController.Line(context, R.string.onboarding_swipe_up)))
            !devicePrefs.getBoolean(PREF_HAS_OPENED_SETTINGS, false) -> LawnchairSmartspaceController.CardData(
                    icon = null,
                    title = context.getString(
                            R.string.onbording_settings_title,
                            context.getString(R.string.derived_app_name)),
                    subtitle = context.getString(R.string.onbording_settings_summary),
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            Intent(context, SettingsActivity::class.java),
                            0))
            else -> null
        }
        updateData(null, card)
    }

    companion object {
        const val PREF_HAS_OPENED_SETTINGS = "pref_hasOpenedSettings"
    }
}