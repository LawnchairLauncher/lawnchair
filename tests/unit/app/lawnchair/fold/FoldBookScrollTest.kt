package app.lawnchair.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scroll arithmetic of the "Two pages" drawer ([FoldDrawerBook]). */
class FoldBookScrollTest {

    // Pages from y = 100 to 1000, rows of 200: 4 whole rows fit, so the second page is 800 up
    private val pageBottom = 1000
    private val shiftY = 800

    @Test
    fun theSecondPageMovesUpByWholeRows() {
        // 900 high pages, rows of 200: 4 rows, the seam 800 below the top
        assertEquals(shiftY, FoldBookScroll.shift(pageHeight = 900, rowHeight = 200))
        assertEquals(900, FoldBookScroll.shift(pageHeight = 900, rowHeight = 300))
        // A row higher than the page still moves by one row; no rows, by the page
        assertEquals(1000, FoldBookScroll.shift(pageHeight = 900, rowHeight = 1000))
        assertEquals(900, FoldBookScroll.shift(pageHeight = 900, rowHeight = 0))
    }

    @Test
    fun aRowCutByTheSeamShowsOnBothPagesAndNeverJumps() {
        // Pages from y = 100, the seam at 900. A row of 200 scrolls up a pixel at a time through
        // the seam: its part above the seam is drawn on the first page, the rest on the second page
        // (moved up by shiftY, clipped at the pages' top). Together always the whole row.
        val pageTop = 100
        val seam = pageTop + shiftY
        var previousFirst = -1
        for (top in seam + 50 downTo seam - 250) {
            val bottom = top + 200
            val second = FoldBookScroll.isOnSecondPage(bottom, seam)
            val onFirst = if (second) (seam - top).coerceIn(0, 200) else 200
            val onSecond = if (second) (bottom - shiftY - maxOf(top - shiftY, pageTop)).coerceIn(0, 200) else 0
            assertEquals("row at $top", 200, onFirst + onSecond)
            // The first page's part only grows as the row comes up: no jump
            assertTrue("row at $top", onFirst >= previousFirst && onFirst - previousFirst <= 1 || previousFirst < 0)
            previousFirst = onFirst
        }
        // Whole on the first page once its bottom reaches the seam, whole on the second page while
        // it starts at or below it
        assertFalse(FoldBookScroll.isOnSecondPage(bottom = seam, seam = seam))
        assertTrue(FoldBookScroll.isOnSecondPage(bottom = seam + 1, seam = seam))
    }

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
