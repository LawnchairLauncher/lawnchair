package app.lawnchair.font

object FontAxes {
    const val WEIGHT = "wght"
    const val WIDTH = "wdth"
    const val OPTICAL_SIZE = "opsz"
    const val GRADE = "GRAD"
    const val ROUNDNESS = "ROND"

    fun mapToString(axes: Map<String, Float>): String {
        return axes.entries.joinToString(", ") { "'${it.key}' ${it.value}" }
    }
}
