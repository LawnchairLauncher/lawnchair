package app.lawnchair.fold

import android.content.Context
import android.view.Surface
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile

/**
 * Tells which half of an unfolded book-style foldable swings when the device folds.
 *
 * Stub until `FoldPosture` (Brief C) is merged: wire it in [FoldDockLayout.hingeSource].
 */
fun interface FoldDockHingeSource {
    /**
     * Returns true when the moving half is the left half of [dp]'s window (hinge on the left of
     * the cover screen, `HingeSide.LEFT`), false when it is the right half (`HingeSide.RIGHT`),
     * or null when the fold line isn't vertical, so the dock can't merge.
     */
    fun isMovingHalfLeft(context: Context, dp: DeviceProfile): Boolean?
}

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
 */
class FoldDockLayout(
    val coverIcons: Int,
    val dockIcons: Int,
    val isMovingHalfLeft: Boolean,
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

    /** Returns the slot of [cellX] in the cover dock, or -1 if [cellX] is an extra cell. */
    fun coverSlotOf(cellX: Int): Int {
        val rank = cellXToRank(cellX)
        return if (rank in 0 until coverIcons) rank else -1
    }

    override fun toString() = "FoldDockLayout(cover=$coverIcons, dock=$dockIcons, movingHalfLeft=$isMovingHalfLeft)"

    companion object {
        /**
         * Where the moving half is. The default follows the display rotation: book-style folds
         * have a vertical fold line in their natural rotation, with the hinge on the left of the
         * cover screen; upside down it is on the right; sideways the fold line is horizontal.
         *
         * TODO(fold): replace with `FoldPosture.hingeSide` (Brief C) once merged.
         */
        @JvmStatic
        var hingeSource = FoldDockHingeSource { _, dp ->
            when (dp.deviceProperties.rotationHint) {
                Surface.ROTATION_0 -> true
                Surface.ROTATION_180 -> false
                else -> null
            }
        }

        /**
         * Returns the dock layout of [dp], or null when [dp] isn't an unfolded foldable with a
         * vertical fold line. [extraIcons] is 0 unless the dock merges: [FoldDock.SAME] and
         * docks with several rows or pages keep the cover's slots (see [getShownHotseatIcons]).
         */
        @JvmStatic
        fun create(context: Context, dp: DeviceProfile): FoldDockLayout? {
            if (!dp.deviceProperties.isTwoPanels || dp.isVerticalBarLayout) return null
            val movingHalfLeft = hingeSource.isMovingHalfLeft(context, dp) ?: return null
            val dockIcons = dp.numShownHotseatIcons
            val coverIcons = dp.inv.numShownHotseatIcons.coerceIn(1, dockIcons.coerceAtLeast(1))
            val merges = dp.numHotseatRows == 1 && dp.numHotseatPages == 1
            return FoldDockLayout(
                coverIcons = coverIcons,
                dockIcons = if (merges) dockIcons else coverIcons,
                isMovingHalfLeft = movingHalfLeft,
            )
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
