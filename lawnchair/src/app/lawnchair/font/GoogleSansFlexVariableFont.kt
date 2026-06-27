package app.lawnchair.font

/**
 * Resolves the AOSP variable-* names used in the launcher to the matching GSF variation axes.
 *
 * @see [app.lawnchair.ui.theme.GoogleSansFlex]
 */
object GoogleSansFlexVariableFont {

    private const val PREFIX = "variable-"
    private const val EMPHASIZED_SUFFIX = "-emphasized"

    private val opticalSizes = mapOf(
        "display-large" to 57f, "display-medium" to 45f, "display-small" to 36f,
        "headline-large" to 32f, "headline-medium" to 28f, "headline-small" to 24f,
        "title-large" to 22f, "title-medium" to 16f, "title-small" to 14f,
        "body-large" to 16f, "body-medium" to 14f, "body-small" to 12f,
        "label-large" to 14f, "label-medium" to 12f, "label-small" to 11f,
    )

    private fun weightFor(category: String, emphasized: Boolean): Int = when (category) {
        "label" -> if (emphasized) 600 else 500
        "title" -> 500
        else -> if (emphasized) 500 else 400 // display, headline, body
    }

    fun isVariableFamily(name: String?): Boolean = name != null && name.startsWith(PREFIX)

    /**
     * Returns the GSF variation axes for AOSP `variable-*` family [name], or null
     * when [name] is not a recognised role.
     */
    fun axesFor(name: String?): Map<String, Float>? {
        if (!isVariableFamily(name)) return null
        var role = name!!.removePrefix(PREFIX)
        val emphasized = role.endsWith(EMPHASIZED_SUFFIX)
        if (emphasized) role = role.removeSuffix(EMPHASIZED_SUFFIX)
        val opticalSize = opticalSizes[role] ?: return null
        val category = role.substringBefore('-')
        return mapOf(
            FontAxes.WEIGHT to weightFor(category, emphasized).toFloat(),
            FontAxes.WIDTH to 100f,
            FontAxes.GRADE to 0f,
            FontAxes.ROUNDNESS to 100f,
            FontAxes.OPTICAL_SIZE to opticalSize,
        )
    }
}
