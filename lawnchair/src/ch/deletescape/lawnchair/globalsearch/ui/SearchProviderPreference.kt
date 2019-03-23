package ch.deletescape.lawnchair.globalsearch.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.globalsearch.SearchProviderController
import com.android.launcher3.R

class SearchProviderPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    var value = ""
    var defaultValue = ""

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

    override fun getSummary() = SearchProviderController.getInstance(context).searchProvider.name

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

    override fun getDialogLayoutResource() = R.layout.dialog_preference_recyclerview
}
