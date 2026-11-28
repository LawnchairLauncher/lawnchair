package app.lawnchair.gestures

import android.view.MotionEvent
import android.view.View
import com.android.launcher3.Launcher

/**
 * LC: whether a touch (in drag layer coordinates) starts on a home screen or dock icon that has
 * a swipe gesture in that direction. The launcher's own swipes (app drawer, notifications) then
 * leave the touch to the icon.
 */
object IconSwipeTouch {

    @JvmStatic
    fun isOnIconWithSwipeGesture(launcher: Launcher, ev: MotionEvent, vertical: Boolean): Boolean {
        val dragLayer = launcher.dragLayer ?: return false
        launcher.workspace?.let { workspace ->
            val coord = floatArrayOf(ev.x, ev.y)
            dragLayer.mapCoordInSelfToDescendant(workspace, coord)
            if (workspace.isTouchOnIconWithSwipeGesture(coord[0], coord[1], vertical)) return true
        }
        // The dock is not part of the workspace pages.
        val hotseat = launcher.hotseat ?: return false
        if (hotseat.visibility != View.VISIBLE) return false
        val pagedView = hotseat.pagedView ?: return false
        val coord = floatArrayOf(ev.x, ev.y)
        // Maps into the pages' coordinates, scroll included.
        dragLayer.mapCoordInSelfToDescendant(pagedView, coord)
        val icon = pagedView.findIcon(coord[0], coord[1]) ?: return false
        return if (vertical) {
            icon.hasConfiguredVerticalIconSwipeGesture()
        } else {
            icon.hasConfiguredHorizontalIconSwipeGesture()
        }
    }
}
