package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.PreferenceCategory
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.TextView
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.theme.ColorEngine
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

class StyledPreferenceCategory(context: Context, attrs: AttributeSet?) : PreferenceCategory(context, attrs), ColorEngine.OnAccentChangeListener {
    var title: TextView? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        title = holder.findViewById(android.R.id.title) as TextView
        ColorEngine.getInstance(context).addAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int) {
        title?.setTextColor(color)
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeAccentChangeListener(this)
    }
}