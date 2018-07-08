package ch.deletescape.lawnchair.popup

import android.view.View.OnClickListener
import android.view.ViewGroup
import ch.deletescape.lawnchair.openPopupMenu
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.popup.SystemShortcut

class OptionsPopupMenu {

    class WallpaperShortcut : SystemShortcut(R.drawable.ic_wallpaper, R.string.wallpaper_button_text) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo?): OnClickListener {
            return OnClickListener { launcher.onClickWallpaperPicker(it) }
        }
    }

    class WidgetShortcut : SystemShortcut(R.drawable.ic_widget, R.string.widget_button_text) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo?): OnClickListener {
            return OnClickListener {
                AbstractFloatingView.getTopOpenView(launcher)?.close(false)
                launcher.onClickAddWidgetButton(it)
            }
        }
    }

    class SettingsShortcut : SystemShortcut(R.drawable.ic_setting, R.string.settings_button_text) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo?): OnClickListener {
            return OnClickListener { launcher.onClickSettingsButton(it) }
        }
    }

    companion object {


        private var downX = 0
        private var downY = 0

        fun show(launcher: Launcher, resetPosition: Boolean) {
            val dragLayer = launcher.dragLayer
            if (resetPosition) {
                downY = dragLayer.top + dragLayer.height
                downX = dragLayer.left + dragLayer.width
            }
            val dummyBubbleTextView = dragLayer.dummyBubbleTextView
            (dummyBubbleTextView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = downY - dragLayer.top
                leftMargin = downX - dragLayer.left
            }
            dummyBubbleTextView.requestLayout()
            dummyBubbleTextView.post {
                openPopupMenu(dummyBubbleTextView, WallpaperShortcut(), WidgetShortcut(), SettingsShortcut())
            }
            launcher.workspace.requestDisallowInterceptTouchEvent(true)
        }

        fun onTouchDown(x: Int, y: Int) {
            downX = x
            downY = y
        }
    }
}
