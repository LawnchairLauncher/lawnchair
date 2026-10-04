package app.lawnchair.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderMotionTest {
    @Test
    fun explicitFolderChoiceIsIndependentOfHomeMotion() {
        for (motion in FolderMotion.entries) {
            assertEquals(motion, FolderMotion.resolve(motion.name, false))
            assertEquals(motion, FolderMotion.resolve(motion.name, true))
        }
    }

    @Test
    fun unselectedOrUnknownModePreservesLegacyBehavior() {
        for (value in listOf("", "future-mode")) {
            assertEquals(FolderMotion.MORPH, FolderMotion.resolve(value, false))
            assertEquals(FolderMotion.INSTANT, FolderMotion.resolve(value, true))
        }
    }
}
