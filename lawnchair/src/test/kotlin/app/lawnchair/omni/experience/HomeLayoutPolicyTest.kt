package app.lawnchair.omni.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeLayoutPolicyTest {
    private val cells = listOf(LayoutCell(1, 0, 0, 0), LayoutCell(2, 0, 1, 0), LayoutCell(3, 0, 0, 1, 2, 2, false))

    @Test fun swapsAreValidatedAsOneBatch() {
        HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(1, 0, 1, 0), LayoutMove(2, 0, 0, 0)), setOf(0))
    }

    @Test fun stationaryIconsAndWidgetSpansRemainOccupied() {
        assertThrows(IllegalStateException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(1, 0, 1, 0)), setOf(0)) }
        assertThrows(IllegalStateException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(1, 0, 1, 2)), setOf(0)) }
    }

    @Test fun hiddenItemsAndWidgetsCannotBeMoved() {
        assertThrows(IllegalStateException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(3, 0, 3, 3)), setOf(0)) }
    }

    @Test fun duplicateIdsAndDestinationsFailBeforeWrites() {
        assertThrows(IllegalArgumentException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(1, 0, 3, 3), LayoutMove(1, 0, 2, 3)), setOf(0)) }
        assertThrows(IllegalStateException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(1, 0, 3, 3), LayoutMove(2, 0, 3, 3)), setOf(0)) }
    }

    @Test fun negativeOutOfGridAndUnknownScreenAreRejected() {
        listOf(LayoutMove(1, 0, -1, 0), LayoutMove(1, 0, 4, 0), LayoutMove(1, 0, 0, 5), LayoutMove(1, 99, 3, 3)).forEach { move ->
            assertThrows(IllegalArgumentException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(move), setOf(0)) }
        }
    }

    @Test fun revisionChangesForPositionsIdentityGridAndReloadButNotListOrder() {
        val original = HomeLayoutPolicy.revision(5, 4, 1, cells)
        assertEquals(original, HomeLayoutPolicy.revision(5, 4, 1, cells.reversed()))
        assertNotEquals(original, HomeLayoutPolicy.revision(6, 4, 1, cells))
        assertNotEquals(original, HomeLayoutPolicy.revision(5, 4, 2, cells))
        assertNotEquals(original, HomeLayoutPolicy.revision(5, 4, 1, cells.map { if (it.id == 1) it.copy(x = 3) else it }))
        assertNotEquals(original, HomeLayoutPolicy.revision(5, 4, 1, cells.map { if (it.id == 1) it.copy(identity = "other-profile") else it }))
    }

    @Test fun payloadAndMissingItemLimitsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, emptyList(), setOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, (1..41).map { LayoutMove(it, 0, 0, 0) }, setOf(0)) }
        assertThrows(IllegalStateException::class.java) { HomeLayoutPolicy.validate(5, 4, cells, listOf(LayoutMove(99, 0, 3, 3)), setOf(0)) }
    }
}
