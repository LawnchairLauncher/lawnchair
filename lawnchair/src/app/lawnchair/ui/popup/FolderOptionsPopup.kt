package app.lawnchair.ui.popup

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcherNullable
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.SelectFolderIcon
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.OptionsPopupView

/**
 * LC: Long-pressing a home screen folder shows a small menu to rename the folder or pick an icon
 * for it. Like the app shortcut popup, it closes as soon as the folder is dragged.
 */
object FolderOptionsPopup {

    @JvmStatic
    fun startLongPressAction(folderIcon: FolderIcon): DragOptions.PreDragCondition? {
        val launcher = folderIcon.context.launcherNullable ?: return null
        val info = folderIcon.mInfo ?: return null
        if (info.id == ItemInfo.NO_ID) return null
        if (info.container != CONTAINER_DESKTOP && info.container != CONTAINER_HOTSEAT) return null

        val bounds = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(folderIcon, bounds)
        val itemId = info.id
        val title = info.title?.toString().orEmpty()
        val popup = OptionsPopupView.show<LawnchairLauncher>(
            launcher,
            RectF(bounds),
            listOf(
                OptionsPopupView.OptionItem(
                    launcher,
                    R.string.folder_rename,
                    R.drawable.ic_edit,
                    StatsLogManager.LauncherEvent.IGNORE,
                ) {
                    AbstractFloatingView.closeAllOpenViews(launcher, false)
                    folderIcon.folder.openAndEditName()
                    true
                },
                OptionsPopupView.OptionItem(
                    launcher,
                    R.string.folder_change_icon,
                    R.drawable.ic_palette,
                    StatsLogManager.LauncherEvent.IGNORE,
                ) {
                    launcher.startActivity(
                        PreferenceActivity.createIntent(launcher, SelectFolderIcon(itemId, title)),
                    )
                    true
                },
            ),
            true,
        ) ?: return null

        val threshold = launcher.resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > threshold

            override fun onPreDragStart(dragObject: DropTarget.DragObject) = Unit

            override fun onPreDragEnd(dragObject: DropTarget.DragObject, dragStarted: Boolean) {
                if (dragStarted) {
                    popup.close(true)
                } else {
                    // Long press released without dragging: keep the menu, show the folder again.
                    folderIcon.visibility = View.VISIBLE
                }
            }
        }
    }
}
