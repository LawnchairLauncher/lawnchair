/*
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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.PreferenceViewHolder
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController
import com.android.launcher3.R

class SmartspaceEventProvidersPreference(context: Context, attrs: AttributeSet?) :
        DialogPreference(context, attrs),
        ControlledPreference by ControlledPreference.Delegate(context, attrs),
        LawnchairPreferences.MutableListPrefChangeListener {

    private val providersPref = context.lawnchairPrefs.eventProviders

    init {
        updateSummary()
    }

    fun setProviders(providers: List<String>) {
        context.lawnchairPrefs.eventProviders.setAll(providers)
        context.lawnchairApp.smartspace.onProviderChanged()
    }

    private fun updateSummary() {
        val providerNames = providersPref.getAll()
                .map { LawnchairSmartspaceController.getDisplayName(context, it) }
        if (providerNames.isNotEmpty()) {
            summary = TextUtils.join(", ", providerNames)
        } else {
            setSummary(R.string.weather_provider_disabled)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val summaryView = holder.findViewById(android.R.id.summary) as TextView
        summaryView.maxLines = 1
        summaryView.ellipsize = TextUtils.TruncateAt.END
    }

    override fun onAttached() {
        super.onAttached()
        providersPref.addListener(this)
    }

    override fun onDetached() {
        super.onDetached()
        providersPref.removeListener(this)
    }

    override fun onListPrefChanged(key: String) {
        runOnMainThread {
            updateSummary()
        }
    }

    override fun getDialogLayoutResource() = R.layout.dialog_preference_recyclerview
}
