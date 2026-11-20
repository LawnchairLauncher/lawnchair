package app.lawnchair.fold

import org.junit.Assert.assertEquals
import org.junit.Test

/** How wide the dock of a foldable's large screen is ([FoldDockLayout.getHotseatColumnSpan]). */
class FoldDockLayoutTest {

    // Two pages of 4 columns, a cover dock of 4 icons
    private val columns = 8
    private val cover = 4

    @Test
    fun aMergedDockSpansBothPages() {
        // The default: 6 icons, from the first workspace column to the last
        assertEquals(columns, FoldDockLayout.getHotseatColumnSpan(dockIcons = 6, coverIcons = cover, workspaceColumns = columns))
        assertEquals(columns, FoldDockLayout.getHotseatColumnSpan(dockIcons = 5, coverIcons = cover, workspaceColumns = columns))
        assertEquals(columns, FoldDockLayout.getHotseatColumnSpan(dockIcons = 8, coverIcons = cover, workspaceColumns = columns))
        // More icons than columns: one column each, never squeezed
        assertEquals(10, FoldDockLayout.getHotseatColumnSpan(dockIcons = 10, coverIcons = cover, workspaceColumns = columns))
    }

    @Test
    fun aDockWithTheCoversCountKeepsTheColumnSpacing() {
        // "Same dock", several rows or pages, or as many icons unfolded as folded
        assertEquals(cover, FoldDockLayout.getHotseatColumnSpan(dockIcons = cover, coverIcons = cover, workspaceColumns = columns))
        assertEquals(3, FoldDockLayout.getHotseatColumnSpan(dockIcons = 3, coverIcons = 3, workspaceColumns = columns))
    }
}
