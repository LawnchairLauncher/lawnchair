package ch.deletescape.lawnchair.gestures.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.support.v7.preference.Preference
import android.util.AttributeSet
import ch.deletescape.lawnchair.gestures.BlankGestureHandler
import ch.deletescape.lawnchair.gestures.GestureController

class GesturePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    var value = ""
    var defaultValue = ""

    private val blankGestureHandler = BlankGestureHandler(context, null)
    private val handler get() = GestureController.createGestureHandler(context, value, blankGestureHandler)

    override fun onAttached() {
        super.onAttached()

        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetached() {
        super.onDetached()

        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        if (key == this.key) {
            value = getPersistedString(defaultValue)
            notifyChanged()
        }
    }

    override fun getSummary() = handler.displayName

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        value = if (restorePersistedValue) {
            getPersistedString(defaultValue as String?) ?: ""
        } else {
            defaultValue as String? ?: ""
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): String? {
        defaultValue = a.getString(index)
        return defaultValue
    }

    override fun onClick() {
        context.startActivity(Intent(context, SelectGestureActivity::class.java).apply {
            putExtra("title", title)
            putExtra("key", key)
            putExtra("value", value)
        })
    }
}
