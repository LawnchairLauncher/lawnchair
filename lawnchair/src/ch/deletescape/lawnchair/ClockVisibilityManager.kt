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

package ch.deletescape.lawnchair

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.*
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.LauncherState.SPRING_LOADED
import com.android.launcher3.anim.AnimatorSetBuilder

class ClockVisibilityManager(private val context: Context) {

    private val enabledPref by context.lawnchairPrefs.BooleanPref(
            "pref_hide_statusbar_clock", false, ::refreshHideClockState)

    var currentWorkspacePage = -1
    var clockVisibleOnHome = false
        set(value) {
            if (field != value) {
                field = value
                refreshHideClockState()
            }
        }
    var launcherIsFocused = false
        set(value) {
            if (field != value) {
                field = value
                refreshHideClockState()
            }
        }
    private var launcherState = NORMAL
        set(value) {
            if (field != value) {
                field = value
                refreshHideClockState()
            }
        }
    private var hideStatusBarClock: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                updateClockVisibility()
            }
        }

    fun onWorkspacePageChanged(workspace: Workspace, currentPage: Int) {
        if (currentWorkspacePage == currentPage) return
        currentWorkspacePage = currentPage
        clockVisibleOnHome = (workspace.getChildAt(currentPage) as CellLayout)
                .shortcutsAndWidgets.childs.any(::isHomeWidget)
    }

    private fun isHomeWidget(view: View): Boolean {
        val info = view.tag as? LauncherAppWidgetInfo ?: return false
        return info.itemType == ITEM_TYPE_CUSTOM_APPWIDGET
    }

    private fun refreshHideClockState() {
        hideStatusBarClock = clockVisibleOnHome
                             && launcherIsFocused
                             && (launcherState == NORMAL || launcherState == SPRING_LOADED)
                             && enabledPref && context.lawnchairPrefs.smartspaceTime
    }

    private fun updateClockVisibility() {
        if (!isSupported) return
        if (!Utilities.hasWriteSecureSettingsPermission(context)) return

        val resolver = context.contentResolver
        val iconBlacklist = Settings.Secure.getString(resolver, ICON_BLACKLIST)
                .let { TextUtils.split(it ?: DEFAULT_BLACKLIST, ",") }
                .toMutableSet()
        val clockEnabled = Settings.System.getInt(resolver, STATUS_BAR_CLOCK, 1) != 0
        if (hideStatusBarClock == true) {
            if (!clockEnabled) return
            if (iconBlacklist.contains(CLOCK)) return
            iconBlacklist.add(CHECK)
            iconBlacklist.add(CLOCK)
            try {
                Settings.System.putInt(resolver, STATUS_BAR_CLOCK, 0)
            } catch (e: IllegalArgumentException) {

            }
        } else {
            if (!iconBlacklist.contains(CHECK)) return
            iconBlacklist.remove(CHECK)
            iconBlacklist.remove(CLOCK)
            try {
                Settings.System.putInt(resolver, STATUS_BAR_CLOCK, 1)
            } catch (e: IllegalArgumentException) {
                
            }
        }
        val blacklistString = TextUtils.join(",", iconBlacklist)
        if (blacklistString == DEFAULT_BLACKLIST) {
            Settings.Secure.putString(context.contentResolver, ICON_BLACKLIST, null)
        } else {
            Settings.Secure.putString(context.contentResolver, ICON_BLACKLIST, blacklistString)
        }
    }

    class ClockStateHandler(private val launcher: Launcher) : LauncherStateManager.StateHandler {

        private val manager = getInstance(launcher)

        override fun setState(state: LauncherState) {
            manager.launcherState = state
        }

        override fun setStateWithAnimation(toState: LauncherState, builder: AnimatorSetBuilder,
                                           config: LauncherStateManager.AnimationConfig) {
            if (!config.playNonAtomicComponent()) return

            val fromState = launcher.stateManager.state
            builder.play(ValueAnimator.ofFloat(0f, 1f).apply {
                duration = config.duration
                addUpdateListener {
                    manager.launcherState =
                            if (animatedFraction > 0.5f) toState else fromState
                }
            })
        }
    }

    companion object : LawnchairSingletonHolder<ClockVisibilityManager>(::ClockVisibilityManager) {

        private const val ICON_BLACKLIST = "icon_blacklist"
        private const val CHECK = "lc_clock_hidden"
        private const val CLOCK = "clock"
        private const val STATUS_BAR_CLOCK = "status_bar_clock"

        private const val DEFAULT_BLACKLIST = "rotate,headset"

        val isSupported = Utilities.ATLEAST_NOUGAT
    }
}
