package app.lawnchair.fold

import org.junit.Assert.assertEquals
import org.junit.Test

/** The scroll arithmetic of the "Two pages" drawer ([FoldDrawerBook]). */
class FoldBookScrollTest {

    // Pages from y = 100 to 1000, rows of 200: 4 whole rows fit, so the second page is 800 up
    private val pageBottom = 1000
    private val shiftY = 800

    @Test
    fun theListEndsWithItsLastRowAtTheBottomOfTheSecondPage() {
        // Drawn at 1800 - 800 = 1000, the second page's bottom: the end
        assertEquals(0, FoldBookScroll.toEnd(lastBottom = 1800, shiftY, pageBottom))
        // One row below it: one row to go
        assertEquals(200, FoldBookScroll.toEnd(lastBottom = 2000, shiftY, pageBottom))
        // Where a plain list ends, the last row at the first page's bottom: past the end, the
        // second page empty
        assertEquals(-800, FoldBookScroll.toEnd(lastBottom = pageBottom, shiftY, pageBottom))
    }
}
