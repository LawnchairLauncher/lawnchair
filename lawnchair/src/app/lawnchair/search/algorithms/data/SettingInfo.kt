package app.lawnchair.search.algorithms.data

data class SettingInfo(
    val id: String,
    val name: String,
    val action: String,
    val requiresUri: Boolean = false,
)
