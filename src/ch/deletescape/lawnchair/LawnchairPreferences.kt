package ch.deletescape.lawnchair

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Looper
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.settings.GridSize
import ch.deletescape.lawnchair.settings.GridSize2D
import ch.deletescape.lawnchair.theme.ThemeManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.MainThreadExecutor
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

class LawnchairPreferences(val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val onChangeMap: MutableMap<String, () -> Unit> = HashMap()
    private val onChangeListeners: MutableMap<String, MutableSet<OnPreferenceChangeListener>> = HashMap()
    private var onChangeCallback: LawnchairPreferencesChangeCallback? = null
    val sharedPrefs = migratePrefs()

    private fun migratePrefs() : SharedPreferences {
        val dir = context.cacheDir.parent
        val oldFile = File(dir, "shared_prefs/" + LauncherFiles.OLD_SHARED_PREFERENCES_KEY + ".xml")
        val newFile = File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile)
            oldFile.delete()
        }
        return context.applicationContext.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    private val doNothing = { }
    private val recreate = { recreate() }
    private val reloadApps = { reloadApps() }
    private val reloadAll = { reloadAll() }
    val restart = { restart() }
    private val refreshGrid = { refreshGrid() }
    private val updateBlur = { updateBlur() }
    private val resetAllApps = { onChangeCallback?.resetAllApps() ?: Unit }
    private val updateSmartspace = { updateSmartspace() }

    var restoreSuccess by BooleanPref("pref_restoreSuccess", false)
    var configVersion by IntPref("config_version", if (restoreSuccess) 0 else CURRENT_VERSION)

    // Blur
    var enableBlur by BooleanPref("pref_enableBlur", false, updateBlur)
    val enableVibrancy = true
    val blurRadius by FloatPref("pref_blurRadius", 75f, updateBlur)

    // Theme
    var iconPack by StringPref("pref_icon_pack", "", doNothing)
    var launcherTheme by StringIntPref("pref_launcherTheme", 1, { ThemeManager.getInstance(context).onThemeChanged() })

    // Desktop
    val smartspaceTime by BooleanPref("pref_smartspace_time", false, refreshGrid)
    val smartspaceDate by BooleanPref("pref_smartspace_date", false, refreshGrid)
    val allowFullWidthWidgets by BooleanPref("pref_fullWidthWidgets", false, restart)
    val gridSize by lazy { GridSize2D(this, "numRows", "numColumns", LauncherAppState.getIDP(context), refreshGrid) }

    // Dock
    val dockColoredGoogle by BooleanPref("pref_dockColoredGoogle", false, doNothing)
    val dockSearchBar by BooleanPref("pref_dockSearchBar", true, restart)
    val dockDefaultOpacity = 100
    val dockCustomOpacity by BooleanPref("pref_hotseatShouldUseCustomOpacity", false, resetAllApps)
    val dockOpacity by AlphaPref("pref_hotseatCustomOpacity", dockDefaultOpacity, resetAllApps)
    val dockShowArrow by BooleanPref("pref_hotseatShowArrow", false, resetAllApps)
    val dockShowPageIndicator by BooleanPref("pref_hotseatShowPageIndicator", true, { onChangeCallback?.updatePageIndicator() })
    val dockStyle by StringIntPref("pref_dockStyle", 3, recreate)
    val dockGradientStyle get() = (dockStyle and 1) == 0
    val dockRoundedCorners get() = (dockStyle and 2) != 0
    val hideDockGradient get() = (dockStyle and 4) != 0
    val dockGridSize by lazy { GridSize(this, "numHotseatIcons", LauncherAppState.getIDP(context), recreate) }

    // Drawer
    val hideAppLabels by BooleanPref("pref_hideAppLabels", false, recreate)
    val hideAllAppsAppLabels by BooleanPref("pref_hideAllAppsAppLabels", false, recreate)
    val allAppsCustomOpacity by BooleanPref("pref_allAppsShouldUseCustomOpacity", false, doNothing)
    val allAppsDefaultOpacity = 235
    val allAppsOpacity by AlphaPref("pref_allAppsOpacitySB", allAppsDefaultOpacity, doNothing)
    val allAppsStartAlpha get() = if (dockCustomOpacity) dockOpacity else dockDefaultOpacity
    val allAppsEndAlpha get() = if (allAppsCustomOpacity) allAppsOpacity else allAppsDefaultOpacity
    val allAppsAlphaRange get() = allAppsEndAlpha - allAppsStartAlpha
    val allAppsGoogleSearch by BooleanPref("pref_allAppsGoogleSearch", true, doNothing)

    // Dev
    var developerOptionsEnabled by BooleanPref("pref_developerOptionsEnabled", false, doNothing)
    val showDebugInfo by BooleanPref("pref_showDebugInfo", false, doNothing)

    var hiddenAppSet by StringSetPref("hidden-app-set", Collections.emptySet(), reloadApps)
    val customAppName = object : MutableMapPref<ComponentKey, String>("pref_appNameMap", reloadAll) {
        override fun flattenKey(key: ComponentKey) = key.toString()
        override fun unflattenKey(key: String) = ComponentKey(context, key)
        override fun flattenValue(value: String) = value
        override fun unflattenValue(value: String) = value
    }
    val customAppIcon = object : MutableMapPref<ComponentKey, IconPackManager.CustomIconEntry>("pref_appIconMap", reloadAll) {
        override fun flattenKey(key: ComponentKey) = key.toString()
        override fun unflattenKey(key: String) = ComponentKey(context, key)
        override fun flattenValue(value: IconPackManager.CustomIconEntry) = value.toString()
        override fun unflattenValue(value: String) = IconPackManager.CustomIconEntry.fromString(value)
    }
    val recentBackups = object : MutableListPref<Uri>(
            Utilities.getDevicePrefs(context), "pref_recentBackups") {
        override fun unflattenValue(value: String) = Uri.parse(value)
    }

    private fun recreate() {
        onChangeCallback?.recreate()
    }

    private fun reloadApps() {
        onChangeCallback?.reloadApps()
    }

    private fun reloadAll() {
        onChangeCallback?.reloadAll()
    }

    fun restart() {
        onChangeCallback?.restart()
    }

    fun refreshGrid() {
        onChangeCallback?.refreshGrid()
    }

    private fun updateBlur() {
        onChangeCallback?.updateBlur()
    }

    private fun updateSmartspace() {
        onChangeCallback?.updateSmartspace()
    }

    fun addOnPreferenceChangeListener(key: String, listener: OnPreferenceChangeListener) {
        if (onChangeListeners[key] == null) {
            onChangeListeners[key] = HashSet()
        }
        onChangeListeners[key]?.add(listener)
        listener.onValueChanged(key, this, true)
    }

    fun removeOnPreferenceChangeListener(key: String, listener: OnPreferenceChangeListener) {
        onChangeListeners[key]?.remove(listener)
    }

    abstract inner class MutableListPref<T>(private val prefs: SharedPreferences,
                                            private val prefKey: String,
                                            onChange: () -> Unit = doNothing) {

        constructor(prefKey: String, onChange: () -> Unit = doNothing) : this(sharedPrefs, prefKey, onChange)

        private val valueList = ArrayList<T>()

        init {
            val arr = JSONArray(prefs.getString(prefKey, "[]"))
            (0 until arr.length()).mapTo(valueList) { unflattenValue(arr.getString(it)) }
            if (onChange != doNothing) {
                onChangeMap[prefKey] = onChange
            }
        }

        fun toList() = ArrayList<T>(valueList)

        open fun flattenValue(value: T) = value.toString()
        abstract fun unflattenValue(value: String): T

        operator fun get(position: Int): T {
            return valueList[position]
        }

        operator fun set(position: Int, value: T) {
            valueList[position] = value
            saveChanges()
        }

        fun add(value: T) {
            valueList.add(value)
            saveChanges()
        }

        fun add(position: Int, value: T) {
            valueList.add(position, value)
            saveChanges()
        }

        fun remove(value: T) {
            valueList.remove(value)
            saveChanges()
        }

        fun removeAt(position: Int) {
            valueList.removeAt(position)
            saveChanges()
        }

        fun contains(value: T): Boolean {
            return valueList.contains(value)
        }

        fun replaceWith(newList: List<T>) {
            valueList.clear()
            valueList.addAll(newList)
            saveChanges()
        }

        private fun saveChanges() {
            val arr = JSONArray()
            valueList.forEach { arr.put(flattenValue(it)) }
            @SuppressLint("CommitPrefEdits")
            val editor = prefs.edit()
            editor.putString(prefKey, arr.toString())
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }
    }

    abstract inner class MutableMapPref<K, V>(private val prefKey: String, onChange: () -> Unit = doNothing) {
        private val valueMap = HashMap<K, V>()

        init {
            val obj = JSONObject(sharedPrefs.getString(prefKey, "{}"))
            obj.keys().forEach {
                valueMap[unflattenKey(it)] = unflattenValue(obj.getString(it))
            }
            if (onChange !== doNothing) {
                onChangeMap[prefKey] = onChange
            }
        }

        fun toMap() = HashMap<K, V>(valueMap)

        open fun flattenKey(key: K) = key.toString()
        abstract fun unflattenKey(key: String): K

        open fun flattenValue(value: V) = value.toString()
        abstract fun unflattenValue(value: String): V

        operator fun set(key: K, value: V?) {
            if (value != null) {
                valueMap[key] = value
            } else {
                valueMap.remove(key)
            }
            saveChanges()
        }

        private fun saveChanges() {
            val obj = JSONObject()
            valueMap.entries.forEach { obj.put(flattenKey(it.key), flattenValue(it.value)) }
            @SuppressLint("CommitPrefEdits")
            val editor = if (bulkEditing) editor!! else sharedPrefs.edit()
            editor.putString(prefKey, obj.toString())
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }

        operator fun get(key: K): V? {
            return valueMap[key]
        }
    }

    open inner class StringPref(key: String, defaultValue: String = "", onChange: () -> Unit = doNothing) :
            PrefDelegate<String>(key, defaultValue, onChange) {
        override fun onGetValue(): String = sharedPrefs.getString(getKey(), defaultValue)

        override fun onSetValue(value: String) {
            edit { putString(getKey(), value) }
        }
    }

    open inner class StringSetPref(key: String, defaultValue: Set<String>, onChange: () -> Unit = doNothing) :
            PrefDelegate<Set<String>>(key, defaultValue, onChange) {
        override fun onGetValue(): Set<String> = sharedPrefs.getStringSet(getKey(), defaultValue)

        override fun onSetValue(value: Set<String>) {
            edit { putStringSet(getKey(), value) }
        }
    }

    open inner class StringIntPref(key: String, defaultValue: Int = 0, onChange: () -> Unit = doNothing) :
            PrefDelegate<Int>(key, defaultValue, onChange) {
        override fun onGetValue(): Int = sharedPrefs.getString(getKey(), "$defaultValue").toInt()

        override fun onSetValue(value: Int) {
            edit { putString(getKey(), "$value") }
        }
    }

    open inner class IntPref(key: String, defaultValue: Int = 0, onChange: () -> Unit = doNothing) :
            PrefDelegate<Int>(key, defaultValue, onChange) {
        override fun onGetValue(): Int = sharedPrefs.getInt(getKey(), defaultValue)

        override fun onSetValue(value: Int) {
            edit { putInt(getKey(), value) }
        }
    }

    open inner class AlphaPref(key: String, defaultValue: Int = 0, onChange: () -> Unit = doNothing) :
            PrefDelegate<Int>(key, defaultValue, onChange) {
        override fun onGetValue(): Int = (sharedPrefs.getFloat(getKey(), defaultValue.toFloat() / 255) * 255).roundToInt()

        override fun onSetValue(value: Int) {
            edit { putFloat(getKey(), value.toFloat() / 255) }
        }
    }

    open inner class FloatPref(key: String, defaultValue: Float = 0f, onChange: () -> Unit = doNothing) :
            PrefDelegate<Float>(key, defaultValue, onChange) {
        override fun onGetValue(): Float = sharedPrefs.getFloat(getKey(), defaultValue)

        override fun onSetValue(value: Float) {
            edit { putFloat(getKey(), value) }
        }
    }

    open inner class BooleanPref(key: String, defaultValue: Boolean = false, onChange: () -> Unit = doNothing) :
            PrefDelegate<Boolean>(key, defaultValue, onChange) {
        override fun onGetValue(): Boolean = sharedPrefs.getBoolean(getKey(), defaultValue)

        override fun onSetValue(value: Boolean) {
            edit { putBoolean(getKey(), value) }
        }
    }

    // ----------------
    // Helper functions and class
    // ----------------

    fun getPrefKey(key: String) = "pref_$key"

    fun commitOrApply(editor: SharedPreferences.Editor, commit: Boolean) {
        if (commit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    var blockingEditing = false
    var bulkEditing = false
    var editor: SharedPreferences.Editor? = null

    fun beginBlockingEdit() {
        blockingEditing = true
    }

    fun endBlockingEdit() {
        blockingEditing = false
    }

    @SuppressLint("CommitPrefEdits")
    fun beginBulkEdit() {
        bulkEditing = true
        editor = sharedPrefs.edit()
    }

    fun endBulkEdit() {
        bulkEditing = false
        commitOrApply(editor!!, blockingEditing)
        editor = null
    }

    inline fun blockingEdit(body: LawnchairPreferences.() -> Unit) {
        beginBlockingEdit()
        body(this)
        endBlockingEdit()
    }

    inline fun bulkEdit(body: LawnchairPreferences.() -> Unit) {
        beginBulkEdit()
        body(this)
        endBulkEdit()
    }

    abstract inner class PrefDelegate<T : Any>(val key: String, val defaultValue: T, private val onChange: () -> Unit) {

        private var cached = false
        private lateinit var value: T

        init {
            onChangeMap[key] = { onValueChanged() }
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!cached) {
                value = onGetValue()
                cached = true
            }
            return value
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            cached = false
            onSetValue(value)
        }

        abstract fun onGetValue(): T

        abstract fun onSetValue(value: T)

        protected inline fun edit(body: SharedPreferences.Editor.() -> Unit) {
            @SuppressLint("CommitPrefEdits")
            val editor = if (bulkEditing) editor!! else sharedPrefs.edit()
            body(editor)
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }

        internal fun getKey() = key

        private fun onValueChanged() {
            cached = false
            onChange.invoke()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        onChangeMap[key]?.invoke()
        onChangeListeners[key]?.forEach { it.onValueChanged(key, this, false) }
    }

    fun registerCallback(callback: LawnchairPreferencesChangeCallback) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        onChangeCallback = callback
    }

    fun unregisterCallback() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
        onChangeCallback = null
    }

    init {
        migrateConfig()
    }

    private fun migrateConfig() {
        if (configVersion != CURRENT_VERSION) {
            blockingEdit {
                bulkEdit {
                    // Migration codes here


                    configVersion = CURRENT_VERSION
                }
            }
        }
    }

    interface OnPreferenceChangeListener {

        fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean)
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: LawnchairPreferences? = null

        const val CURRENT_VERSION = 200

        fun getInstance(context: Context): LawnchairPreferences {
            if (INSTANCE == null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    INSTANCE = LawnchairPreferences(context.applicationContext)
                } else {
                    try {
                        return MainThreadExecutor().submit(Callable { LawnchairPreferences.getInstance(context) }).get()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    }

                }
            }
            return INSTANCE!!
        }

        fun getInstanceNoCreate(): LawnchairPreferences {
            return INSTANCE!!
        }
    }
}