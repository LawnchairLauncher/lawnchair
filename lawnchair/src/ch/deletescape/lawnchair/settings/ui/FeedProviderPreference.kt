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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.preference.DialogPreference
import ch.deletescape.lawnchair.FeedBridge
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.getIcon
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.controllers.FeedProviderController
import ch.deletescape.lawnchair.settings.ui.controllers.WeatherIconPackController
import ch.deletescape.lawnchair.smartspace.weather.icons.WeatherIconManager
import com.android.launcher3.R

class FeedProviderPreference(context: Context, attrs: AttributeSet? = null) :
        DialogPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {
    private val prefs = context.lawnchairPrefs
    private val current get() = providers(context).firstOrNull { it.packageName == prefs.feedProvider } ?: providers(context)[0]

    init {
        layoutResource = R.layout.pref_with_preview_icon
        dialogLayoutResource = R.layout.pref_dialog_icon_pack
    }

    override fun onAttached() {
        super.onAttached()

        prefs.addOnPreferenceChangeListener(this, KEY)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        isVisible = FeedProviderController(context).isVisible
        updateSummaryAndIcon()
    }

    private fun updateSummaryAndIcon() {
        icon = current.icon
        summary = current.name
    }

    override fun onDetached() {
        super.onDetached()

        prefs.removeOnPreferenceChangeListener(this, KEY)
    }

    companion object {
        const val KEY = "pref_feedProvider"

        fun providers(context: Context) = listOf(ProviderInfo(
                packageName = "",
                name = context.getString(R.string.theme_default),
                icon = context.getIcon())) +
                                          FeedBridge.getAvailableProviders(context).map {
                                              ProviderInfo(it.loadLabel(context.packageManager).toString(), it.packageName, it.loadIcon(context.packageManager))
                                          }
    }

    data class ProviderInfo(
            val name: String,
            val packageName: String,
            val icon: Drawable?)
}