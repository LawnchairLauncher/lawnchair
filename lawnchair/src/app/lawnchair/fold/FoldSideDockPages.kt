package app.lawnchair.fold

import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Workspace

/**
 * Makes room for the side dock ([FoldDock.SIDE]) on the page of the fixed half only.
 *
 * The workspace keeps its normal side margins: [DeviceProfile.getSideDockPageInset] comes off
 * both sides of its padding, so both pages stay exactly one half wide and meet at the hinge. Each
 * pair's first page, the one on the fixed half (Worker C's pairing, see
 * [Workspace.getLeftmostVisiblePageForIndex]), takes that width back as extra padding on its
 * outer edge, under the column. The moving half's page keeps the full half.
 */
object FoldSideDockPages {
    @JvmStatic
    fun applyPagePadding(workspace: Workspace<*>, dp: DeviceProfile) {
        val inset = dp.sideDockPageInset
        val base = dp.cellLayoutPaddingPx
        val onLeft = dp.isSideDockOnLeft
        for (i in 0 until workspace.childCount) {
            val page = workspace.getChildAt(i) as? CellLayout ?: continue
            val fixedHalf = inset > 0 && workspace.getLeftmostVisiblePageForIndex(i) == i
            val left = base.left + if (fixedHalf && onLeft) inset else 0
            val right = base.right + if (fixedHalf && !onLeft) inset else 0
            if (page.paddingLeft != left || page.paddingRight != right ||
                page.paddingTop != base.top || page.paddingBottom != base.bottom
            ) {
                page.setPadding(left, base.top, right, base.bottom)
            }
        }
    }
}
