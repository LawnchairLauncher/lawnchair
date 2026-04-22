package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveInformation(
    private val version: Int = 2,
    val announcements: List<Announcement> = emptyList(),
    val features: Map<String, String?> = emptyMap(),
)
