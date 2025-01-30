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
import app.lawnchair.flowerpot.rules.Rules
import com.android.launcher3.model.data.AppInfo

class FlowerpotApps(private val context: Context, private val pot: Flowerpot) {
    private val intentMatches = mutableSetOf<String>()
    val categorizedApps = mutableMapOf<String, MutableList<AppInfo>>()

    init {
        populateIntentMatches()
    }

    fun updateAppList(appList: List<AppInfo?>?) {
        categorizedApps.clear()

        val validAppList = appList?.filterNotNull() ?: emptyList()

        val categoryTitle = pot.displayName

        val categorizedInThisPot = mutableSetOf<String>()

        validAppList
            .mapNotNull { it.targetPackage }
            .filter { packageName ->
                categorizedInThisPot.add(packageName) &&
                    (packageName in intentMatches || pot.rules.contains(Rules.Package(packageName)))
            }
            .forEach { packageName ->
                categorizedApps.getOrPut(categoryTitle) { mutableListOf() }
                    .add(validAppList.first { it.targetPackage == packageName })
            }
    }

    private fun populateIntentMatches() {
        intentMatches.clear()

        pot.rules.forEach { rule ->
            val intent = when (rule) {
                is Rules.IntentCategory -> Intent(Intent.ACTION_MAIN).addCategory(rule.category)
                is Rules.IntentAction -> Intent(rule.action)
                else -> return@forEach
            }

            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNullTo(intentMatches) { it.activityInfo?.packageName }
        }
    }
}
