package app.lawnchair.omni.experience

import java.security.MessageDigest

internal data class LayoutCell(
    val id: Int,
    val screenId: Int,
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val movable: Boolean = true,
    val identity: String = "",
)
internal data class LayoutMove(val id: Int, val screenId: Int, val x: Int, val y: Int)

/** Pure planning: all requested moves succeed together or none may be written. */
internal object HomeLayoutPolicy {
    fun folderMembers(cells: List<LayoutCell>, ids: List<Int>): List<LayoutCell> {
        require(ids.size in 2..40 && ids.distinct().size == ids.size) { "invalid_members" }
        val byId = cells.associateBy { it.id }
        return ids.map { id ->
            val item = byId[id] ?: error("item_unavailable")
            check(item.movable && item.spanX == 1 && item.spanY == 1) { "item_unavailable" }
            item
        }
    }

    fun revision(rows: Int, columns: Int, generation: Int, cells: List<LayoutCell>): String {
        val text = "$rows,$columns,$generation;" + cells.sortedBy { it.id }.joinToString(";") { "$it" }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun validate(rows: Int, columns: Int, cells: List<LayoutCell>, moves: List<LayoutMove>, screens: Set<Int>) {
        require(moves.size in 1..40 && moves.map { it.id }.toSet().size == moves.size) { "invalid_moves" }
        val byId = cells.associateBy { it.id }
        val movingIds = moves.map { it.id }.toSet()
        val destinations = moves.map { move ->
            val item = byId[move.id] ?: error("item_unavailable")
            check(item.movable) { "item_unavailable" }
            require(
                move.screenId in screens && move.x >= 0 && move.y >= 0 &&
                    move.x <= columns - item.spanX && move.y <= rows - item.spanY,
            ) { "invalid_position" }
            item.copy(screenId = move.screenId, x = move.x, y = move.y)
        }
        val occupied = cells.filter { it.id !in movingIds }.toMutableList()
        destinations.forEach { target ->
            check(
                occupied.none { other ->
                    target.screenId == other.screenId && target.x < other.x + other.spanX &&
                        target.x + target.spanX > other.x && target.y < other.y + other.spanY &&
                        target.y + target.spanY > other.y
                },
            ) { "cell_occupied" }
            occupied.add(target)
        }
    }
}
