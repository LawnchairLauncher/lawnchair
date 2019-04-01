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

package ch.deletescape.lawnchair.predictions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import com.android.quickstep.TouchInteractionService
import com.google.android.apps.nexuslauncher.CustomAppPredictor
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader
import com.google.android.apps.nexuslauncher.util.ComponentKeyMapper

// TODO: Add support for shortcuts actions to create an actions backport/compat
/**
 * Fallback app predictor for users without quickswitch
 */
open class LawnchairEventPredictor(private val context: Context): CustomAppPredictor(context) {

    private val packageManager by lazy { context.packageManager }
    private val launcher by lazy { LauncherAppState.getInstance(context).launcher }
    private val predictionsHeader by lazy { launcher.appsView.floatingHeaderView as PredictionsFloatingHeader }

    private val devicePrefs = Utilities.getDevicePrefs(context)
    private val appsList = CountRankedArrayPreference(devicePrefs, "recent_app_launches", 250)

    override fun updatePredictions() {
        super.updatePredictions()
        if (isPredictorEnabled) {
            predictionsHeader.setPredictedApps(isPredictorEnabled, predictions)
        }
    }

    override fun logAppLaunch(v: View?, intent: Intent?, user: UserHandle?) {
        super.logAppLaunch(v, intent, user)
        if (isPredictorEnabled && recursiveIsDrawer(v)) {
            val componentInfo = intent?.component
            if (componentInfo != null && mAppFilter.shouldShowApp(componentInfo, user)) {
                clearRemovedComponents()
                appsList.add(ComponentKey(componentInfo, user).toString())
                updatePredictions()
            }
        }
    }

    private fun clearRemovedComponents() {
        appsList.removeAll {
            val component = getComponentFromString(it).componentKey?.componentName ?: return@removeAll true
            try {
                packageManager.getActivityInfo(component, 0)
                false
            } catch (ignored: PackageManager.NameNotFoundException) {
                val intent = packageManager.getLaunchIntentForPackage(component.packageName)
                if (intent != null) {
                    val componentInfo = intent.component
                    if (componentInfo != null) {
                        val key = ComponentKey(componentInfo, Process.myUserHandle())
                        appsList.replace(it, key.toString())
                        return@removeAll false
                    }
                }
                true
            }
        }
    }

    override fun getPredictions(): MutableList<ComponentKeyMapper> {
        return (if (isPredictorEnabled) {
            clearRemovedComponents()

            val rankedSet = appsList.getRanked()
            rankedSet.map { getComponentFromString(it) }.filter { !isHiddenApp(context, it.componentKey) }.toMutableList()
        } else mutableListOf()).apply {
            for (placeholder in PLACE_HOLDERS) {
                if (size >= MAX_PREDICTIONS) {
                    break
                }
                val intent = packageManager.getLaunchIntentForPackage(placeholder)
                if (intent != null) {
                    val component = intent.component
                    if (component != null) {
                        val key = ComponentKey(component, Process.myUserHandle())

                        if (!appsList.contains(key.toString()) && !isHiddenApp(context, key)) {
                            add(ComponentKeyMapper(context, key))
                        }
                    }
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == SettingsActivity.SHOW_PREDICTIONS_PREF) {
            if (!isPredictorEnabled) {
                appsList.clear()
            }
        }
    }

    override fun isPredictorEnabled(): Boolean {
        // Only enable as fallback
        return super.isPredictorEnabled() && !TouchInteractionService.isConnected()
    }

    /**
     * A ranked list with roll over to get/store currently relevant events and rank them by occurence
     */
    inner class CountRankedArrayPreference(private val prefs: SharedPreferences, private val key: String, private val maxSize: Int = -1, private val delimiter: String = ";") {
        private var list = load()

        fun getRanked() : Set<String> = list.distinct().sortedBy { value -> list.count { it == value } }.reversed().toSet()

        fun add(string: String) {
            list.add(0, string)
            if (maxSize >= 0 && list.size > maxSize) {
                list = list.drop(maxSize).toMutableList()
            }
            save()
        }

        fun clear() {
            list.clear()
            prefs.edit().remove(key).apply()
        }
        fun removeAll(filter: (String) -> Boolean) = list.removeAll(filter)
        fun replace(filter: String, replacement: String) = list.replaceAll { if (it == filter) replacement else it }
        fun contains(element: String) = list.contains(element)

        private fun load() = (prefs.getString(key, "")?: "").split(delimiter).toMutableList()
        private fun save() {
            val strValue = list.joinToString(delimiter)
            prefs.edit().putString(key, strValue).apply()
        }
    }
}