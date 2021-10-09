package app.lawnchair.icons

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.icons.ClockDrawableWrapper

data class IconEntry(val iconPack: IconPack, val name: String) {

    fun getDrawable(iconDpi: Int, user: UserHandle): Drawable? {
        val drawable = iconPack.getIcon(this, iconDpi) ?: return null
        val clockMetadata = if (user == Process.myUserHandle()) iconPack.getClock(this) else null
        if (clockMetadata != null) {
            val clockDrawable = ClockDrawableWrapper.forMeta(Build.VERSION.SDK_INT, clockMetadata) {
                drawable
            }
            if (clockDrawable != null) {
                return clockDrawable
            }
        }
        return drawable
    }
}

data class CalendarIconEntry(val iconPack: IconPack, val prefix: String) {
    fun getIconEntry(day: Int) = IconEntry(iconPack, "$prefix${day + 1}")
}
