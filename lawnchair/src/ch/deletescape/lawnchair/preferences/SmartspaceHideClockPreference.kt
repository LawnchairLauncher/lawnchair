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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.Preference
import android.util.AttributeSet
import android.widget.Toast
import ch.deletescape.lawnchair.ClockVisibilityManager
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.topjohnwu.superuser.Shell

class SmartspaceHideClockPreference(context: Context, attrs: AttributeSet?) :
        StyledSwitchPreferenceCompat(context, attrs) {

    init {
        isVisible = ClockVisibilityManager.isSupported
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restoreValue, defaultValue)
        if (!Utilities.hasWriteSecureSettingsPermission(context)) {
            isChecked = false
        }
    }

    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        isEnabled = dependency.isEnabled
        val switchDependency = dependency as? StyledSwitchPreferenceCompat ?: return
        isVisible = ClockVisibilityManager.isSupported && switchDependency.isChecked
    }

    override fun onClick() {
        if (!Utilities.hasWriteSecureSettingsPermission(context)) {
            Shell.su("pm grant ${BuildConfig.APPLICATION_ID} ${android.Manifest.permission.WRITE_SECURE_SETTINGS}").submit {
                if (Utilities.hasWriteSecureSettingsPermission(context)) {
                    isChecked = true
                } else {
                    Toast.makeText(context, R.string.root_access_required, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            super.onClick()
        }
    }
}
