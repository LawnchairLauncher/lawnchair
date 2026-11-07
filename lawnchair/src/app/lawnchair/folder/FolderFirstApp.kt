@file:JvmName("FolderFirstApp")

package app.lawnchair.folder

import app.lawnchair.gestures.DirectionalGestureListener
import app.lawnchair.launcherNullable
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.LauncherState
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.touch.ItemClickHandler

/** Returns the app a tap on [icon] launches instead of opening the folder, if any. */
fun appLaunchedByTap(icon: FolderIcon): WorkspaceItemInfo? {
    if (!tapOpensFirstApp(icon)) return null
    return icon.mInfo.getContents().minByOrNull { it.rank } as? WorkspaceItemInfo
}

fun swipeUpOpensFolder(icon: FolderIcon): Boolean {
    val launcher = icon.context.launcherNullable ?: return false
    return tapOpensFirstApp(icon) && !launcher.isInState(LauncherState.EDIT_MODE)
}

private fun tapOpensFirstApp(icon: FolderIcon): Boolean = !icon.isInAppDrawer &&
    PreferenceManager2.getInstance(icon.context).folderTapOpensFirstApp.firstCached()

class FolderSwipeUpListener(private val icon: FolderIcon) : DirectionalGestureListener(icon.context) {
    override fun onSwipeTop(velocity: Float): Boolean {
        ItemClickHandler.openFolder(icon)
        return true
    }

    override fun onSwipeDown(velocity: Float) = false
    override fun onSwipeLeft(velocity: Float) = false
    override fun onSwipeRight(velocity: Float) = false
}
