package ch.deletescape.lawnchair.settings.ui.preferences

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.android.launcher3.R

class CustomSeekBarPreference(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs), Preference.OnPreferenceChangeListener {

    private val minValue: Int
    private val maxValue: Int
    private val snapEvery: Int
    private val asPercentages: Boolean

    private var displayedValue: Int = 0
    private var realValue: Double = 0.0

    init {
        widgetLayoutResource = R.layout.custom_seek_bar_preference
        layoutResource = R.layout.custom_seek_bar_preference
        context.obtainStyledAttributes(attrs, R.styleable.CustomSeekBarPreference).apply {
            minValue = this.getInt(R.styleable.CustomSeekBarPreference_minValue, 0)
            maxValue = this.getInt(R.styleable.CustomSeekBarPreference_maxValue, 100)
            snapEvery = this.getInt(R.styleable.CustomSeekBarPreference_snapEvery, 1)
            asPercentages = this.getBoolean(R.styleable.CustomSeekBarPreference_asPercentages, false)
            recycle()
        }
    }

    override fun onBindViewHolder(view: PreferenceViewHolder?) {
        super.onBindViewHolder(view)
        onPreferenceChangeListener = this

        val seekBar: SeekBar = view?.findViewById(R.id.seek_bar) as SeekBar
        val valueView: TextView = view.findViewById(R.id.value_view) as TextView

        seekBar.max = (maxValue - minValue) / snapEvery

        seekBar.setOnSeekBarChangeListener(object:  SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                displayedValue = progress * snapEvery + minValue
                realValue = if (asPercentages) displayedValue.toDouble() / 100 else displayedValue.toDouble()
                valueView.text = if (asPercentages) "$displayedValue%" else "$displayedValue"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        return true
    }

}