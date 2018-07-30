package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.Preference
import android.util.AttributeSet
import ch.deletescape.lawnchair.colors.ColorEngine

open class StyledIconPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs), ColorEngine.OnAccentChangeListener {

    override fun onAttached() {
        super.onAttached()
        ColorEngine.getInstance(context).addAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int) {
        icon.setTint(color)
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeAccentChangeListener(this)
    }
}