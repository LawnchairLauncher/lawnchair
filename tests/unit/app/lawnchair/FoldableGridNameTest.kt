package app.lawnchair

import app.lawnchair.DeviceProfileOverrides.DBGridInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** The grid picked for a grid size ([pickGridName]), with the grids of device_profiles.xml. */
class FoldableGridNameTest {

    private fun grid(rows: Int, columns: Int, name: String) = DBGridInfo(numHotseatColumns = 4, numRows = rows, numColumns = columns) to name

    private fun size(rows: Int, columns: Int) = DBGridInfo(numHotseatColumns = 4, numRows = rows, numColumns = columns)

    // In XML order: the regular 4_by_6 comes before the responsive practical, both 6 rows x 4 columns
    private val foldable = listOf(grid(6, 4, "4_by_6"), grid(6, 4, "practical"))
    private val phone = listOf(grid(3, 3, "3_by_3"), grid(6, 4, "4_by_6"))

    @Test
    fun foldablesGetTheRegularGrid() {
        for (rows in 2..6) {
            for (columns in 2..4) {
                assertEquals("$rows x $columns", "4_by_6", pickGridName(foldable, size(rows, columns)))
            }
        }
    }

    @Test
    fun largerSizesGetTheFirstOfTheLargestGrids() {
        // More rows or columns than any grid has: the largest size, and of those the first grid
        assertEquals("4_by_6", pickGridName(foldable, size(7, 4)))
        assertEquals("4_by_6", pickGridName(foldable, size(6, 5)))
        assertEquals("4_by_6", pickGridName(foldable, size(9, 7)))
        assertEquals("4_by_6", pickGridName(phone, size(8, 6)))
    }

    @Test
    fun phonesAreUnchanged() {
        assertEquals("3_by_3", pickGridName(phone, size(3, 3)))
        assertEquals("4_by_6", pickGridName(phone, size(4, 3)))
        assertEquals("4_by_6", pickGridName(phone, size(6, 4)))
        assertEquals("6_by_5", pickGridName(listOf(grid(5, 6, "6_by_5")), size(7, 7)))
    }
}
