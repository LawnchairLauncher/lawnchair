package app.lawnchair.theme

import android.content.Context
import androidx.annotation.IntDef
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject

abstract class WallpaperManagerCompat(val context: Context) {

    private val listeners = mutableListOf<OnColorsChangedListener>()

    @ColorsHints
    abstract val colorHints: Int

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

        const val HINT_SUPPORTS_DARK_TEXT = 1 shl 0
        const val HINT_SUPPORTS_DARK_THEME = 1 shl 1

        @IntDef(
            value = [HINT_SUPPORTS_DARK_TEXT, HINT_SUPPORTS_DARK_THEME],
            flag = true
        )
        @Retention(
            AnnotationRetention.SOURCE
        )
        annotation class ColorsHints

        @JvmField
        val INSTANCE = MainThreadInitializedObject { context ->
            when {
                Utilities.ATLEAST_S -> WallpaperManagerCompatVS(context)
                Utilities.ATLEAST_P -> WallpaperManagerCompatVP(context)
                else -> WallpaperManagerCompatVO(context)
            }
        }
    }
}
