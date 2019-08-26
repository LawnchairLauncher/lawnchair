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

import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import ch.deletescape.lawnchair.sesame.Sesame
import ch.deletescape.lawnchair.sesame.SesameShortcutInfo
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.*
import com.android.launcher3.graphics.LauncherIcons
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import com.google.android.apps.nexuslauncher.CustomAppPredictor
import com.google.android.apps.nexuslauncher.allapps.Action
import com.google.android.apps.nexuslauncher.allapps.ActionView
import com.google.android.apps.nexuslauncher.allapps.ActionsController
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader
import com.google.android.apps.nexuslauncher.util.ComponentKeyMapper
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1.ShortcutType
import org.json.JSONObject
import java.util.HashSet
import java.util.concurrent.TimeUnit

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
    private val phonesList = CountRankedArrayPreference(devicePrefs, "plugged_app_launches", 20)
    private val actionList = CountRankedArrayPreference(devicePrefs, "recent_shortcut_launches", 100)
    open val isActionsEnabled get() = !(PackageManagerHelper.isAppEnabled(context.packageManager, ACTIONS_PACKAGE, 0) && ActionsController.get(context).actions.size > 0) && prefs.showActions

    private var actionsCache = listOf<String>()

    private val sesameComponent = ComponentName.unflattenFromString("ninja.sesame.app.edge/.activities.MainActivity")

    /**
     * Time at which headphones have been plugged in / connected. 0 if disconnected, -1 before initialized
     */
    private var phonesConnectedAt = -1L
        set(value) {
            field = value
            if (value != -1L) {
                phonesLaunches = 0
                updatePredictions()
                if (value != 0L) {
                    // Ensure temporary predictions get removed again after
                    handler.postDelayed(this::updatePredictions, DURATION_RECENTLY)
                }
            }
        }
    /**
     * Whether headphones have just been plugged in / connected (in the last two minutes)
     * TODO: Is two minutes appropriate or do we want to increase this?
     */
    private val phonesJustConnected get() = phonesConnectedAt > 0 && SystemClock.uptimeMillis() in phonesConnectedAt until phonesConnectedAt + DURATION_RECENTLY
    /**
     * Whether or not the current app launch is relevant for headphone suggestions or not
     */
    private val relevantForPhones get() = phonesLaunches < 2 && phonesJustConnected
    /**
     * Number of launches recorded since headphones were connected
     */
    private var phonesLaunches = 0
    private val phonesStateChangeReceiver by lazy {
        object : BroadcastReceiver() {
            private var firstReceive = true

            override fun onReceive(context: Context, intent: Intent) {
                if (!firstReceive) {
                    phonesConnectedAt = when (intent.action) {
                        Intent.ACTION_HEADSET_PLUG -> {
                            when (intent.getIntExtra("state", -1)) {
                                1 -> SystemClock.currentThreadTimeMillis()
                                else -> 0
                            }
                        }
                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                            when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                                2 -> SystemClock.currentThreadTimeMillis()
                                else -> 0
                            }
                        }
                        else -> 0
                    }
                }
                firstReceive = false
            }
        }
    }

    init {
        if (isPredictorEnabled && !Sesame.isAvailable(context)) {
            setupBroadcastReceiver()
        }
    }

    private fun setupBroadcastReceiver() {
        context.registerReceiver( phonesStateChangeReceiver,
                IntentFilter(Intent.ACTION_HEADSET_PLUG).apply {
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                }, null, handler)
    }

    private fun tearDownBroadcastReceiver() {
        try {
            context.unregisterReceiver(phonesStateChangeReceiver)
        } catch(ignored: Exception) {
            // there is apparently no way to reliably check if a receiver is actually registered and
            // an exception is thrown when trying to unregister one that never was
        }
    }

    override fun updatePredictions() {
        super.updatePredictions()
        if (isPredictorEnabled) {
            runOnMainThread {
                predictionsHeader.setPredictedApps(isPredictorEnabled, predictions)
            }
        }
    }

    override fun updateActions() {
        super.updateActions()
        if (isActionsEnabled) {
            getActions {
                actions ->
                runOnMainThread {
                    actionsRow.onUpdated(actions)
                }
            }
        }
    }

    override fun logAppLaunch(v: View?, intent: Intent?, user: UserHandle?) {
        super.logAppLaunch(v, intent, user)
        logAppLaunchImpl(v, intent, user ?: Process.myUserHandle())
    }

    private fun logAppLaunchImpl(v: View?, intent: Intent?, user: UserHandle) {
        if (isPredictorEnabled) {
            if (Sesame.isAvailable(context)) {
              updatePredictions()
            } else if (v !is ActionView && intent?.component != null && mAppFilter.shouldShowApp(intent.component, user)) {
                clearRemovedComponents()

                var changed = false
                val key = ComponentKey(intent.component, user).toString()
                if (recursiveIsDrawer(v)) {
                    appsList.add(key)
                    changed = true
                }
                if (relevantForPhones) {
                    phonesList.add(key)
                    phonesLaunches++
                    changed = true
                }

                if (changed) {
                    updatePredictions()
                }
            }
        }
    }

    override fun logShortcutLaunch(intent: Intent, info: ItemInfo) {
        super.logShortcutLaunch(intent, info)
        if (isActionsEnabled) {
            if (Sesame.isAvailable(context)) {
                runOnMainThread {
                    updateActions()
                }
            } else if (info is ShortcutInfo && info.shortcutInfo != null) {
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
    }

    // TODO: There must be a better, more elegant way to concatenate these lists
    override fun getPredictions(): MutableList<ComponentKeyMapper> {
        return if(isPredictorEnabled && Sesame.isAvailable(context)) {
            val lst = SesameFrontend.getUsageCounts(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3),
                    System.currentTimeMillis(), MAX_PREDICTIONS * 2,
                    arrayOf(ShortcutType.APP_COMPONENT))
            val user = Process.myUserHandle()
            val fullList = lst?.mapNotNull { it.shortcut.componentName }
                                   ?.map { getComponentFromString(it) }
                                   ?.filterNot { it.componentKey.componentName == sesameComponent }
                                   ?.filterNot { isHiddenApp(context, it.componentKey) }
                                   ?.filter {
                                       mAppFilter.shouldShowApp(it.componentKey.componentName, user)
                                   }?.toMutableList() ?: mutableListOf<ComponentKeyMapper>()
            if (fullList.size < MAX_PREDICTIONS) {
                fullList.addAll(
                        PLACE_HOLDERS.mapNotNull { packageManager.getLaunchIntentForPackage(it)?.component }
                                .map { ComponentKeyMapper(context, ComponentKey(it, user)) }
                                .filterNot { fullList.contains(it) }
                )
            }
            fullList.take(MAX_PREDICTIONS).toMutableList()
        } else if (isPredictorEnabled) {
            clearRemovedComponents()
            val user = Process.myUserHandle()
            val appList = if (phonesJustConnected) phonesList.getRanked().take(MAX_HEADPHONE_SUGGESTIONS).toMutableList() else mutableListOf()
            appList.addAll(appsList.getRanked().filterNot { appList.contains(it) }.take(MAX_PREDICTIONS - appList.size))
            val fullList = appList.map { getComponentFromString(it) }
                    .filterNot { isHiddenApp(context, it.componentKey) }.toMutableList()
            if (fullList.size < MAX_PREDICTIONS) {
                fullList.addAll(
                        PLACE_HOLDERS.mapNotNull { packageManager.getLaunchIntentForPackage(it)?.component }
                                .map { ComponentKeyMapper(context, ComponentKey(it, user)) }
                )
            }
            fullList.take(MAX_PREDICTIONS).toMutableList()
        } else mutableListOf()
    }

    open fun setHiddenAction(action: String) {
        val hiddenActionSet = HashSet(Utilities.getLawnchairPrefs(context).hiddenPredictActionSet)
        hiddenActionSet.add(action)
        Utilities.getLawnchairPrefs(context).hiddenPredictActionSet = hiddenActionSet
    }

    private fun isHiddenAction(action: String): Boolean {
        return HashSet(Utilities.getLawnchairPrefs(context).hiddenPredictActionSet).contains(action)
    }

    private fun getFilteredActionList(actionList: ArrayList<Action>): ArrayList<Action> {
        val arrayList = ArrayList<Action>()
        for (action: Action in actionList) {
            if (!isHiddenAction(action.toString())) {
                arrayList.add(action)
            }
        }
        return arrayList
    }

    // TODO: Extension function?
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
        phonesList.removeAll {
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
                        phonesList.replace(it, key.toString())
                        return@removeAll false
                    }
                }
                true
            }
        }
    }

    fun getActions(callback: (ArrayList<Action>) -> Unit) {
        cleanActions()
        if (!isActionsEnabled) {
            callback(ArrayList())
            return
        }
        runOnUiWorkerThread {
            if (Sesame.isAvailable(context)) {
                val lst = SesameFrontend.getUsageCounts(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4),
                        System.currentTimeMillis(), ActionsController.MAX_ITEMS + 3,
                        arrayOf(ShortcutType.DEEP_LINK, ShortcutType.CONTACT))
                if (lst != null) {
                    callback(getFilteredActionList(ArrayList(lst.mapIndexedNotNull { index, usage ->
                        val shortcut = SesameShortcutInfo(context, usage.shortcut)
                        actionFromString(
                                actionToString(shortcut.id, shortcut.getBadgePackage(context), shortcut.`package`),
                                index.toLong())
                    })))
                    return@runOnUiWorkerThread
                }
            }
            callback(getFilteredActionList(ArrayList(actionList.getRanked()
                                                             .take(ActionsController.MAX_ITEMS)
                                                             .mapIndexedNotNull { index, s ->
                                                                 actionFromString(s, index.toLong())
                                                             })))
        }
    }

    fun cleanActions() {
        runOnUiWorkerThread {
            actionList.removeAll { actionFromString(it) == null }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == SettingsActivity.SHOW_PREDICTIONS_PREF) {
            if (!isPredictorEnabled) {
                appsList.clear()
                tearDownBroadcastReceiver()
            } else {
                setupBroadcastReceiver()
            }
        } else if (key == SettingsActivity.SHOW_ACTIONS_PREF) {
            if (!isActionsEnabled) {
                getActions {
                    actions ->
                    runOnMainThread {
                        actionsRow.onUpdated(actions)
                    }
                }
            }
        } else if (key == SettingsActivity.HIDDEN_ACTIONS_PREF) {
            updateActions()
        }
    }

    override fun isPredictorEnabled(): Boolean {
        // Only enable as fallback, that pref would be set to a proper timestamp if prediction actually worked.
        return super.isPredictorEnabled() && Utilities.getReflectionPrefs(context).getLong("reflection_most_recent_usage", 0L) == 0L
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
        fun replace(filter: String, replacement: String) {
            list = list.map { if (it == filter) replacement else it }.toMutableList()
        }
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

        // TODO: Increase to two?
        const val MAX_HEADPHONE_SUGGESTIONS = 1
        // Our definition of "Recently"
        @JvmStatic
        val DURATION_RECENTLY = TimeUnit.MINUTES.toMillis(2)
    }
}