package app.lawnchair.folder

import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.util.kotlinxJson
import kotlinx.serialization.Serializable

/** LC: an icon picked from an icon pack for a home screen folder. */
@Serializable
data class FolderIconOverride(
    val packPackageName: String,
    val drawableName: String,
    val label: String,
    val type: String = IconType.Normal.name,
) {
    fun toIconPickerItem() = IconPickerItem(
        packPackageName = packPackageName,
        drawableName = drawableName,
        label = label,
        type = IconType.entries.firstOrNull { it.name == type } ?: IconType.Normal,
    )

    companion object {
        fun from(item: IconPickerItem) = FolderIconOverride(
            packPackageName = item.packPackageName,
            drawableName = item.drawableName,
            label = item.label,
            type = item.type.name,
        )

        fun decode(json: String): FolderIconOverride? = runCatching { kotlinxJson.decodeFromString<FolderIconOverride>(json) }.getOrNull()
    }
}
