package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.graphics.Bitmap
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.ItemInfo
import com.google.android.apps.nexuslauncher.DynamicDrawableFactory
import com.google.android.apps.nexuslauncher.clock.CustomClock

class LawnchairDrawableFactory(context: Context) : DynamicDrawableFactory(context) {

    private val iconPackManager = IconPackManager.getInstance(context)
    val customClockDrawer = CustomClock(context)

    override fun newIcon(icon: Bitmap, info: ItemInfo): FastBitmapDrawable {
        return iconPackManager.newIcon(icon, info, this)
    }
}
