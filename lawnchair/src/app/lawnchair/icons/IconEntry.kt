package app.lawnchair.icons

data class IconEntry(
    val packPackageName: String,
    val name: String,
    val type: IconType
) {
    fun resolveDynamicCalendar(day: Int): IconEntry {
        if (type != IconType.Calendar) throw IllegalStateException("type is not calendar")
        return IconEntry(packPackageName, "$name${day + 1}", IconType.Normal)
    }
}

enum class IconType {
    Normal, Calendar
}
