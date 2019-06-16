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

package ch.deletescape.lawnchair.flowerpot

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import ch.deletescape.lawnchair.flowerpot.rules.CodeRule
import ch.deletescape.lawnchair.flowerpot.rules.Rule
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey

class FlowerpotApps(private val context: Context, private val pot: Flowerpot) : LauncherAppsCompat.OnAppsChangedCallbackCompat {

    private val launcherApps = LauncherAppsCompat.getInstance(context)
    private val intentMatches = mutableSetOf<String>()
    val matches = mutableSetOf<ComponentKey>()

    init {
        filterApps()
        launcherApps.addOnAppsChangedCallback(this)
    }

    private fun filterApps() {
        matches.clear()
        intentMatches.clear()
        for (rule in pot.rules.filterIsInstance<Rule.IntentCategory>()) {
            context.packageManager.queryIntentActivities(Intent().addCategory(rule.category), 0).forEach {
                intentMatches.add(it.activityInfo.packageName)
            }
        }
        for (rule in pot.rules.filterIsInstance<Rule.IntentAction>()) {
            context.packageManager.queryIntentActivities(Intent(rule.action), 0).forEach {
                intentMatches.add(it.activityInfo.packageName)
            }
        }
        launcherApps.getActivityList(null, Process.myUserHandle()).forEach {
            if (intentMatches.contains(it.componentName.packageName)
                    || pot.rules.contains(Rule.Package(it.componentName.packageName))) {
                matches.add(ComponentKey(it.componentName, it.user))
            } else {
                for (rule in pot.rules.filterIsInstance<Rule.CodeRule>()) {
                    if (CodeRule.get(rule.rule, *rule.args).matches(it.applicationInfo)) {
                        matches.add(ComponentKey(it.componentName, it.user))
                        break
                    }
                }
            }
        }
    }

    override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
        filterApps()
    }

    override fun onPackageAdded(packageName: String?, user: UserHandle?) {
        filterApps()
    }

    override fun onPackageChanged(packageName: String?, user: UserHandle?) {
        filterApps()
    }

    override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
        filterApps()
    }

    override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
        filterApps()
    }

    override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
        filterApps()
    }

    override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) {
        filterApps()
    }

    override fun onShortcutsChanged(packageName: String?, shortcuts: MutableList<ShortcutInfoCompat>?, user: UserHandle?) {
        filterApps()
    }
}
