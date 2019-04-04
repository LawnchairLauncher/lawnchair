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

import android.annotation.TargetApi
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities

abstract class DefaultHomeCompat(protected val activity: Activity) {

    abstract fun isDefaultHome(): Boolean

    fun requestDefaultHome() {
        activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    class DefaultHomeCompatVNMr1(activity: Activity) : DefaultHomeCompat(activity) {

        override fun isDefaultHome(): Boolean {
            return BuildConfig.APPLICATION_ID == resolveDefaultHome()
        }

        private fun resolveDefaultHome(): String? {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info = activity.packageManager
                    .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            return info?.activityInfo?.packageName
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    class DefaultHomeCompatVQ(activity: Activity) : DefaultHomeCompat(activity) {

        private val roleManager = activity.getSystemService(RoleManager::class.java)!!

        override fun isDefaultHome(): Boolean {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }

    companion object {

        @JvmStatic
        fun create(activity: Activity): DefaultHomeCompat {
            if (Utilities.ATLEAST_Q) {
                return DefaultHomeCompatVQ(activity)
            }
            return DefaultHomeCompatVNMr1(activity)
        }
    }
}
