package app.lawnchair.folder

import android.graphics.Rect
import android.graphics.RectF
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.OptionsPopupView

/**
 * Home-screen folders aren't [com.android.launcher3.BubbleTextView]s, so a long-press on one
 * can't use the app icon's `PopupContainerWithArrow` options popup. Left unhandled, a folder's
 * long-press falls straight into a drag/reorder with no way to reach its options -- unlike an
 * app icon, where a plain long-press shows a popup and only turns into a drag once the finger
 * moves past a threshold.
 *
 * This builds a lighter options popup (the same [OptionsPopupView] mechanism already used for
 * app-drawer folders) together with a matching [DragOptions.PreDragCondition], so a folder's
 * long-press behaves the same way: show the popup immediately, only start a real drag once the
 * finger moves far enough.
 */
object FolderLongPress {

    fun startLongPressAction(folderIcon: FolderIcon): DragOptions.PreDragCondition? {
        val folderInfo = folderIcon.mInfo
        // App-drawer folders show their own popup via getAllAppsItemLongClickListener and never
        // reach this path; this guard just keeps that behavior unchanged if that ever changes.
        if (folderInfo.container == ItemInfo.NO_ID) return null

        val launcher = Launcher.getLauncher(folderIcon.context)
        val bounds = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(folderIcon, bounds)
        val popup = OptionsPopupView.show<Launcher>(
            launcher,
            RectF(bounds),
            listOf(
                OptionsPopupView.OptionItem(
                    launcher,
                    if (FolderCoverMode.isEnabled(folderInfo)) {
                        R.string.disable_cover_mode
                    } else {
                        R.string.enable_cover_mode
                    },
                    R.drawable.ic_apps,
                    StatsLogManager.LauncherEvent.IGNORE,
                ) {
                    folderIcon.toggleCoverMode()
                    true
                },
            ),
            true,
        ) ?: return null

        val threshold = folderIcon.resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > threshold

            override fun onPreDragStart(dragObject: DropTarget.DragObject) {
                folderIcon.setIconVisible(false)
            }

            override fun onPreDragEnd(dragObject: DropTarget.DragObject, dragStarted: Boolean) {
                folderIcon.setIconVisible(true)
                if (dragStarted) {
                    popup.close(true)
                }
            }
        }
    }
}
