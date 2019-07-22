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

package ch.deletescape.lawnchair.settings

import ch.deletescape.lawnchair.JavaField
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.Utilities

class IconScale @JvmOverloads constructor(
        prefs: LawnchairPreferences,
        scaleKey: String,
        private val fallbackScaleKey: String? = null,
        landscapeScaleKey: String = "landscape${Utilities.upperCaseFirstLetter(scaleKey)}",
        private val landscapeFallbackScaleKey: String = "landscape${Utilities.upperCaseFirstLetter(fallbackScaleKey)}",
        private val targetObject: Any,
        private val onChangeListener: () -> Unit = prefs.restart) {

    var scale by JavaField<Float>(targetObject, scaleKey)
    val scaleOriginal by JavaField<Float>(targetObject, "${scaleKey}Original")

    var landscapeScale by JavaField<Float>(targetObject, landscapeScaleKey)
    val landscapeScaleOriginal by JavaField<Float>(targetObject, "${landscapeScaleKey}Original")

    val hasFallback = fallbackScaleKey != null

    private val onChange = {
        applyCustomization()
        onChangeListener.invoke()
    }

    var scalePref by prefs.FloatPref("pref_$scaleKey", if (hasFallback) -1f else 1f, onChange)

    init {
        applyCustomization()
    }

    private fun applyCustomization() {
        scale = fromPref(scalePref, scaleOriginal, fallbackScaleKey)
        landscapeScale = fromPref(scalePref, landscapeScaleOriginal, landscapeFallbackScaleKey)
    }

    fun fromPref(value: Float, default: Float, fallbackKey: String?): Float {
        if (value < 0 && hasFallback) {
            val fallback by JavaField<Float>(targetObject, fallbackKey!!)
            return fallback
        }
        return value * default
    }
}