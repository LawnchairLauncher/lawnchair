package app.lawnchair.theme.colorscheme

/**
 * Represents a Material3 Color Scheme via Int.
 */
data class ColorScheme(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceTint: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val scrim: Int,
    val surfaceBright: Int,
    val surfaceDim: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainerLowest: Int,
) {

    fun get(colorName: String): Int {
        return when (colorName) {
            "primary" -> primary
            "onPrimary" -> onPrimary
            "primaryContainer" -> primaryContainer
            "onPrimaryContainer" -> onPrimaryContainer
            "inversePrimary" -> inversePrimary
            "secondary" -> secondary
            "onSecondary" -> onSecondary
            "secondaryContainer" -> secondaryContainer
            "onSecondaryContainer" -> onSecondaryContainer
            "tertiary" -> tertiary
            "onTertiary" -> onTertiary
            "tertiaryContainer" -> tertiaryContainer
            "onTertiaryContainer" -> onTertiaryContainer
            "background" -> background
            "onBackground" -> onBackground
            "surface" -> surface
            "onSurface" -> onSurface
            "surfaceVariant" -> surfaceVariant
            "onSurfaceVariant" -> onSurfaceVariant
            "surfaceTint" -> surfaceTint
            "inverseSurface" -> inverseSurface
            "inverseOnSurface" -> inverseOnSurface
            "error" -> error
            "onError" -> onError
            "errorContainer" -> errorContainer
            "onErrorContainer" -> onErrorContainer
            "outline" -> outline
            "outlineVariant" -> outlineVariant
            "scrim" -> scrim
            "surfaceBright" -> surfaceBright
            "surfaceDim" -> surfaceDim
            "surfaceContainer" -> surfaceContainer
            "surfaceContainerHigh" -> surfaceContainerHigh
            "surfaceContainerHighest" -> surfaceContainerHighest
            "surfaceContainerLow" -> surfaceContainerLow
            "surfaceContainerLowest" -> surfaceContainerLowest
            else -> throw IllegalArgumentException("Invalid color name: $colorName")
        }
    }
}
