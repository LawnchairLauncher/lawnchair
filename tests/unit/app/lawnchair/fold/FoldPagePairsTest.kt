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
            assertTrue(FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, uprightMovingHalfLeft = true))
            assertFalse(FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, uprightMovingHalfLeft = false))
            for (movingHalfLeft in listOf(true, false, null)) {
                assertEquals(rtl, FoldPagePairs.isPageOrderRtl(FoldPageOrder.READING, rtl, movingHalfLeft))
            }
            // Hinge side unknown: the language
            assertEquals(rtl, FoldPagePairs.isPageOrderRtl(FoldPageOrder.OPENING, rtl, uprightMovingHalfLeft = null))
        }
    }

    @Test
    fun uprightOnlyReadingTowardsTheFixedHalfPutsItSecond() {
        for (rtl in listOf(false, true)) {
            for (movingHalfLeft in listOf(true, false, null)) {
                assertFalse(upright(FoldPageOrder.OPENING, rtl, movingHalfLeft))
            }
            assertFalse(upright(FoldPageOrder.READING, rtl, movingHalfLeft = null))
        }
        // Left to right with the fixed half on the right (end), or right to left with it on the left
        assertTrue(upright(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = true))
        assertTrue(upright(FoldPageOrder.READING, isRtlLanguage = true, movingHalfLeft = false))
        // The fixed half on the start side: the same as the opening direction
        assertFalse(upright(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = false))
        assertFalse(upright(FoldPageOrder.READING, isRtlLanguage = true, movingHalfLeft = true))
    }

    @Test
    fun rotatingNeverReversesThePageOrder() {
        // A book-style foldable: the moving half is the left one upright, none sideways, the right
        // one upside down
        for (order in FoldPageOrder.entries) {
            for (rtl in listOf(false, true)) {
                val rtlOrder = FoldPagePairs.isPageOrderRtl(order, rtl, uprightMovingHalfLeft = true)
                assertEquals("$order, rtl=$rtl", order == FoldPageOrder.OPENING || rtl, rtlOrder)
                val uprightSecond = FoldPagePairs.isFixedHalfSecond(order, rtl, uprightMovingHalfLeft = true, movingHalfLeft = true)
                // Sideways: no fixed half, the pairs of the upright device
                assertEquals(uprightSecond, FoldPagePairs.isFixedHalfSecond(order, rtl, uprightMovingHalfLeft = true, movingHalfLeft = null))
                // Upside down: the same order, the fixed half on the other side, so the page the
                // user was on is the other one of its pair
                assertEquals(!uprightSecond, FoldPagePairs.isFixedHalfSecond(order, rtl, uprightMovingHalfLeft = true, movingHalfLeft = false))
            }
        }
        // Opening direction in English upright: [3 | 2], page 2 on the fixed (right) half. Upside
        // down the fixed half is on the left: [2 | 1], still right to left, page 2 kept there.
        val upsideDown = FoldPagePairs.isFixedHalfSecond(FoldPageOrder.OPENING, false, uprightMovingHalfLeft = true, movingHalfLeft = false)
        assertTrue(upsideDown)
        assertEquals(1, FoldPagePairs.pairStart(2, FoldPagePairs.pairParity(2, upsideDown)))
        assertEquals(1, FoldPagePairs.pairedPage(2, upsideDown))
        // Flip phones (horizontal hinge upright): no side at all, the language
        assertFalse(FoldPagePairs.isFixedHalfSecond(FoldPageOrder.OPENING, false, uprightMovingHalfLeft = null, movingHalfLeft = null))
    }

    @Test
    fun theAnchorStaysOnTheFixedHalf() {
        for (order in FoldPageOrder.entries) {
            for (movingHalfLeft in listOf(true, false)) {
                val fixedHalfSecond = upright(order, isRtlLanguage = false, movingHalfLeft)
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
        val fixedHalfSecond = upright(FoldPageOrder.READING, isRtlLanguage = false, movingHalfLeft = true)
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

    /** [FoldPagePairs.isFixedHalfSecond] with the device upright: its hinge side is the upright one. */
    private fun upright(order: FoldPageOrder, isRtlLanguage: Boolean, movingHalfLeft: Boolean?) = FoldPagePairs.isFixedHalfSecond(order, isRtlLanguage, movingHalfLeft, movingHalfLeft)
}
