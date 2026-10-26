package app.lawnchair.dock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable dock layout. Empty slots are simply absent. Positions are page, column and row;
 * folder contents ignore them and keep list order.
 */
@Serializable
data class DockConfig(
    val format: String = FORMAT,
    val version: Int = 1,
    val columns: Int,
    val rows: Int = 1,
    val pages: Int,
    val slots: List<DockSlot>,
) {
    companion object {
        const val FORMAT = "lawnchair-dock"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            explicitNulls = false
        }

        fun parse(text: String): DockConfig = json.decodeFromString(serializer(), text).also {
            require(it.format == FORMAT) { "Not a Lawnchair dock config" }
            require(it.columns in 3..10 && it.rows in 1..2 && it.pages in 1..5) {
                "Unsupported dock size ${it.columns}x${it.rows}, ${it.pages} pages"
            }
        }
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}

@Serializable
data class DockSlot(
    val type: SlotType,
    val page: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    /** Flattened ComponentName, "package/class" */
    val component: String? = null,
    val title: String? = null,
    /** Base64 PNG, shown greyed out while the app is not installed */
    val icon: String? = null,
    val items: List<DockSlot> = emptyList(),
)

@Serializable
enum class SlotType {
    @SerialName("app")
    APP,

    @SerialName("folder")
    FOLDER,

    @SerialName("drawer")
    DRAWER,
}
