package app.lawnchair.util

import com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR
import kotlin.math.ceil

/**
 * Utility to calculate hotseat cell height depending on label visibility and touch size.
 */
object DockCellSize {
    /**
     * Calculates the cell height for a dock icon based on whether labels are enabled.
     */
    @JvmStatic
    fun calculate(iconSize: Int, labelHeight: Int, showLabels: Boolean, minimumTouchSize: Int): Int {
        val baseSize = ceil(iconSize * 2 * ICON_OVERLAP_FACTOR).toInt() - iconSize / 2
        return if (showLabels) {
            baseSize + labelHeight
        } else {
            maxOf(baseSize, minimumTouchSize)
        }
    }
}
