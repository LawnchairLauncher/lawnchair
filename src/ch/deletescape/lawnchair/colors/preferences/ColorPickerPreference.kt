package ch.deletescape.lawnchair.colors.preferences

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.app.FragmentManager
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R
import me.priyesh.chroma.ColorMode
import me.priyesh.chroma.ColorSelectListener

class ColorPickerPreference(context: Context, attrs: AttributeSet?)
    : Preference(context, attrs), ColorEngine.OnAccentChangeListener {

    private val engine = ColorEngine.getInstance(context)

    init {
        fragment = key
        layoutResource = R.layout.pref_with_preview_icon
    }

    override fun onAttached() {
        super.onAttached()

        engine.addAccentChangeListener(this)
    }

    override fun onDetached() {
        super.onDetached()

        engine.removeAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int, foregroundColor: Int) {
        summary = engine.accentResolver.getDisplayName()
        if (icon == null){
            icon = context.resources.getDrawable(R.drawable.color_preview, null)
        }
        icon.setColorFilter(color, PorterDuff.Mode.SRC)
    }

    fun showDialog(fragmentManager: FragmentManager) {
        ColorPickerDialog.newInstance(engine.accent).show(fragmentManager, key)
    }
}
