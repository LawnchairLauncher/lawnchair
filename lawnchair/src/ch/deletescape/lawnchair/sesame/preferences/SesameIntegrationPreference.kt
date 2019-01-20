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

package ch.deletescape.lawnchair.sesame.preferences

import android.content.Context
import android.os.Parcelable
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.Toast
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.preferences.ResumablePreference
import ch.deletescape.lawnchair.preferences.StyledSwitchPreferenceCompat
import ch.deletescape.lawnchair.sesame.Sesame
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper
import ninja.sesame.lib.bridge.v1.SesameFrontend

class SesameIntegrationPreference(context: Context, attrs: AttributeSet?): StyledSwitchPreferenceCompat(context, attrs), ResumablePreference {
    private val isInstalled: Boolean by lazy { PackageManagerHelper.isAppEnabled(context.packageManager, Sesame.PACKAGE, 0) }

    init {
        isPersistent = false // !important
        if (isInstalled) {
            updateValue()
        } else {
            updateSummary()
        }
    }

    private fun updateSummary() {
        summary = context.getString(when {
            !isInstalled -> R.string.sesame_upsell
            isChecked -> R.string.pref_sesame_enabled
            else -> R.string.pref_sesame_disabled
        })
    }

    private fun updateValue() {
        super.setChecked(SesameFrontend.getIntegrationState(context))
        updateSummary()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (!isInstalled) {
            checkableView?.isVisible = false
        }
    }

    override fun setChecked(checked: Boolean) {
        SesameFrontend.setIntegrationState(context, checked)
    }

    override fun onResume() {
        updateValue()
    }

    override fun onClick() {
        if (isInstalled) {
            super.onClick()
        } else {
            context.startActivity(PackageManagerHelper(context).getMarketIntent(Sesame.PACKAGE))
        }
    }

    override fun shouldDisableDependents(): Boolean {
        return !isInstalled || super.shouldDisableDependents()
    }
}