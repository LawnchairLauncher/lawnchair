package ch.deletescape.lawnchair

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Looper
import android.preference.PreferenceManager
import ch.deletescape.lawnchair.settings.GridSize
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.MainThreadExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.reflect.KProperty

class LawnchairPreferences(val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val onChangeMap: MutableMap<String, () -> Unit> = HashMap()
    private var onChangeCallback: LawnchairPreferencesChangeCallback? = null
    val sharedPrefs: SharedPreferences = context.applicationContext
            .getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

    private val doNothing = { }
    private val recreate = { recreate() }
    private val reloadApps = { reloadApps() }
    private val reloadAll = { reloadAll() }
    private val restart = { restart() }

    // Theme
    var iconPack by StringPref("pref_icon_pack", "", doNothing)

    // Desktop
    val allowFullWidthWidgets by BooleanPref("pref_fullWidthWidgets", false, restart)
    val gridSize by lazy { GridSize(this, "numRows", "numColumns", LauncherAppState.getIDP(context)) }

    // Dock
    val hideDockGradient by BooleanPref("pref_hideDockGradient", false, recreate)

    // Drawer
    val hideAppLabels by BooleanPref("pref_hideAppLabels", false, recreate)
    val hideAllAppsAppLabels by BooleanPref("pref_hideAllAppsAppLabels", false, recreate)

    var hiddenAppSet by StringSetPref("hidden-app-set", Collections.emptySet(), reloadApps)
    val customAppName = object : MutableMapPref<ComponentName, String>("pref_appNameMap", reloadAll) {
        override fun flattenKey(key: ComponentName) = key.flattenToString()
        override fun unflattenKey(key: String) = ComponentName.unflattenFromString(key)
        override fun flattenValue(value: String) = value
        override fun unflattenValue(value: String) = value
    }
    val recentBackups = object : MutableListPref<Uri>(
            PreferenceManager.getDefaultSharedPreferences(context), "pref_recentBackups") {
        override fun unflattenValue(value: String) = Uri.parse(value)
    }
    var restoreSuccess by BooleanPref("pref_restoreSuccess", false)

    private fun recreate() {
        onChangeCallback?.recreate()
    }

    private fun reloadApps() {
        onChangeCallback?.reloadApps()
    }

    private fun reloadAll() {
        onChangeCallback?.reloadAll()
    }

    private fun restart() {
        onChangeCallback?.restart()
    }

    fun refreshGrid() {
        onChangeCallback?.refreshGrid()
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
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sharedPrefs.getString(getKey(property), defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            edit { putString(getKey(property), value) }
        }
    }

    open inner class StringSetPref(key: String, defaultValue: Set<String>? = null, onChange: () -> Unit = doNothing) :
            PrefDelegate<Set<String>?>(key, defaultValue, onChange) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String>? = sharedPrefs.getStringSet(getKey(property), defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>?) {
            edit { putStringSet(getKey(property), value) }
        }
    }

    open inner class IntPref(key: String, defaultValue: Int = 0, onChange: () -> Unit = doNothing) :
            PrefDelegate<Int>(key, defaultValue, onChange) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sharedPrefs.getInt(getKey(property), defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            edit { putInt(getKey(property), value) }
        }
    }

    open inner class FloatPref(key: String, defaultValue: Float = 0f, onChange: () -> Unit = doNothing) :
            PrefDelegate<Float>(key, defaultValue, onChange) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = sharedPrefs.getFloat(getKey(property), defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            edit { putFloat(getKey(property), value) }
        }
    }

    open inner class BooleanPref(key: String, defaultValue: Boolean = false, onChange: () -> Unit = doNothing) :
            PrefDelegate<Boolean>(key, defaultValue, onChange) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sharedPrefs.getBoolean(getKey(property), defaultValue)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            edit { putBoolean(getKey(property), value) }
        }
    }

    // ----------------
    // Helper functions and class
    // ----------------

    fun getPrefKey(key: String) = "pref_$key"

    private fun setBoolean(pref: String, value: Boolean, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putBoolean(pref, value), commit)
    }

    private fun getBoolean(pref: String, default: Boolean): Boolean {
        return sharedPrefs.getBoolean(pref, default)
    }

    private fun setString(pref: String, value: String, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putString(pref, value), commit)
    }

    private fun getString(pref: String, default: String): String {
        return sharedPrefs.getString(pref, default)
    }

    private fun setInt(pref: String, value: Int, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putInt(pref, value), commit)
    }

    private fun getInt(pref: String, default: Int): Int {
        return sharedPrefs.getInt(pref, default)
    }

    private fun setFloat(pref: String, value: Float, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putFloat(pref, value), commit)
    }

    private fun getFloat(pref: String, default: Float): Float {
        return sharedPrefs.getFloat(pref, default)
    }

    private fun setLong(pref: String, value: Long, commit: Boolean) {
        commitOrApply(sharedPrefs.edit().putLong(pref, value), commit)
    }

    private fun getLong(pref: String, default: Long): Long {
        return sharedPrefs.getLong(pref, default)
    }

    private fun remove(pref: String, commit: Boolean) {
        return commitOrApply(sharedPrefs.edit().remove(pref), commit)
    }

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

    abstract inner class PrefDelegate<T>(val key: String, val defaultValue: T, onChange: () -> Unit) {

        init {
            if (onChange !== doNothing) {
                onChangeMap[key] = onChange
            }
        }

        abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T

        abstract operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)

        protected inline fun edit(body: SharedPreferences.Editor.() -> Unit) {
            @SuppressLint("CommitPrefEdits")
            val editor = if (bulkEditing) editor!! else sharedPrefs.edit()
            body(editor)
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }

        @Suppress("USELESS_ELVIS")
        internal fun getKey(property: KProperty<*>) = key ?: getPrefKey(property.name)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        onChangeMap[key]?.invoke()
    }

    fun registerCallback(callback: LawnchairPreferencesChangeCallback) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        onChangeCallback = callback
    }

    fun unregisterCallback() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
        onChangeCallback = null
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: LawnchairPreferences? = null

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