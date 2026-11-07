package app.lawnchair.fold

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.launcher3.DeviceProfile
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView

/**
 * The [FoldDrawerLayout.PAGES] drawer transition, "book": the fixed page does not move at all; the
 * apps of the second page rise from below its bottom edge, top row first, driven through the list's
 * [FoldDrawerBook]. The header rows stay on the fixed half. After an untracked fold, on the cover,
 * only the search bar moves: the second page is off the cover. Played only while the first page is
 * on the fixed half: with the "Page order" option putting it on the moving half, the rows seen on
 * the cover move to the other half, and the engine plays [SimpleFoldStyle]'s fade instead.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DrawerBookStyle(
    appsView: ActivityAllAppsContainerView<*>,
    dp: DeviceProfile,
    capture: FoldDrawerState.Capture?,
) : DrawerFoldStyle(appsView, dp, capture) {

    private var book: FoldDrawerBook? = null

    override fun prepare(scene: FoldScene, list: AllAppsRecyclerView) {
        if (onLarge && scene.largeLayout) book = FoldDrawerBook.of(list)?.takeIf { it.isActive }
    }

    override fun apply(scene: FoldScene, list: AllAppsRecyclerView, t: Float) {
        val book = book ?: return
        // On the large layout t is the progress
        book.transitionProgress = t
        book.update()
    }

    override fun onEnd(scene: FoldScene) {
        // First, so the book drives itself again whatever happens below
        book?.let {
            it.transitionProgress = Float.NaN
            it.update()
        }
        book = null
        super.onEnd(scene)
    }
}
