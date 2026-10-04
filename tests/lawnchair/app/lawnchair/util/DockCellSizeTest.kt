package app.lawnchair.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DockCellSizeTest {
    @Test
    fun hidingLabelsRemovesTheLabelContainerButKeepsFolderOverlap() {
        assertEquals(162, DockCellSize.calculate(144, 45, false, 144))
        assertEquals(162, DockCellSize.calculate(144, 0, false, 144))
        assertEquals(297, DockCellSize.calculate(144, 45, true, 144))
    }

    @Test
    fun smallIconsKeepAMinimumTouchTarget() {
        assertEquals(144, DockCellSize.calculate(90, 0, false, 144))
    }
}
