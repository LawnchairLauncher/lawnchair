package ch.deletescape.lawnchair.settings.ui.preferences

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.android.launcher3.R

class CustomSeekBarPreference(context: Context, attrs: AttributeSet? = null) : SeekBarPreference(context, attrs), Preference.OnPreferenceChangeListener {

    private val minValue: Int
    private val maxValue: Int

    init {
        context.obtainStyledAttributes(attrs, R.styleable.CustomSeekBarPreference).apply {
            minValue = this.getInt(R.styleable.CustomSeekBarPreference_minValue, 10)
            maxValue = this.getInt(R.styleable.CustomSeekBarPreference_maxValue, 100)
            recycle()
        }
    }

    override fun onBindViewHolder(view: PreferenceViewHolder?) {
        super.onBindViewHolder(view)
        onPreferenceChangeListener = this
        Log.i(null, "$minValue")
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        TODO("Not yet implemented")
    }

}