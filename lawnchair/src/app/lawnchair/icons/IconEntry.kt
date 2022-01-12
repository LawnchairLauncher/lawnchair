package app.lawnchair.icons

data class IconEntry(val packPackageName: String, val name: String)

data class CalendarIconEntry(val packPackageName: String, val prefix: String) {
    fun getIconEntry(day: Int) = IconEntry(packPackageName, "$prefix${day + 1}")
}
