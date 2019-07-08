/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

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
    protected val hideProperty = manager::dockHidden

    abstract var enableGradient: Boolean
    abstract var enableShadow: Boolean
    abstract var radius: Float
    abstract var opacity: Int
    abstract var enableArrow: Boolean
    abstract var hide: Boolean

    var opacityPref
        get() = opacity.toFloat() / 255f
        set(value) { opacity = (value * 255f).roundToInt() }

    private class RoundedStyle(manager: StyleManager) : PredefinedStyle(manager, defaultGradient = true, defaultShadow = false, defaultRadius = 16f, defaultArrow = false)
    private class GradientStyle(manager: StyleManager) : PredefinedStyle(manager, defaultGradient = true)
    private class FlatStyle(manager: StyleManager) : PredefinedStyle(manager)
    private class TransparentStyle(manager: StyleManager) : PredefinedStyle(manager, defaultGradient = true, defaultOpacity = 0)
    private class HiddenStyle(manager: StyleManager) : PredefinedStyle(manager, defaultArrow = false, defaultGradient = true, defaultRadius = 16f, defaultHide = true) {
        override var enableArrow
            get() = showArrowProperty.get()
            set(value) { showArrowProperty.set(value) }
    }

    private class CustomStyle(manager: StyleManager) : DockStyle(manager) {

        override var enableGradient by PropertyDelegate(gradientProperty)
        override var enableShadow by PropertyDelegate(shadowProperty)
        override var radius by PropertyDelegate(radiusProperty)
        override var opacity by PropertyDelegate(opacityProperty)
        override var enableArrow by PropertyDelegate(showArrowProperty)
        override var hide
            get() = false
            set(value) {}
    }

    private abstract class PredefinedStyle(manager: StyleManager,
                                           val defaultGradient: Boolean = false,
                                           val defaultShadow: Boolean = false,
                                           val defaultRadius: Float = 0f,
                                           val defaultOpacity: Int = -1,
                                           val defaultArrow: Boolean = true,
                                           val defaultHide: Boolean = false) : DockStyle(manager) {

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

        override var hide
            get() = defaultHide
            set(value) { setProp(hideProperty, value, defaultHide) }

        fun <T> setProp(property: KMutableProperty0<T>, value: T, defaultValue: T) {
            if (value != defaultValue) {
                manager.prefs.blockingEdit {
                    bulkEdit {
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
    }

    class StyleManager(val prefs: LawnchairPreferences,
                       private val onPresetChange: () -> Unit,
                       private val onCustomizationChange: () -> Unit) {

        val onChangeListener = ::onValueChanged
        var dockPreset by prefs.StringIntPref("pref_dockPreset", 1, onChangeListener)
        val dockDefaultOpacity = -1
        var dockOpacity by prefs.AlphaPref("pref_hotseatCustomOpacity", dockDefaultOpacity, onChangeListener)
        var dockRadius by prefs.FloatPref("pref_dockRadius", 16f, onChangeListener)
        var dockShadow by prefs.BooleanPref("pref_dockShadow", true, onChangeListener)
        var dockShowArrow by prefs.BooleanPref("pref_hotseatShowArrow", false, onChangeListener)
        var dockGradient by prefs.BooleanPref("pref_dockGradient", false, ::onGradientChanged)
        var dockHidden by prefs.BooleanPref("pref_hideHotseat", false, onChangeListener)

        val styles = arrayListOf(CustomStyle(this), RoundedStyle(this), GradientStyle(this), FlatStyle(this),
                TransparentStyle(this), HiddenStyle(this))
        var currentStyle = styles[dockPreset]
        private var oldStyle = styles[dockPreset]

        private val listeners = HashSet<() -> Unit>()

        private fun onGradientChanged() {
            onPresetChange()
            onValueChanged()
        }

        private fun onValueChanged() {
            currentStyle = styles[dockPreset]
            if (currentStyle.enableGradient != oldStyle.enableGradient || currentStyle.hide != oldStyle.hide) {
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
                Pair("enableArrow", DockStyle::enableArrow),
                Pair("hide", DockStyle::hide))
    }
}
