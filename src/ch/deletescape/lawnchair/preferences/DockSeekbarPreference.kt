package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.Utilities
import kotlin.reflect.KMutableProperty1

class DockSeekbarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        SeekbarPreference(context, attrs, defStyleAttr) {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val currentStyle get() = prefs.dockStyles.currentStyle

    @Suppress("UNCHECKED_CAST")
    private val property = DockStyle.properties[key] as KMutableProperty1<DockStyle, Float>

    private val onChangeListener = { setValue(property.get(currentStyle)) }

    override val allowResetToDefault = false

    init {
        current = property.get(currentStyle)
    }

    override fun onAttached() {
        super.onAttached()
        prefs.dockStyles.addListener(onChangeListener)
    }

    override fun onDetached() {
        super.onDetached()
        prefs.dockStyles.removeListener(onChangeListener)
    }

    override fun getPersistedFloat(defaultReturnValue: Float): Float {
        return property.get(currentStyle)
    }

    override fun persistFloat(value: Float): Boolean {
        property.set(currentStyle, value)
        return true
    }
}
