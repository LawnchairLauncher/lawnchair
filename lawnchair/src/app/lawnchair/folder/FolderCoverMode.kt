package app.lawnchair.folder

import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.touch.ItemClickHandler

/**
 * "Cover mode" makes a folder display and behave like a single app: its icon shows the folder's
 * first app and a tap launches that app directly, while swiping up still opens the folder to
 * reveal its contents. The toggle and rendering live on [FolderIcon] itself
 * ([FolderIcon.toggleCoverMode], [FolderIcon.updateCoverMode]); this object holds the shared
 * decisions both that class and [ItemClickHandler] need to agree on.
 */
object FolderCoverMode {

    fun isEnabled(info: FolderInfo): Boolean = info.hasOption(FolderInfo.FLAG_COVER_MODE)

    /**
     * The app cover mode should represent, i.e. the first item in the folder's current preview
     * order. Returns null when cover mode is off, the folder is empty, or its first item can't
     * stand in for a single app (e.g. an app pair) -- callers should fall back to the folder's
     * normal behavior in that case. This is always read live from the folder's contents, so an
     * uninstalled cover app or a reordered folder is picked up automatically the next time it's
     * checked, with no extra state to keep in sync.
     */
    fun getCoverItem(folderIcon: FolderIcon): WorkspaceItemInfo? {
        if (!isEnabled(folderIcon.mInfo)) return null
        return folderIcon.getPreviewItemsOnPage(0).firstOrNull() as? WorkspaceItemInfo
    }

    /**
     * Launches the folder's cover app in place of opening the folder.
     * @return false when cover mode isn't applicable, so the caller should open the folder as
     * usual.
     */
    fun launchCoverApp(view: View, folderIcon: FolderIcon): Boolean {
        val item = getCoverItem(folderIcon) ?: return false
        ItemClickHandler.onClickAppShortcut(view, item, Launcher.getLauncher(view.context))
        return true
    }

    fun createGestureListener(folderIcon: FolderIcon): View.OnTouchListener =
        FolderCoverModeGestureListener(folderIcon)
}
