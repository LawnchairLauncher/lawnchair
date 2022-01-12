package app.lawnchair.icons

data class IconPickerCategory(
    val title: String,
    val items: List<IconPickerItem>
)

fun IconPickerCategory.filter(searchQuery: String): IconPickerCategory {
    return IconPickerCategory(
        title = title,
        items = items
            .filter { it.label.lowercase().contains(searchQuery.lowercase()) }
    )
}
