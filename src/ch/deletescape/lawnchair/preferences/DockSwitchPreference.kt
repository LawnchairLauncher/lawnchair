package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v14.preference.SwitchPreference
import android.util.AttributeSet
import com.android.launcher3.Utilities
import kotlin.reflect.KMutableProperty1

class DockSwitchPreference(context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs) {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val currentStyle get() = prefs.dockStyles.currentStyle

    @Suppress("UNCHECKED_CAST")
    private val property = DockStyle.properties[key] as KMutableProperty1<DockStyle, Boolean>

    private val onChangeListener = { isChecked = property.get(currentStyle) }

    init {
        isChecked = property.get(currentStyle)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        isChecked = property.get(currentStyle)
    }

    override fun onAttached() {
        super.onAttached()
        prefs.dockStyles.addListener(onChangeListener)
    }

    override fun onDetached() {
        super.onDetached()
        prefs.dockStyles.removeListener(onChangeListener)
    }

    override fun getPersistedBoolean(defaultReturnValue: Boolean): Boolean {
        return property.get(currentStyle)
    }

    override fun persistBoolean(value: Boolean): Boolean {
        property.set(currentStyle, value)
        return true
    }
}
