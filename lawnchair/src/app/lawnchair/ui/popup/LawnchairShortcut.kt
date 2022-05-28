package app.lawnchair.ui.popup

import android.content.pm.LauncherActivityInfo
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.override.CustomizeAppDialog
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppInfo as ModelAppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ComponentKey

class LawnchairShortcut {

    companion object {
        val CUSTOMIZE = SystemShortcut.Factory<LawnchairLauncher> { activity, itemInfo ->
            getAppInfo(activity, itemInfo)?.let { Customize(activity, it, itemInfo) }
        }

        private fun getAppInfo(launcher: LawnchairLauncher, itemInfo: ItemInfo): ModelAppInfo? {
            if (itemInfo is ModelAppInfo) return itemInfo
            if (itemInfo.itemType != ITEM_TYPE_APPLICATION) return null
            val key = ComponentKey(itemInfo.targetComponent, itemInfo.user)
            return launcher.appsView.appsStore.getApp(key)
        }
    }

    class Customize(
        private val launcher: LawnchairLauncher,
        private val appInfo: ModelAppInfo,
        itemInfo: ItemInfo
    ) : SystemShortcut<LawnchairLauncher>(R.drawable.ic_edit, R.string.customize_button_text, launcher, itemInfo) {

        override fun onClick(v: View) {
            val outObj = Array<Any?>(1) { null }
            var icon = Utilities.loadFullDrawableWithoutTheme(launcher, appInfo, 0, 0, outObj)
            if (mItemInfo.screenId != NO_ID && icon is BitmapInfo.Extender) {
                icon = icon.getThemedDrawable(launcher)
            }
            val launcherActivityInfo = outObj[0] as LauncherActivityInfo
            val defaultTitle = launcherActivityInfo.label.toString()

            AbstractFloatingView.closeAllOpenViews(launcher)
            ComposeBottomSheet.show(
                context = launcher,
                contentPaddings = PaddingValues(bottom = 64.dp)
            ) {
                CustomizeAppDialog(
                    icon = icon,
                    defaultTitle = defaultTitle,
                    componentKey = appInfo.toComponentKey(),
                    onClose = { close(true) }
                )
            }
        }
    }
}
