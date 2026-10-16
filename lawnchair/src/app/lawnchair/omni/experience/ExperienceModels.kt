package app.lawnchair.omni.experience

import kotlinx.serialization.Serializable

@Serializable
data class ShelfCard(
    val id: String,
    val title: String,
    val text: String = "",
    val uri: String? = null,
    val mimeType: String? = null,
    val source: String = "local",
)

@Serializable
data class FolderRule(
    val folderId: Int,
    val nameContains: String = "",
    val packagePrefix: String = "",
    val category: Int? = null,
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    fun matches(key: String, name: String, pkg: String, appCategory: Int): Boolean {
        if (key in exclude) return false
        if (key in include) return true
        val hasRule = nameContains.isNotBlank() || packagePrefix.isNotBlank() || category != null
        return hasRule &&
            (
                nameContains.isBlank() || app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer.normalize(name)
                    .contains(app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer.normalize(nameContains))
                ) &&
            (packagePrefix.isBlank() || pkg.startsWith(packagePrefix)) &&
            (category == null || category == appCategory)
    }
}

@Serializable
data class HomeScene(
    val id: String,
    val name: String,
    val booleans: Map<String, Boolean> = emptyMap(),
    val strings: Map<String, String> = emptyMap(),
    val apps: List<String> = emptyList(),
    val automatic: Boolean = false,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val charging: Boolean? = null,
    val headset: Boolean? = null,
) {
    fun isActive(minute: Int, isCharging: Boolean, hasHeadset: Boolean): Boolean {
        val inTime = when {
            startMinute == endMinute -> true
            startMinute < endMinute -> minute in startMinute until endMinute
            else -> minute >= startMinute || minute < endMinute
        }
        return automatic && inTime && (charging == null || charging == isCharging) &&
            (headset == null || headset == hasHeadset)
    }
}

@Serializable
data class WidgetStack(val outerId: Int, val widgetIds: List<Int> = emptyList(), val selected: Int = 0)

@Serializable
data class ExperienceState(
    val aliases: Map<String, String> = emptyMap(),
    val cards: List<ShelfCard> = emptyList(),
    val rules: List<FolderRule> = emptyList(),
    val scenes: List<HomeScene> = emptyList(),
    val activeScene: String? = null,
    val undoScene: HomeScene? = null,
    val automaticScenes: Boolean = false,
    val stacks: List<WidgetStack> = emptyList(),
    val preferredWorkspace: String? = null,
)

internal fun parseSceneTime(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
