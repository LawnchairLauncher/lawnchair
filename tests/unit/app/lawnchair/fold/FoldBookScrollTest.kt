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

    @Test
    fun aRowCutAtTheTopSettlesToTheNearerEdge() {
        val far = 10_000
        // A row already starts at the top
        assertEquals(0, FoldBookScroll.settle(rowTop = 0, rowBottom = 200, toEnd = far))
        // 50 of it scrolled out: back in. 150 out: on to the next row
        assertEquals(-50, FoldBookScroll.settle(rowTop = -50, rowBottom = 150, toEnd = far))
        assertEquals(50, FoldBookScroll.settle(rowTop = -150, rowBottom = 50, toEnd = far))
        // Halfway: the whole row in
        assertEquals(-100, FoldBookScroll.settle(rowTop = -100, rowBottom = 100, toEnd = far))
        // The last item not laid out
        assertEquals(50, FoldBookScroll.settle(rowTop = -150, rowBottom = 50, toEnd = Int.MAX_VALUE))
    }

    @Test
    fun theEndStaysTheEnd() {
        // Not past the end: the next row is 50 away but the end only 30
        assertEquals(30, FoldBookScroll.settle(rowTop = -150, rowBottom = 50, toEnd = 30))
        // The end is farther than the row's start: back to the row
        assertEquals(-20, FoldBookScroll.settle(rowTop = -20, rowBottom = 180, toEnd = 30))
        // At the end: nothing. Past it: back to it
        assertEquals(0, FoldBookScroll.settle(rowTop = -70, rowBottom = 130, toEnd = 0))
        assertEquals(-800, FoldBookScroll.settle(rowTop = -70, rowBottom = 130, toEnd = -800))
    }
}
