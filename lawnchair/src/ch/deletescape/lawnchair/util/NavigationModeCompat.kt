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

package ch.deletescape.lawnchair.util

import android.content.Context
import ch.deletescape.lawnchair.lawnchairPrefs
import android.content.ContentResolver
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import com.android.quickstep.SysUINavigationMode

class NavigationModeCompat(context: Context) {

    private var swipeUpEnabled = false
        set(value) {
            if (field != value) {
                field = value
                notifyChange()
            }
        }
    private var fullGestureMode by context.lawnchairPrefs.BooleanPref("pref_fullGestureMode", false, ::notifyChange)

    var listener: Listener? = null
    val currentMode get() = when {
        !swipeUpEnabled -> SysUINavigationMode.Mode.THREE_BUTTONS
        fullGestureMode -> SysUINavigationMode.Mode.NO_BUTTON
        else -> SysUINavigationMode.Mode.TWO_BUTTONS
    }

    init {
        SwipeUpGestureEnabledSettingObserver(context.contentResolver).register()
    }

    private fun notifyChange() {
        listener?.onNavigationModeChange()
    }

    private inner class SwipeUpGestureEnabledSettingObserver(
            private val resolver: ContentResolver) :
            ContentObserver(Handler()) {

        private val defaultValue: Int = if (getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME)) 1 else 0

        private val value: Boolean
            get() = Settings.Secure.getInt(resolver, SWIPE_UP_SETTING_NAME, defaultValue) == 1

        fun register() {
            resolver.registerContentObserver(Settings.Secure.getUriFor(SWIPE_UP_SETTING_NAME),
                                             false, this)
            swipeUpEnabled = value
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            swipeUpEnabled = value
        }

        override fun deliverSelfNotifications(): Boolean {
            return true
        }
    }

    interface Listener {

        fun onNavigationModeChange()
    }

    companion object : LawnchairSingletonHolder<NavigationModeCompat>(::NavigationModeCompat) {

        private const val SWIPE_UP_ENABLED_DEFAULT_RES_NAME = "config_swipe_up_gesture_default"

        private const val SWIPE_UP_SETTING_NAME = "swipe_up_to_switch_apps_enabled"

        private fun getSystemBooleanRes(resName: String): Boolean {
            val res = Resources.getSystem()
            val resId = res.getIdentifier(resName, "bool", "android")

            return if (resId != 0) {
                res.getBoolean(resId)
            } else {
                false
            }
        }
    }
}
