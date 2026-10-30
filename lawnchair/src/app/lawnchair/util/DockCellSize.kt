package app.lawnchair.util

import com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR
import kotlin.math.ceil

object DockCellSize {
    @JvmStatic
    fun calculate(iconSize: Int, labelHeight: Int, showLabels: Boolean, minimumTouchSize: Int): Int = if (showLabels) {
        ceil(iconSize * 2 * ICON_OVERLAP_FACTOR).toInt() - iconSize / 2 + labelHeight
    } else {
        // Hidden labels need only the icon/folder footprint, not the taller label container.
        maxOf(ceil(iconSize * ICON_OVERLAP_FACTOR).toInt(), minimumTouchSize)
    }
}
