package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView

import ch.deletescape.lawnchair.R

class SeekbarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : Preference(context, attrs, defStyleAttr), SeekBar.OnSeekBarChangeListener {

    private var mSeekbar: SeekBar? = null
    private var mValueText: TextView? = null
    private var min: Float = 0.toFloat()
    private var max: Float = 0.toFloat()
    private var current: Float = 0.toFloat()
    private var defaultValue: Float = 0.toFloat()
    private var multiplier: Int = 0
    private var format: String? = null

    init {
        layoutResource = R.layout.preference_seekbar
        init(context, attrs!!)
    }

    private fun init(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.SeekbarPreference)
        min = ta.getFloat(R.styleable.SeekbarPreference_minValue, 0f)
        max = ta.getFloat(R.styleable.SeekbarPreference_maxValue, 100f)
        multiplier = ta.getInt(R.styleable.SeekbarPreference_summaryMultiplier, 1)
        format = ta.getString(R.styleable.SeekbarPreference_summaryFormat)
        defaultValue = ta.getFloat(R.styleable.SeekbarPreference_defaultSeekbarValue, min)
        if (format == null) {
            format = "%.2f"
        }
        ta.recycle()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        mSeekbar = view.findViewById<SeekBar>(R.id.seekbar)
        mValueText = view.findViewById<TextView>(R.id.txtValue)
        mSeekbar!!.setOnSeekBarChangeListener(this)

        current = getPersistedFloat(defaultValue)
        val progress = ((current - min) / ((max - min) / 100)).toInt()
        mSeekbar!!.progress = progress
        updateSummary()
    }

    override fun onCreateView(parent: ViewGroup): View {
        return super.onCreateView(parent)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        current = min + (max - min) / 100 * progress
        updateSummary()

        persistFloat(current)

    }

    private fun updateSummary() {
        mValueText!!.text = String.format(format!!, current * multiplier)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}

    override fun onStopTrackingTouch(seekBar: SeekBar) {}
}
