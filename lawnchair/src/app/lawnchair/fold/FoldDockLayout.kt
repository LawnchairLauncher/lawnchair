package app.lawnchair.fold

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile

/**
 * The dock of an unfolded foldable when the fold line is vertical ([FoldDock.MERGE]).
 *
 * The cover dock has [coverIcons] slots. The large dock has [dockIcons] cells: the cover's
 * slots keep their order on the fixed half, against its outer edge, and the [extraIcons] extra
 * slots sit on the moving half next to them, the first extra slot nearest. So the edge icon stays
 * at the edge in both states. This only maps hotseat ranks to cells of the first row: the ranks
 * in the database never change.
 *
 * Hinge on the right (the moving half is the right one) keeps the plain order: rank n is cell n.
 *
 * With [isSide] ([FoldDock.SIDE]) the same dock stands as a column on the outer edge of the fixed
 * half, as if the merged dock had turned around the bottom outer corner: the cover's outer edge
 * icon is at the bottom, next to the corner, and the extra slots are at the top ([rankToCellY]).
 */
class FoldDockLayout(
    val coverIcons: Int,
    val dockIcons: Int,
    val isMovingHalfLeft: Boolean,
    val isSide: Boolean = false,
) {
    val extraIcons: Int get() = dockIcons - coverIcons

    /** Returns the first-row cell of the hotseat item at [rank]. */
    fun rankToCellX(rank: Int): Int {
        if (!isMovingHalfLeft || rank !in 0 until dockIcons) return rank
        return if (rank < coverIcons) {
            extraIcons + rank
        } else {
            extraIcons - 1 - (rank - coverIcons)
        }
    }

    /** Returns the hotseat rank of the first-row cell [cellX]. Inverse of [rankToCellX]. */
    fun cellXToRank(cellX: Int): Int {
        if (!isMovingHalfLeft || cellX !in 0 until dockIcons) return cellX
        return if (cellX >= extraIcons) {
            cellX - extraIcons
        } else {
            coverIcons + (extraIcons - 1 - cellX)
        }
    }

    /** Returns the side dock's cell, from the top, of the hotseat item at [rank]. */
    fun rankToCellY(rank: Int): Int {
        val cellX = rankToCellX(rank)
        return if (isMovingHalfLeft) cellX else dockIcons - 1 - cellX
    }

    /** Returns the hotseat rank of the side dock's cell [cellY]. Inverse of [rankToCellY]. */
    fun cellYToRank(cellY: Int): Int = cellXToRank(if (isMovingHalfLeft) cellY else dockIcons - 1 - cellY)

    /** Returns the slot of [cellX] in the cover dock, or -1 if [cellX] is an extra cell. */
    fun coverSlotOf(cellX: Int): Int {
        val rank = cellXToRank(cellX)
        return if (rank in 0 until coverIcons) rank else -1
    }

    override fun toString() = "FoldDockLayout(cover=$coverIcons, dock=$dockIcons, movingHalfLeft=$isMovingHalfLeft, side=$isSide)"

    companion object {
        /**
         * Returns the dock layout of [dp], or null when [dp] isn't an unfolded foldable with a
         * vertical fold line. [extraIcons] is 0 unless the dock merges: [FoldDock.SAME] and
         * docks with several rows or pages keep the cover's slots (see [getShownHotseatIcons]).
         */
        @JvmStatic
        fun create(dp: DeviceProfile): FoldDockLayout? {
            if (!dp.deviceProperties.isTwoPanels || dp.isVerticalBarLayout) return null
            // With "Independent" the large screen has its own dock (FoldItemSets): nothing merges
            // into it from the cover, so it is a plain dock unless it stands at the side.
            if (dp.isInnerItemSet && !dp.isSideDock) return null
            val movingHalfLeft = FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint) ?: return null
            val dockIcons = dp.numShownHotseatIcons
            val coverIcons = dp.inv.numShownHotseatIcons.coerceIn(1, dockIcons.coerceAtLeast(1))
            val merges = dp.numHotseatRows == 1 && dp.numHotseatPages == 1
            return FoldDockLayout(
                coverIcons = coverIcons,
                dockIcons = if (merges) dockIcons else coverIcons,
                isMovingHalfLeft = movingHalfLeft,
                isSide = dp.isSideDock,
            )
        }

        /**
         * Returns whether the side dock ([FoldDock.SIDE]) is on the left edge, or null when [dp]
         * has no side dock: not an unfolded foldable, no vertical fold line, the dock disabled, or
         * another option. The column goes on the outer edge of the fixed half, opposite the
         * moving half. Called while [dp] is being built: it only reads its device properties.
         */
        @JvmStatic
        fun getSideDockOnLeft(context: Context, dp: DeviceProfile): Boolean? {
            if (!dp.deviceProperties.isTwoPanels || dp.isVerticalBarLayout) return null
            val prefs2 = PreferenceManager2.getInstance(context)
            if (prefs2.foldDock.firstCached(prefs2) != FoldDock.SIDE) return null
            if (!prefs2.isHotseatEnabled.firstCached(prefs2)) return null
            return FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint)?.not()
        }

        /**
         * Returns how many icons a dock row shows, given the [shown] count of the display option.
         *
         * An unfolded foldable shows the cover's count when the dock doesn't merge: with
         * [FoldDock.SAME], and with 2 rows or several pages. Hotseat ranks are flat, so the
         * cover's rows and pages already use every rank the database holds
         * (`numDatabaseHotseatIcons`); extra cells there would need new ranks, which means a grid
         * migration. Mirroring the cover keeps every icon in its row and page.
         */
        @JvmStatic
        fun getShownHotseatIcons(
            context: Context,
            inv: InvariantDeviceProfile,
            isTwoPanels: Boolean,
            shown: Int,
            rows: Int,
            pages: Int,
        ): Int {
            if (!isTwoPanels) return shown
            val prefs2 = PreferenceManager2.getInstance(context)
            val sameAsCover = prefs2.foldDock.firstCached(prefs2) == FoldDock.SAME || rows * pages > 1
            return if (sameAsCover) shown.coerceAtMost(inv.numShownHotseatIcons) else shown
        }
    }
}
