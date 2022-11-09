package app.lawnchair.wallpaper

import android.app.WallpaperManager
import android.content.Context
import app.lawnchair.util.requireSystemService
import app.lawnchair.wallpaper.WallpaperColorsCompat.Companion.HINT_SUPPORTS_DARK_THEME
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject

sealed class WallpaperManagerCompat(val context: Context) {

    private val listeners = mutableListOf<OnColorsChangedListener>()
    protected val wallpaperManager: WallpaperManager = context.requireSystemService()

    abstract val wallpaperColors: WallpaperColorsCompat?

    val colorHints: Int get() = wallpaperColors?.colorHints ?: 0
    val supportsDarkTheme: Boolean get() = (colorHints and HINT_SUPPORTS_DARK_THEME) != 0

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
