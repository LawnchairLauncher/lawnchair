package app.lawnchair.font

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.lifecycle.lifecycleScope
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.lookupLifecycleOwner
import app.lawnchair.util.runOnMainThread
import com.android.launcher3.R
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.launch

class FontManager private constructor(private val context: Context) {

    private val fontCache = FontCache.INSTANCE.get(context)

    private val specMap = createFontMap()

    private fun createFontMap(): Map<Int, FontSpec> {
        val sansSerif = Typeface.SANS_SERIF
        val sansSerifMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val prefs = PreferenceManager.getInstance(context)
        val map = mutableMapOf<Int, FontSpec>()
        map[R.id.font_base_icon] = FontSpec(prefs.fontWorkspace, sansSerif)
        map[R.id.font_button] = FontSpec(prefs.fontHeadingMedium, sansSerifMedium)
        map[R.id.font_heading] = FontSpec(prefs.fontHeading, sansSerif)
        map[R.id.font_heading_medium] = FontSpec(prefs.fontHeadingMedium, sansSerif)
        map[R.id.font_body] = FontSpec(prefs.fontBody, sansSerif)
        map[R.id.font_body_medium] = FontSpec(prefs.fontBodyMedium, sansSerif)
        return map
    }

    fun overrideFont(textView: TextView, attrs: AttributeSet?) {
        try {
            val context = textView.context
            var a = context.obtainStyledAttributes(attrs, R.styleable.CustomFont)
            var fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
            var fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
            val ap = a.getResourceId(R.styleable.CustomFont_android_textAppearance, -1)
            a.recycle()

            if (ap != -1) {
                a = context.obtainStyledAttributes(ap, R.styleable.CustomFont)
                if (fontType == -1) {
                    fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
                }
                if (fontWeight == -1) {
                    fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
                }
                a.recycle()
            }

            if (fontType != -1) {
                setCustomFont(textView, fontType, fontWeight)
            }
        } catch (_: Exception) {
        }
    }

    @JvmOverloads
    fun setCustomFont(textView: TextView, @IdRes type: Int, style: Int = -1) {
        val spec = specMap[type] ?: return
        val lifecycleOwner = textView.context.lookupLifecycleOwner()
        lifecycleOwner?.lifecycleScope?.launch {
            val typeface = fontCache.getTypeface(spec.font.createWithWeight(style)) ?: spec.fallback
            runOnMainThread {
                textView.typeface = typeface
            }
        }
    }

    class FontSpec(val loader: () -> FontCache.Font, val fallback: Typeface) {
        constructor(font: FontCache.Font, fallback: Typeface) : this({ font }, fallback)
        constructor(pref: BasePreferenceManager.FontPref, fallback: Typeface) : this(pref::get, fallback)

        val font get() = loader()
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::FontManager)
    }
}
