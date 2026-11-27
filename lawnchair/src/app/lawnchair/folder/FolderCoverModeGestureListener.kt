package app.lawnchair.folder

import app.lawnchair.gestures.DirectionalGestureListener
import com.android.launcher3.folder.FolderIcon

/**
 * Recognizes a swipe up on a cover-mode folder icon and opens the folder normally, while leaving
 * taps (which launch the cover app, see [FolderCoverMode]) and every other gesture untouched so
 * reordering and workspace-wide swipes keep working as usual.
 */
class FolderCoverModeGestureListener(
    private val folderIcon: FolderIcon,
) : DirectionalGestureListener(folderIcon.context) {

    override fun onSwipeTop(velocity: Float): Boolean {
        val folder = folderIcon.folder ?: return false
        if (folder.isOpen || folder.isDestroyed) return false
        folderIcon.cancelLongPress()
        folder.animateOpen()
        return true
    }

    override fun onSwipeDown(velocity: Float) = false
    override fun onSwipeLeft(velocity: Float) = false
    override fun onSwipeRight(velocity: Float) = false
}
