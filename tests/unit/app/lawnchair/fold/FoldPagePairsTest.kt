package app.lawnchair.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How home pages pair up on a foldable's two panels, for both page orders. */
class FoldPagePairsTest {

    // Pages 0..4; the hinge side is "moving half left" (fixed half on the right) or not
    private val pageCount = 5

    @Test
    fun openingRunsFromTheFixedHalfReadingFollowsTheLanguage() {
        for (rtl in listOf(false, true)) {
            assertTrue(FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, movingHalfLeft = true))
            assertFalse(FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, movingHalfLeft = false))
            for (movingHalfLeft in listOf(true, false, null)) {
                assertEquals(rtl, FoldPagePairs.isPageOrderRtl(FoldPageOrder.READING, rtl, movingHalfLeft))
            }
            // Hinge side unknown: the language
            assertEquals(rtl, FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, movingHalfLeft = null))
        }
    }

    @Test
    fun onlyReadingTowardsTheFixedHalfPutsItSecond() {
        for (rtl in listOf(false, true)) {
            for (movingHalfLeft in listOf(true, false, null)) {
                assertFalse(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.OPENING, rtl, movingHalfLeft))
            }
            assertFalse(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, rtl, movingHalfLeft = null))
        }
        // Left to right with the fixed half on the right (end), or right to left with it on the left
        assertTrue(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = true))
        assertTrue(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, isRtlLanguage = true, movingHalfLeft = false))
        // The fixed half on the start side: the same as the opening direction
        assertFalse(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = false))
        assertFalse(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, isRtlLanguage = true, movingHalfLeft = true))
    }

    @Test
    fun theAnchorStaysOnTheFixedHalf() {
        for (order in FoldPageOrder.entries) {
            for (movingHalfLeft in listOf(true, false)) {
                val fixedHalfSecond = FoldPagePairs.isFixedHalfSecond(order, isRtlLanguage = false, movingHalfLeft)
                // First, middle (even and odd) and last page
                for (anchor in listOf(0, 2, 3, pageCount - 1)) {
                    val parity = FoldPagePairs.pairParity(anchor, fixedHalfSecond)
                    val start = FoldPagePairs.pairStart(anchor, parity)
                    val fixedPage = FoldPagePairs.fixedHalfPage(start, pageCount, fixedHalfSecond)
                    val case = "$order, movingHalfLeft=$movingHalfLeft, anchor=$anchor"
                    if (fixedHalfSecond && anchor == 0) {
                        // The first page can't end a pair: (0, 1), page 1 on the fixed half
                        assertEquals(case, 0, start)
                        assertEquals(case, 1, fixedPage)
                    } else {
                        assertEquals(case, anchor, fixedPage)
                    }
                    assertEquals(case, if (fixedHalfSecond && anchor > 0) anchor - 1 else anchor, start)
                }
            }
        }
    }

    @Test
    fun readingLeftToRightWithTheFixedHalfOnTheRight() {
        val fixedHalfSecond = FoldPagePairs.isFixedHalfSecond(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = true)
        // Unfolded on page 2: [1 | 2], the first page alone (shown with page 1), then [3 | 4]
        assertEquals(1, FoldPagePairs.pairParity(2, fixedHalfSecond))
        assertEquals(listOf(0, 1, 1, 3, 3), pairStarts(parity = 1))
        assertEquals(1, FoldPagePairs.pairedPage(2, fixedHalfSecond))
        // Unfolded on page 1 or 3: [0 | 1], [2 | 3], then page 4 alone
        assertEquals(0, FoldPagePairs.pairParity(1, fixedHalfSecond))
        assertEquals(0, FoldPagePairs.pairParity(3, fixedHalfSecond))
        assertEquals(listOf(0, 0, 2, 2, 4), pairStarts(parity = 0))
        // Unfolded on the last page: [3 | 4]
        assertEquals(1, FoldPagePairs.pairParity(4, fixedHalfSecond))
        assertEquals(3, FoldPagePairs.pairedPage(4, fixedHalfSecond))
        // Unfolded on the first page: [0 | 1]
        assertEquals(0, FoldPagePairs.pairParity(0, fixedHalfSecond))
        assertEquals(1, FoldPagePairs.pairedPage(0, fixedHalfSecond))
    }

    @Test
    fun theFixedHalfPageOfEachPair() {
        // The first page alone, shown with page 1 on the fixed half
        assertEquals(1, FoldPagePairs.fixedHalfPage(0, pageCount, fixedHalfSecond = true))
        assertEquals(3, FoldPagePairs.fixedHalfPage(2, pageCount, fixedHalfSecond = true))
        // The last page alone stays the page shown
        assertEquals(4, FoldPagePairs.fixedHalfPage(4, pageCount, fixedHalfSecond = true))
        for (start in 0 until pageCount) {
            assertEquals(start, FoldPagePairs.fixedHalfPage(start, pageCount, fixedHalfSecond = false))
        }
    }

    @Test
    fun openingKeepsThePairsOfBefore() {
        // The parity was indexOf(anchor) % 2 with -1 and 0 giving 0, and the pairs AOSP's for parity
        // 0 and max(0, page - floorMod(page - 1, 2)) for parity 1.
        for (anchor in -1..6) {
            assertEquals(if (anchor > 0) anchor % 2 else 0, FoldPagePairs.pairParity(anchor, fixedHalfSecond = false))
            assertEquals(anchor + 1, FoldPagePairs.pairedPage(anchor, fixedHalfSecond = false))
        }
        for (page in 0..6) {
            assertEquals(page - page % 2, FoldPagePairs.pairStart(page, 0))
            assertEquals(maxOf(0, page - Math.floorMod(page - 1, 2)), FoldPagePairs.pairStart(page, 1))
        }
    }

    private fun pairStarts(parity: Int) = (0 until pageCount).map { FoldPagePairs.pairStart(it, parity) }
}
