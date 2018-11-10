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
        landscapeScaleKey: String = "landscape${Utilities.upperCaseFirstLetter(scaleKey)}",
        targetObject: Any,
        private val onChangeListener: () -> Unit = prefs.restart) {

    var scale by JavaField<Float>(targetObject, scaleKey)
    val scaleOriginal by JavaField<Float>(targetObject, "${scaleKey}Original")

    var landscapeScale by JavaField<Float>(targetObject, landscapeScaleKey)
    val landscapeScaleOriginal by JavaField<Float>(targetObject, "${landscapeScaleKey}Original")

    private val onChange = {
        applyCustomization()
        onChangeListener.invoke()
    }

    var scalePref by prefs.FloatPref("pref_$scaleKey", 1f, onChange)

    init {
        applyCustomization()
    }

    private fun applyCustomization() {
        scale = fromPref(scalePref, scaleOriginal)
        landscapeScale = fromPref(scalePref, landscapeScaleOriginal)
    }

    fun fromPref(value: Float, default: Float) = value * default
}