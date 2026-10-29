package app.lawnchair.ui.preferences.components.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pace of the fold previews ([previewTimeAt]): the unfold's motion plays twice as slow. */
class FoldPreviewPaceTest {

    // 5.28 s loops: folded 0.76 s, the motion 2.56 s (1.28 s in the painter's 4 s loop), open
    // 1.12 s, folded again 0.84 s
    private val loop = 5.28f

    @Test
    fun aLoopRunsThroughThePainterLoop() {
        assertEquals(0f, previewTimeAt(0f), 0f)
        assertEquals(1f, previewTimeAt(1f), 1e-5f)
        var previous = 0f
        for (i in 1..1000) {
            val time = previewTimeAt(i / 1000f)
            assertTrue("loop time $time at step $i", time >= previous && time - previous < 0.002f)
            previous = time
        }
    }

    @Test
    fun theMotionPlaysTwiceAsSlowTheStillFramesKeepTheirTime() {
        // Folded until 0.76 s, as before
        assertEquals(0.19f, previewTimeAt(0.76f / loop), 1e-5f)
        // The motion, 0.19 to 0.51 of the painter's loop, over 2.56 s instead of 1.28 s
        assertEquals(0.35f, previewTimeAt((0.76f + 1.28f) / loop), 1e-5f)
        assertEquals(0.51f, previewTimeAt((0.76f + 2.56f) / loop), 1e-5f)
        // Open for 1.12 s, then folded for 0.84 s, as before
        assertEquals(0.79f, previewTimeAt((0.76f + 2.56f + 1.12f) / loop), 1e-5f)
    }

    @Test
    fun outOfRangeFractionsStayInTheLoop() {
        assertEquals(0f, previewTimeAt(-0.5f), 0f)
        assertEquals(1f, previewTimeAt(1.5f), 1e-5f)
    }
}
