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

package app.lawnchair.flowerpot

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import app.lawnchair.flowerpot.rules.CodeRules
import app.lawnchair.flowerpot.rules.Rules
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageUserKey

class FlowerpotApps(private val context: Context, private val pot: Flowerpot) {

    private val intentMatches = mutableSetOf<String>()
    val matches = mutableSetOf<ComponentKey>()
    private val packageMatches = mutableSetOf<PackageUserKey>()
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)

    init {
        filterApps()
    }

    private fun filterApps() {
        queryIntentMatches()
        matches.clear()
        packageMatches.clear()

        UserCache.INSTANCE.get(context).userProfiles.asSequence().forEach {
            addFromPackage(null, it)
        }
    }

    private fun addFromPackage(packageName: String?, user: UserHandle) {
        launcherApps.getActivityList(packageName, user).forEach {
            if (intentMatches.contains(it.componentName.packageName) ||
                pot.rules.contains(Rules.Package(it.componentName.packageName))
            ) {
                matches.add(ComponentKey(it.componentName, it.user))
                packageMatches.add(PackageUserKey(it.componentName.packageName, it.user))
            } else {
                for (rule in pot.rules.filterIsInstance<Rules.CodeRule>()) {
                    if (CodeRules.get(rule.rule, *rule.args).matches(it.applicationInfo)) {
                        matches.add(ComponentKey(it.componentName, it.user))
                        packageMatches.add(PackageUserKey(it.componentName.packageName, it.user))
                        break
                    }
                }
            }
        }
    }

    private fun queryIntentMatches() {
        intentMatches.clear()
        for (rule in pot.rules.filterIsInstance<Rules.IntentCategory>()) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(rule.category), 0).forEach {
                intentMatches.add(it.activityInfo.packageName)
            }
        }
        for (rule in pot.rules.filterIsInstance<Rules.IntentAction>()) {
            context.packageManager.queryIntentActivities(Intent(rule.action), 0).forEach {
                intentMatches.add(it.activityInfo.packageName)
            }
        }
    }
}
