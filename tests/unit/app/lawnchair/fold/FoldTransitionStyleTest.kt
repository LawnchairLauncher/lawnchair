package app.lawnchair.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored keys of the fold animation: removed styles read as a current one. */
class FoldTransitionStyleTest {

    @Test
    fun noneReadsAsSimple() {
        assertEquals(FoldTransitionStyle.SIMPLE, FoldTransitionStyle.forRemovedKey("none"))
    }

    @Test
    fun otherRemovedKeysReadAsTheDefault() {
        for (key in listOf("mass", "deep", "")) {
            assertEquals(FoldTransitionStyle.WINDOW, FoldTransitionStyle.forRemovedKey(key))
        }
    }

    @Test
    fun removedKeysAreNoLongerOptions() {
        val keys = FoldTransitionStyle.entries.map { it.key }
        assertTrue(keys.none { it in listOf("none", "mass", "deep") })
        assertEquals(keys.size, keys.toSet().size)
    }
}
