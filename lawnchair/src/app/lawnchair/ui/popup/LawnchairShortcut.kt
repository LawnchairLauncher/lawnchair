package app.lawnchair.ui.popup

import android.content.pm.LauncherActivityInfo
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.override.CustomizeAppDialog
import app.lawnchair.views.showBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ComponentKey

class LawnchairShortcut {

    companion object {
        val CUSTOMIZE = SystemShortcut.Factory<LawnchairLauncher> { activity, itemInfo ->
            if (itemInfo.itemType == ITEM_TYPE_APPLICATION) Customize(activity, itemInfo) else null
        }
    }

    class Customize(
        private val launcher: LawnchairLauncher,
        itemInfo: ItemInfo
    ) : SystemShortcut<LawnchairLauncher>(R.drawable.ic_edit, R.string.customize_button_text, launcher, itemInfo) {

        @OptIn(ExperimentalMaterialApi::class)
        override fun onClick(v: View) {
            val outObj = Array<Any?>(1) { null }
            val key = ComponentKey(mItemInfo.targetComponent, mItemInfo.user)
            val appInfo = launcher.appsView.appsStore.getApp(key)!!
            var icon = Utilities.loadFullDrawableWithoutTheme(launcher, appInfo, 0, 0, outObj)
            if (mItemInfo.screenId != NO_ID && icon is BitmapInfo.Extender) {
                icon = icon.getThemedDrawable(launcher)
            }
            val launcherActivityInfo = outObj[0] as LauncherActivityInfo
            val defaultTitle = launcherActivityInfo.label.toString()

            AbstractFloatingView.closeAllOpenViews(launcher)
            launcher.showBottomSheet(
                contentPaddings = PaddingValues(bottom = 64.dp)
            ) {
                CustomizeAppDialog(
                    icon = icon,
                    defaultTitle = defaultTitle,
                    componentKey = key
                )
            }
        }
    }
}
