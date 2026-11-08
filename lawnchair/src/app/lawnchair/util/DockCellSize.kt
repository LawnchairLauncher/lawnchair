package app.lawnchair.util

import com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR
import kotlin.math.ceil

object DockCellSize {
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
