package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import com.android.launcher3.R

class SeekbarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        Preference(context, attrs, defStyleAttr), SeekBar.OnSeekBarChangeListener, View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener {

    private var mSeekbar: SeekBar? = null
    private var mValueText: TextView? = null
    private var min: Float = 0.toFloat()
    private var max: Float = 0.toFloat()
    private var current: Float = 0.toFloat()
    private var defaultValue: Float = 0.toFloat()
    private var multiplier: Int = 0
    private var format: String? = null
    private var steps: Int = 100


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
        steps = ta.getInt(R.styleable.SeekbarPreference_steps, 100)
        if (format == null) {
            format = "%.2f"
        }
        ta.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.itemView
        mSeekbar = view.findViewById(R.id.seekbar)
        mValueText = view.findViewById(R.id.txtValue)
        mSeekbar!!.max = steps
        mSeekbar!!.setOnSeekBarChangeListener(this)

        current = getPersistedFloat(defaultValue)
        updateDisplayedValue()

        view.setOnCreateContextMenuListener(this)
    }

    private fun updateDisplayedValue() {
        val progress = ((current - min) / ((max - min) / steps))
        mSeekbar!!.progress = Math.round(progress)
        updateSummary()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        current = min + (max - min) / steps * progress
        current = Math.round(current * 100f) / 100f //round to .00 places
        updateSummary()

        persistFloat(current)
    }

    private fun updateSummary() {
        mValueText!!.text = String.format(format!!, current * multiplier)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}

    override fun onStopTrackingTouch(seekBar: SeekBar) {}

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menu.setHeaderTitle(title)
        menu.add(0, 0, 0, R.string.reset_to_default)
        for (i in (0 until menu.size())) {
            menu.getItem(i).setOnMenuItemClickListener(this)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        current = defaultValue
        persistFloat(defaultValue)
        updateDisplayedValue()
        return true
    }
}
