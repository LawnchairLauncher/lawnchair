package app.lawnchair.fold

import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.IntArray as LIntArray

/**
 * The two item sets of the "Independent" home layout, in one database.
 *
 * Cover items are stored as always. Items of the large (inner) screen use the same containers
 * (desktop and dock) with screen ids, and dock ranks, from [INNER_SCREEN_BASE] up, so every table,
 * backup and existing database path still sees one ordinary file. Folder contents point at their
 * folder and follow its set. The launcher presents the active set with screen ids from 0, see
 * `CellPosMapper`; the other set stays in the model, never bound and never deleted.
 */
object FoldItemSets {

    /** First model screen id (and dock rank) of the inner set. */
    const val INNER_SCREEN_BASE = 1_000_000

    /** Whether this is a top-level desktop or dock item of the inner set. */
    @JvmStatic
    fun isInnerItem(item: ItemInfo): Boolean = (item.container == CONTAINER_DESKTOP || item.container == CONTAINER_HOTSEAT) &&
        item.screenId >= INNER_SCREEN_BASE

    /**
     * Whether a top-level desktop or dock item belongs to the active set. Other containers (folder
     * contents, predictions) are not filtered here.
     */
    @JvmStatic
    fun isInActiveSet(item: ItemInfo, innerSetActive: Boolean): Boolean {
        if (item.container != CONTAINER_DESKTOP && item.container != CONTAINER_HOTSEAT) return true
        return isInnerItem(item) == innerSetActive
    }

    /** Whether the launcher currently shows the inner set (on any thread, best effort). */
    @JvmStatic
    fun isInnerSetShown(): Boolean = LawnchairLauncher.instance?.deviceProfile?.isInnerItemSet == true

    /** Inner set page ids, numbered from 0, as model screen ids. */
    @JvmStatic
    fun toInnerModelScreenIds(screenIds: LIntArray): LIntArray {
        val result = LIntArray(screenIds.size())
        for (i in 0 until screenIds.size()) {
            result.add(screenIds[i] + INNER_SCREEN_BASE)
        }
        return result
    }
}
