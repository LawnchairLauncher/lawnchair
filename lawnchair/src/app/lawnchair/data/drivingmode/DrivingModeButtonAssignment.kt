package app.lawnchair.data.drivingmode

import androidx.room.Entity

/**
 * One assignable button on the driving mode grid, keyed by its (page, row, col) position.
 * [targetType] is "app" (an installed app/activity, [targetValue] a ComponentKey.toString()) or
 * "special" (a built-in action like Exit/Settings/Navigation, [targetValue] the action's id -
 * see [app.lawnchair.drivingmode.DrivingModeSpecialAction]).
 */
@Entity(tableName = "drivingmodebutton", primaryKeys = ["page", "row", "col"])
data class DrivingModeButtonAssignment(
    val page: Int,
    val row: Int,
    val col: Int,
    val targetType: String,
    val targetValue: String,
)
