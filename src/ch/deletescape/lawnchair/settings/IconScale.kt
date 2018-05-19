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