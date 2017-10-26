package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet

import ch.deletescape.lawnchair.R

class ApiKeyPreference : EditTextPreference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
    }

    override fun persistString(value: String?): Boolean {
        return super.persistString(value).apply { updateSummary() }
    }

    private fun updateSummary() {
        val apiKey = sharedPreferences.getString(PreferenceFlags.KEY_WEATHER_API_KEY, PreferenceFlags.PREF_DEFAULT_WEATHER_API_KEY)
        if (!TextUtils.isEmpty(apiKey))
            summary = apiKey.replace("[A-Za-z0-9]".toRegex(), "*")
        else
            setSummary(R.string.weather_api_key_not_set)
    }

}
