package app.lawnchair.wallpaper

import android.app.WallpaperManager
import android.content.Context
import androidx.core.content.getSystemService
import app.lawnchair.wallpaper.WallpaperColorsCompat.Companion.HINT_SUPPORTS_DARK_THEME
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject

abstract class WallpaperManagerCompat(val context: Context) {

    private val listeners = mutableListOf<OnColorsChangedListener>()
    protected val wallpaperManager = context.getSystemService<WallpaperManager>()!!

    abstract val wallpaperColors: WallpaperColorsCompat?

    val colorHints get() = wallpaperColors?.colorHints ?: 0
    val supportsDarkTheme get() = (colorHints and HINT_SUPPORTS_DARK_THEME) != 0

    fun addOnChangeListener(listener: OnColorsChangedListener) {
        listeners.add(listener)
    }

    fun removeOnChangeListener(listener: OnColorsChangedListener) {
        listeners.remove(listener)
    }

    protected fun notifyChange() {
        listeners.toTypedArray().forEach {
            it.onColorsChanged()
        }
    }

    interface OnColorsChangedListener {
        fun onColorsChanged()
    }

    companion object {

        @JvmField
        val INSTANCE = MainThreadInitializedObject { context ->
            when {
                Utilities.ATLEAST_S -> WallpaperManagerCompatVS(context)
                Utilities.ATLEAST_O_MR1 -> WallpaperManagerCompatVOMR1(context)
                else -> WallpaperManagerCompatVO(context)
            }
        }
    }
}
