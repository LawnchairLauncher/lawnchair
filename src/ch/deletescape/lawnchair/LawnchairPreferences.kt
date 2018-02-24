package ch.deletescape.lawnchair

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherFiles
import com.android.launcher3.MainThreadExecutor
import com.android.launcher3.compat.UserManagerCompat
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.reflect.KProperty

class LawnchairPreferences(val context: Context) {

    val hideDockGradient by BooleanPref(defaultValue = false)

    private inner class MutableStringPref(key: String? = null, defaultValue: String = "") :
            StringPref(key, defaultValue), MutablePrefDelegate<String> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            edit { putString(getKey(property), value) }
        }
    }

    private inner open class StringPref(key: String? = null, defaultValue: String = "") :
            PrefDelegate<String>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = sharedPrefs.getString(getKey(property), defaultValue)
    }

    private inner class MutableStringSetPref(key: String? = null, defaultValue: Set<String>? = null) :
            StringSetPref(key, defaultValue), MutablePrefDelegate<Set<String>?> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>?) {
            edit { putStringSet(getKey(property), value) }
        }
    }

    private inner open class StringSetPref(key: String? = null, defaultValue: Set<String>? = null) :
            PrefDelegate<Set<String>?>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String>? = sharedPrefs.getStringSet(getKey(property), defaultValue)
    }

    private inner class MutableIntPref(key: String? = null, defaultValue: Int = 0) :
            IntPref(key, defaultValue), MutablePrefDelegate<Int> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            edit { putInt(getKey(property), value) }
        }
    }

    private inner open class IntPref(key: String? = null, defaultValue: Int = 0) :
            PrefDelegate<Int>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sharedPrefs.getInt(getKey(property), defaultValue)
    }

    private inner class MutableFloatPref(key: String? = null, defaultValue: Float = 0f) :
            FloatPref(key, defaultValue), MutablePrefDelegate<Float> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            edit { putFloat(getKey(property), value) }
        }
    }

    private inner open class FloatPref(key: String? = null, defaultValue: Float = 0f) :
            PrefDelegate<Float>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = sharedPrefs.getFloat(getKey(property), defaultValue)
    }

    private inner class MutableBooleanPref(key: String? = null, defaultValue: Boolean = false) :
            BooleanPref(key, defaultValue), MutablePrefDelegate<Boolean> {
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            edit { putBoolean(getKey(property), value) }
        }
    }

    private inner open class BooleanPref(key: String? = null, defaultValue: Boolean = false) :
            PrefDelegate<Boolean>(key, defaultValue) {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sharedPrefs.getBoolean(getKey(property), defaultValue)
    }

    // ----------------
    // Helper functions and class
    // ----------------

    private val sharedPrefs: SharedPreferences = getSharedPrefs()

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

    fun getSharedPrefs() : SharedPreferences {
        return context.applicationContext.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
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

    private abstract inner class PrefDelegate<T>(val key: String?, val defaultValue: T) {
        abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T

        protected inline fun edit(body: SharedPreferences.Editor.() -> Unit) {
            @SuppressLint("CommitPrefEdits")
            val editor = if (bulkEditing) editor!! else sharedPrefs.edit()
            body(editor)
            if (!bulkEditing)
                commitOrApply(editor, blockingEditing)
        }

        internal fun getKey(property: KProperty<*>) = key ?: getPrefKey(property.name)
    }

    private interface MutablePrefDelegate<T> {
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
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