package app.lawnchair.omni.experience

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SceneController {
    private val mutations = Mutex()
    val booleanKeys = setOf("show_status_bar", "hide_app_drawer_search_bar", "themed_hotseat_qsb", "all_apps_remember_position")
    val stringKeys = setOf("dock_search_bar_provider", "search_algorithm", "icon_shape")
    val floatKeys = setOf("home_icon_size_factor", "drawer_icon_size_factor", "home_icon_label_size_factor")

    suspend fun capture(context: Context, name: String, id: String = ExperienceStore.newId()): HomeScene = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager2.omniPreferencesDataStore(context).data.first()
        HomeScene(
            id,
            name,
            booleanKeys.mapNotNull { key -> prefs[booleanPreferencesKey(key)]?.let { key to it } }.toMap(),
            stringKeys.mapNotNull { key -> prefs[stringPreferencesKey(key)]?.let { key to it } }.toMap() +
                ("theme" to PreferenceManager.getInstance(context).launcherTheme.get()) +
                ("icon_pack" to PreferenceManager.getInstance(context).iconPackPackage.get()),
            floats = floatKeys.mapNotNull { key -> prefs[floatPreferencesKey(key)]?.let { key to it } }.toMap(),
            stackSelections = ExperienceStore.get(context).read().stacks.associate { it.outerId to it.selected },
        )
    }

    suspend fun apply(context: Context, sceneId: String, checkAccess: () -> Unit = {}, automatic: Boolean = false) = mutations.withLock {
        checkAccess()
        val store = ExperienceStore.get(context)
        val state = store.read()
        if (automatic && !state.automaticScenes) return@withLock
        val scene = state.scenes.firstOrNull { it.id == sceneId } ?: error("Scene unavailable")
        val previous = capture(context, "Undo")
        checkAccess()
        if (automatic) store.update { it.copy(automaticBaseline = it.automaticBaseline ?: previous, automaticSceneId = sceneId) }
        write(context, scene, checkAccess)
        store.update {
            checkAccess()
            it.copy(activeScene = sceneId, undoScene = previous, stacks = restoreStacks(it.stacks, scene), automaticBaseline = if (automatic) it.automaticBaseline else null, automaticSceneId = sceneId.takeIf { automatic })
        }
        refresh(context)
    }

    suspend fun undo(context: Context) = mutations.withLock {
        val store = ExperienceStore.get(context)
        val scene = store.read().undoScene ?: return@withLock
        write(context, scene)
        store.update { it.copy(activeScene = null, undoScene = null, stacks = restoreStacks(it.stacks, scene), automaticBaseline = null, automaticSceneId = null) }
        refresh(context)
    }

    private suspend fun restoreAutomatic(context: Context) = mutations.withLock {
        val store = ExperienceStore.get(context)
        val state = store.read()
        val baseline = state.automaticBaseline ?: return@withLock
        if (state.automaticSceneId == null) return@withLock
        write(context, baseline)
        store.update { it.copy(activeScene = null, undoScene = null, automaticBaseline = null, automaticSceneId = null, stacks = restoreStacks(it.stacks, baseline)) }
        refresh(context)
    }

    private fun restoreStacks(stacks: List<WidgetStack>, scene: HomeScene): List<WidgetStack> = stacks.map { stack ->
        val selected = scene.stackSelections[stack.outerId]
        if (selected != null && selected in stack.widgetIds.indices) stack.copy(selected = selected) else stack
    }

    private suspend fun write(context: Context, scene: HomeScene, checkAccess: () -> Unit = {}) = withContext(Dispatchers.IO) {
        require(scene.booleans.keys.all { it in booleanKeys } && scene.strings.keys.all { it in stringKeys || it in setOf("theme", "icon_pack") })
        require(scene.strings["theme"] == null || scene.strings["theme"] in setOf("system", "light", "dark"))
        require(scene.floats.keys.all { it in floatKeys } && scene.floats.values.all { it.isFinite() && it in .25f..3f })
        PreferenceManager2.omniPreferencesDataStore(context).edit { prefs ->
            checkAccess()
            booleanKeys.forEach { key ->
                val pref = booleanPreferencesKey(key)
                scene.booleans[key]?.let { prefs[pref] = it } ?: prefs.remove(pref)
            }
            floatKeys.forEach { key ->
                val pref = floatPreferencesKey(key)
                scene.floats[key]?.let { prefs[pref] = it } ?: prefs.remove(pref)
            }
            stringKeys.forEach { key ->
                val pref = stringPreferencesKey(key)
                scene.strings[key]?.let { prefs[pref] = it } ?: prefs.remove(pref)
            }
        }
        checkAccess()
        scene.strings["icon_pack"]?.let { PreferenceManager.getInstance(context).iconPackPackage.set(it) }
        scene.strings["theme"]?.let { PreferenceManager.getInstance(context).launcherTheme.set(it) }
    }

    private suspend fun refresh(context: Context) = withContext(Dispatchers.Main) {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
    }

    /** Time/power/headset rules are evaluated only while Home is visible. No background service. */
    fun attach(launcher: LawnchairLauncher) {
        var job: Job? = null
        var registered = false
        fun evaluate() {
            job?.cancel()
            job = launcher.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val state = ExperienceStore.get(launcher).read()
                    if (!state.automaticScenes && state.automaticBaseline == null) return@launch
                    val battery = launcher.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val audio = launcher.getSystemService(AudioManager::class.java)
                    val headset = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any {
                        it.type in setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
                    }
                    val time = Calendar.getInstance()
                    val minute = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE)
                    when (val change = state.automaticSceneChange(minute, charging, headset)) {
                        AutomaticSceneChange.None -> Unit
                        AutomaticSceneChange.Restore -> withContext(kotlinx.coroutines.NonCancellable) { restoreAutomatic(launcher) }
                        is AutomaticSceneChange.Apply -> withContext(kotlinx.coroutines.NonCancellable) { apply(launcher, change.id, automatic = true) }
                    }
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    android.util.Log.w("OmniScenes", "Automatic scene unavailable", error)
                }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = evaluate()
        }
        launcher.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                    addAction(Intent.ACTION_HEADSET_PLUG)
                    addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                }
                ContextCompat.registerReceiver(launcher, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                registered = true
                evaluate()
            }
            override fun onStop(owner: LifecycleOwner) {
                if (registered) launcher.unregisterReceiver(receiver)
                registered = false
                job?.cancel()
            }
        })
    }
}
