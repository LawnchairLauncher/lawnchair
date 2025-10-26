package app.lawnchair.font

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.res.use
import androidx.lifecycle.lifecycleScope
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.lookupLifecycleOwner
import app.lawnchair.util.runOnMainThread
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.launch

@LauncherAppSingleton
class FontManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val fontCache = FontCache.INSTANCE.get(context)

    private val specMap = createFontMap()

    private fun createFontMap(): Map<Int, FontSpec> {
        val sansSerif = Typeface.SANS_SERIF
        val sansSerifMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val prefs = PreferenceManager.getInstance(context)
        return mapOf(
            R.id.font_base_icon to FontSpec(prefs.fontWorkspace, sansSerif),
            R.id.font_button to FontSpec(prefs.fontHeadingMedium, sansSerifMedium),
            R.id.font_heading to FontSpec(prefs.fontHeading, sansSerif),
            R.id.font_heading_medium to FontSpec(prefs.fontHeadingMedium, sansSerif),
            R.id.font_body to FontSpec(prefs.fontBody, sansSerif),
            R.id.font_body_medium to FontSpec(prefs.fontBodyMedium, sansSerif),
        )
    }

    fun overrideFont(textView: TextView, attrs: AttributeSet?) {
        try {
            val context = textView.context
            var fontType = -1
            var fontWeight = -1
            var ap = -1
            context.obtainStyledAttributes(
                attrs,
                R.styleable.CustomFont,
            ).use { a ->
                fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
                fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
                ap = a.getResourceId(R.styleable.CustomFont_android_textAppearance, -1)
            }

            if (ap != -1) {
                context.obtainStyledAttributes(ap, R.styleable.CustomFont).use { a ->
                    if (fontType == -1) {
                        fontType = a.getResourceId(R.styleable.CustomFont_customFontType, -1)
                    }
                    if (fontWeight == -1) {
                        fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
                    }
                }
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

    override fun close() {
        TODO("Not yet implemented")
    }

    class FontSpec(val loader: () -> FontCache.Font, val fallback: Typeface) {
        constructor(pref: BasePreferenceManager.FontPref, fallback: Typeface) : this(pref::get, fallback)

        val font get() = loader()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFontManager)
    }
}
