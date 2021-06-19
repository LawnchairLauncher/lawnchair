package app.lawnchair.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.theme.color.DefaultColorScheme
import com.android.launcher3.util.MainThreadInitializedObject
import dev.kdrag0n.android12ext.monet.theme.ColorScheme
import dev.kdrag0n.android12ext.monet.theme.DynamicColorScheme
import dev.kdrag0n.android12ext.monet.theme.TargetColors

class ColorSchemeCache(private val context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private val targetColors by lazy { TargetColors() }
    private val currentColorScheme = mutableStateOf(loadColorScheme())
    val current get() = currentColorScheme.value

    fun reloadColorScheme() {
        currentColorScheme.value = loadColorScheme()
    }

    private fun loadColorScheme(): ColorScheme {
        val accentColor = prefs.accentColor.get()
        if (accentColor != 0) {
            return DynamicColorScheme(targetColors, accentColor)
        }
        return DefaultColorScheme(context, targetColors)
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::ColorSchemeCache)
    }
}
