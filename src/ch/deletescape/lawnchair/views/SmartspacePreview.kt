package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SmartspacePreview(context: Context, attrs: AttributeSet?) :
        FrameLayout(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val enable = prefs::enableSmartspace
    private val usePillQsb = prefs::usePillQsb
    private val prefsToWatch = arrayOf("pref_smartspace", "pref_smartspace_time",
            "pref_smartspace_time_24_h", "pref_smartspace_date", "pref_use_pill_qsb")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener(this, *prefsToWatch)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener(this, *prefsToWatch)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        removeAllViews()
        inflateCurrentView()
    }

    private fun inflateCurrentView() {
        if (enable.get()) {
            val layout = if (usePillQsb.get()) R.layout.qsb_blocker_view else R.layout.search_container_workspace
            addView(inflateView(layout))
        }
    }

    private fun inflateView(layout: Int): View {
        val view = LayoutInflater.from(context).inflate(layout, this, false)
        view.layoutParams.height = resources.getDimensionPixelSize(R.dimen.smartspace_preview_height)
        return view
    }
}
