package app.lawnchair.theme

@JvmInline
value class UiColorMode(val mode: Int) {

    val isDarkTheme get() = (mode and FLAG_DARK) != 0
    val isDarkText get() = (mode and FLAG_DARK_TEXT) != 0
    val isDarkPrimaryColor get() = (mode and FLAG_DARK_PRIMARY_COLOR) != 0

    companion object {
        const val FLAG_DARK = 1 shl 0
        const val FLAG_DARK_TEXT = 1 shl 1
        const val FLAG_DARK_PRIMARY_COLOR = 1 shl 2

        val Light = UiColorMode(0)
        val Light_DarkText = UiColorMode(FLAG_DARK_TEXT)
        val Light_DarkPrimaryColor = UiColorMode(FLAG_DARK_PRIMARY_COLOR)

        val Dark = UiColorMode(FLAG_DARK)
        val Dark_DarkText = UiColorMode(FLAG_DARK and FLAG_DARK_TEXT)
        val Dark_DarkPrimaryColor = UiColorMode(FLAG_DARK and FLAG_DARK_PRIMARY_COLOR)
    }
}
