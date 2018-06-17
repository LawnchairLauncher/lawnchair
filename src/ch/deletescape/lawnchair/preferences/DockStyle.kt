package ch.deletescape.lawnchair.preferences

import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.PropertyDelegate
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

abstract class DockStyle(protected val manager: StyleManager) {

    protected val gradientProperty = manager::dockGradient
    protected val shadowProperty = manager::dockShadow
    protected val radiusProperty = manager::dockRadius
    protected val opacityProperty = manager::dockOpacity
    protected val showArrowProperty = manager::dockShowArrow

    abstract var enableGradient: Boolean
    abstract var enableShadow: Boolean
    abstract var radius: Float
    abstract var opacity: Int
    abstract var enableArrow: Boolean

    var opacityPref
        get() = opacity.toFloat() / 255f
        set(value) { opacity = (value * 255f).roundToInt() }

    private class RoundedStyle(manager: StyleManager) : PredefinedStyle(manager, false, true, 8f, 100, false)
    private class GradientStyle(manager: StyleManager) : PredefinedStyle(manager, true, false, 0f, 100, true)
    private class FlatStyle(manager: StyleManager) : PredefinedStyle(manager, false, false, 0f, 100, true)
    private class TransparentStyle(manager: StyleManager) : PredefinedStyle(manager, true, false, 0f, 100, true)

    private class CustomStyle(manager: StyleManager) : DockStyle(manager) {

        override var enableGradient by PropertyDelegate(gradientProperty)
        override var enableShadow by PropertyDelegate(shadowProperty)
        override var radius by PropertyDelegate(radiusProperty)
        override var opacity by PropertyDelegate(opacityProperty)
        override var enableArrow by PropertyDelegate(showArrowProperty)
    }

    private abstract class PredefinedStyle(manager: StyleManager,
                                   val defaultGradient: Boolean,
                                   val defaultShadow: Boolean,
                                   val defaultRadius: Float,
                                   val defaultOpacity: Int,
                                   val defaultArrow: Boolean) : DockStyle(manager) {

        override var enableGradient
            get() = defaultGradient
            set(value) { setProp(gradientProperty, value, defaultGradient) }

        override var enableShadow
            get() = defaultShadow
            set(value) { setProp(shadowProperty, value, defaultShadow) }

        override var radius
            get() = defaultRadius
            set(value) { setProp(radiusProperty, value, radius) }

        override var opacity
            get() = defaultOpacity
            set(value) { setProp(opacityProperty, value, opacity) }

        override var enableArrow
            get() = defaultArrow
            set(value) { setProp(showArrowProperty, value, defaultArrow) }

        fun <T> setProp(property: KMutableProperty0<T>, value: T, defaultValue: T) {
            if (value != defaultValue) {
                manager.prefs.bulkEdit {
                    gradientProperty.set(enableGradient)
                    shadowProperty.set(enableShadow)
                    radiusProperty.set(radius)
                    opacityProperty.set(opacity)
                    showArrowProperty.set(enableArrow)
                    manager.dockPreset = 0
                    property.set(value)
                }
            }
        }
    }

    class StyleManager(val prefs: LawnchairPreferences,
                       private val onPresetChange: () -> Unit,
                       private val onCustomizationChange: () -> Unit) {

        val onChangeListener = ::onValueChanged
        var dockPreset by prefs.StringIntPref("pref_dockPreset", 1, onChangeListener)
        val dockDefaultOpacity = 100
        var dockOpacity by prefs.AlphaPref("pref_hotseatCustomOpacity", dockDefaultOpacity, onChangeListener)
        var dockRadius by prefs.FloatPref("pref_dockRadius", 8f, onChangeListener)
        var dockShadow by prefs.BooleanPref("pref_dockShadow", true, onChangeListener)
        var dockShowArrow by prefs.BooleanPref("pref_hotseatShowArrow", false, onChangeListener)
        var dockGradient by prefs.BooleanPref("pref_dockGradient", false, onChangeListener)

        val styles = arrayListOf(CustomStyle(this), RoundedStyle(this), GradientStyle(this), FlatStyle(this),
                TransparentStyle(this))
        var currentStyle = styles[dockPreset]
        private var oldStyle = styles[dockPreset]

        private val listeners = HashSet<() -> Unit>()

        private fun onValueChanged() {
            currentStyle = styles[dockPreset]
            if (currentStyle.enableGradient != oldStyle.enableGradient) {
                onPresetChange()
            } else {
                onCustomizationChange()
            }
            oldStyle = styles[dockPreset]
            listeners.forEach { it() }
        }

        fun addListener(listener: () -> Unit) {
            listeners.add(listener)
        }

        fun removeListener(listener: () -> Unit) {
            listeners.remove(listener)
        }
    }

    companion object {

        val properties = hashMapOf(
                Pair("enableGradient", DockStyle::enableGradient),
                Pair("enableShadow", DockStyle::enableShadow),
                Pair("radius", DockStyle::radius),
                Pair("opacityPref", DockStyle::opacityPref),
                Pair("enableArrow", DockStyle::enableArrow))
    }
}
