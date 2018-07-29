package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.support.v4.app.FragmentManager
import android.support.v7.preference.Preference
import android.util.AttributeSet
import com.android.launcher3.R
import me.priyesh.chroma.ChromaDialog
import me.priyesh.chroma.ColorMode
import me.priyesh.chroma.ColorSelectListener

class ColorPickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs), ColorSelectListener {

    var fragmentManager: FragmentManager? = null
    private val default: Int
    private val colorMode: ColorMode
    private var color: Int = 0

    init {
        fragment = key
        layoutResource = R.layout.pref_with_preview_icon
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference)
        default = ta.getColor(R.styleable.ColorPickerPreference_defaultColor, Color.WHITE)
        colorMode = ta.getInt(R.styleable.ColorPickerPreference_colorMode, 0).toColorMode()
        ta.recycle()
        color = default
        updatePreview()
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restorePersistedValue, defaultValue)
        color = getPersistedInt(default)
        updatePreview()
    }

    fun showDialog() {
        ChromaDialog.Builder()
                .initialColor(color)
                .colorMode(colorMode)
                .onColorSelected(this)
                .create()
                .show(fragmentManager, key)
    }

    override fun onColorSelected(color: Int) {
        this.color = color
        persistInt(color)
        updatePreview()
    }

    private fun updatePreview(){
        val mask = if (colorMode == ColorMode.ARGB) 0xFFFFFFFF else 0xFFFFFF
        summary = String.format("#%06X", mask and color.toLong())
        if(icon == null){
            icon = context.resources.getDrawable(R.drawable.color_preview, null)
        }
        icon.setColorFilter(color, PorterDuff.Mode.SRC)
    }

    private fun Int.toColorMode() = when (this) {
        0 -> ColorMode.RGB
        1 -> ColorMode.ARGB
        2 -> ColorMode.HSV
        else -> ColorMode.RGB
    }
}