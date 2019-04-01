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
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnThread
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.*
import com.android.launcher3.graphics.LauncherIcons
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.Provider
import com.android.quickstep.TouchInteractionService
import com.google.android.apps.nexuslauncher.CustomAppPredictor
import com.google.android.apps.nexuslauncher.allapps.Action
import com.google.android.apps.nexuslauncher.allapps.ActionsController
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader
import com.google.android.apps.nexuslauncher.util.ComponentKeyMapper
import org.json.JSONObject

// TODO: Fix action icons being loaded too early, leading to f*cked icons when using sesame
/**
 * Fallback app predictor for users without quickswitch
 */
open class LawnchairEventPredictor(private val context: Context): CustomAppPredictor(context) {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val packageManager by lazy { context.packageManager }
    private val launcher by lazy { LauncherAppState.getInstance(context).launcher }
    private val predictionsHeader by lazy { launcher.appsView.floatingHeaderView as PredictionsFloatingHeader }
    private val actionsRow by lazy { predictionsHeader.actionsRowView }
    private val deepShortcutManager by lazy { DeepShortcutManager.getInstance(context) }

    private val handlerThread by lazy { HandlerThread("event-predictor").apply { start() }}
    private val handler by lazy { Handler(handlerThread.looper) }

    private val devicePrefs = Utilities.getDevicePrefs(context)
    private val appsList = CountRankedArrayPreference(devicePrefs, "recent_app_launches", 250)
    private val actionList = CountRankedArrayPreference(devicePrefs, "recent_shortcut_launches")
    private val isActionsEnabled get() = !(PackageManagerHelper.isAppEnabled(context.packageManager, ACTIONS_PACKAGE, 0) && TouchInteractionService.isConnected() && ActionsController.get(context).actions.size > 0) && prefs.showActions

    private var actionsCache = listOf<String>()

    override fun updatePredictions() {
        super.updatePredictions()
        if (isPredictorEnabled) {
            predictionsHeader.setPredictedApps(isPredictorEnabled, predictions)
        }
    }

    override fun updateActions() {
        super.updateActions()
        if (isActionsEnabled) {
            actionsRow.onUpdated(getActions())
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

    override fun logShortcutLaunch(intent: Intent, info: ItemInfo) {
        super.logShortcutLaunch(intent, info)
        if (isActionsEnabled && info is ShortcutInfo && info.shortcutInfo != null) {
            runOnThread(handler) {
                cleanActions()

                val badge = info.shortcutInfo.getBadgePackage(context)
                actionList.add(actionToString(info.shortcutInfo.id, badge, badge))
                val new = actionList.getRanked().take(ActionsController.MAX_ITEMS)
                if (new != actionsCache) {
                    actionsCache = new
                    runOnMainThread {
                        updateActions()
                    }
                } else {
                    Log.d("EventPredictor", "Stayed same")
                }
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

    fun getActions(): ArrayList<Action> {
        cleanActions()
        return ArrayList(actionList.getRanked().take(ActionsController.MAX_ITEMS).mapIndexedNotNull { index, s -> actionFromString(s, index.toLong()) })
    }

    fun cleanActions() {
        actionList.removeAll { actionFromString(it) == null }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == SettingsActivity.SHOW_PREDICTIONS_PREF) {
            if (!isPredictorEnabled) {
                appsList.clear()
            }
        } else if (key == SettingsActivity.SHOW_ACTIONS_PREF) {
            if (!isActionsEnabled) {
                actionList.clear()
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

    private fun actionToString(id: String, publisher: String, badge: String) = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_PUBLISHER, publisher)
        put(KEY_BADGE, badge)
    }.toString()

    private fun actionFromString(string: String, position: Long = 0): Action? {
        try {
            Log.d("EventPredictor", string)
            val obj = JSONObject(string)
            val id = obj.getString(KEY_ID)
            //val expiration = obj.getLong(KEY_EXPIRATION)
            val publisher = obj.getString(KEY_PUBLISHER)
            val badge = obj.getString(KEY_BADGE)
            //val pos = obj.getLong(KEY_POSITION)
            val list = deepShortcutManager.queryForFullDetails(publisher, listOf(id), Process.myUserHandle())
            if (!list.isEmpty()) {
                val shortcutInfo = list[0]
                val info = ShortcutInfo(shortcutInfo, context)
                val li = LauncherIcons.obtain(context)
                li.createShortcutIcon(shortcutInfo, true, null).applyTo(info)
                li.recycle()
                val appName = try {
                    packageManager.getApplicationInfo(badge, 0).loadLabel(packageManager)
                } catch (ignore: PackageManager.NameNotFoundException) {
                    try {
                        packageManager.getApplicationInfo(publisher, 0).loadLabel(packageManager)
                    } catch (ignore: PackageManager.NameNotFoundException) {
                        context.getString(R.string.package_state_unknown)
                    }
                }
                return Action(
                        id,
                        id,
                        Long.MAX_VALUE,
                        publisher,
                        badge,
                        appName,
                        shortcutInfo,
                        info,
                        position
                )
            }
        } catch (ignore: Throwable) { }
        return null
    }

    companion object {
        const val ACTIONS_PACKAGE = "com.google.android.as"

        const val KEY_ID = "id"
        const val KEY_EXPIRATION = "expiration"
        const val KEY_PUBLISHER = "publisher"
        const val KEY_BADGE = "badge"
        const val KEY_POSITION = "position"
    }
}