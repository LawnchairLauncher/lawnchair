package app.lawnchair.fold

import app.lawnchair.fold.FoldItemSets.INNER_SCREEN_BASE
import org.junit.Assert.assertEquals
import org.junit.Test

/** The item set encoding of the "Independent" home layout: page ids and dock ranks. */
class FoldItemSetsTest {

    @Test
    fun coverIdsAreTheModelIds() {
        for (id in listOf(0, 1, 7, INNER_SCREEN_BASE - 1)) {
            assertEquals(id, FoldItemSets.toModelScreenId(id, inner = false))
            assertEquals(id, FoldItemSets.toPresenterScreenId(id))
        }
    }

    @Test
    fun innerIdsStartAtTheBase() {
        assertEquals(INNER_SCREEN_BASE, FoldItemSets.toModelScreenId(0, inner = true))
        assertEquals(INNER_SCREEN_BASE + 3, FoldItemSets.toModelScreenId(3, inner = true))
        assertEquals(0, FoldItemSets.toPresenterScreenId(INNER_SCREEN_BASE))
        assertEquals(3, FoldItemSets.toPresenterScreenId(INNER_SCREEN_BASE + 3))
    }

    @Test
    fun theBaseIsTheFirstInnerId() {
        assertEquals(INNER_SCREEN_BASE - 1, FoldItemSets.toPresenterScreenId(INNER_SCREEN_BASE - 1))
        assertEquals(0, FoldItemSets.toPresenterScreenId(INNER_SCREEN_BASE))
        assertEquals(1, FoldItemSets.toPresenterScreenId(INNER_SCREEN_BASE + 1))
    }

    @Test
    fun noPageStaysNoPage() {
        assertEquals(-1, FoldItemSets.toModelScreenId(-1, inner = true))
        assertEquals(-1, FoldItemSets.toModelScreenId(-1, inner = false))
        assertEquals(-1, FoldItemSets.toPresenterScreenId(-1))
    }

    @Test
    fun dockRanksRoundTripInBothSets() {
        // Dock ranks use the page encoding: rank r of the inner dock is stored as base + r.
        for (rank in 0 until 12) {
            for (inner in listOf(false, true)) {
                val model = FoldItemSets.toModelScreenId(rank, inner)
                assertEquals(inner, model >= INNER_SCREEN_BASE)
                assertEquals(rank, FoldItemSets.toPresenterScreenId(model))
            }
        }
    }

    @Test
    fun setConditionsSplitAtTheBase() {
        assertEquals("screen >= $INNER_SCREEN_BASE", FoldItemSets.whereItemSet(inner = true))
        assertEquals("screen < $INNER_SCREEN_BASE", FoldItemSets.whereItemSet(inner = false))
    }
}
