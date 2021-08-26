package app.lawnchair.util

fun String.capitalize() = replaceFirstChar { firstChar ->
    firstChar.uppercase()
}