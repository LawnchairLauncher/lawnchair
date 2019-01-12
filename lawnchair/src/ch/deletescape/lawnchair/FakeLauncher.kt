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

package ch.deletescape.lawnchair

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class FakeLauncher : Activity()

fun changeDefaultHome(context: Context) {
    val pm = context.packageManager
    val fakeLauncher = ComponentName(context, FakeLauncher::class.java)

    pm.setComponentEnabledSetting(fakeLauncher, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

    val picker = Intent(Intent.ACTION_MAIN)
    picker.addCategory(Intent.CATEGORY_HOME)
    picker.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(picker)

    pm.setComponentEnabledSetting(fakeLauncher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
}
