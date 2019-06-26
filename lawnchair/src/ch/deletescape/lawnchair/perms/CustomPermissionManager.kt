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

package ch.deletescape.lawnchair.perms

import android.app.Activity
import android.content.Context
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.SingletonHolder
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.R

class CustomPermissionManager private constructor(private val context: Context) {
    private var grantedPerms by context.lawnchairPrefs.StringSetPref("pref_grantedCustomPerms", emptySet())
    private var deniedPerms by context.lawnchairPrefs.StringSetPref("pref_deniedCustomPerms", emptySet())

    fun checkPermission(permission: String) = grantedPerms.contains(permission)

    fun checkOrRequestPermission(permission: String, @StringRes explanation: Int?, callback: (allowed: Boolean) -> Unit) {
        deniedPerms.forEach {
            it.d()
        }
        if (!DEBUG_PROMPT_ALWAYS) {
            if (deniedPerms.contains(permission)) {
                callback(false)
                return
            } else if (checkPermission(permission)) {
                callback(true)
                return
            }
        }
        val uiValues = MAP[permission]!!
        CustomPermissionRequestDialog
                .create(context, uiValues.first, uiValues.second, explanation)
                .onResult { allowed ->
                    if (allowed) {
                        grantedPerms += permission
                    } else {
                        deniedPerms += permission
                    }
                }
                .onResult(callback)
                .show()
    }

    // todo: add ui to allow resetting permissions
    fun resetPermission(permission: String) {
        grantedPerms -= permission
        deniedPerms -= permission
    }

    companion object : LawnchairSingletonHolder<CustomPermissionManager>(::CustomPermissionManager) {
        /**
         * Allows access to coarse, network based location
         */
        const val PERMISSION_IPLOCATE = "PERMISSION_IPLOCATE"

        private val MAP = mapOf<String, Pair<@StringRes Int, @DrawableRes Int>>(
                PERMISSION_IPLOCATE to Pair(R.string.permission_iplocate, R.drawable.ic_location)
        )

        private const val DEBUG_PROMPT_ALWAYS = false
    }
}

fun Context.checkCustomPermission(permission: String) = CustomPermissionManager.getInstance(this).checkPermission(permission)