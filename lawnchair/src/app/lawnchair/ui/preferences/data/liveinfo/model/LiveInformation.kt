package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveInformation(
    private val version: Int = 1,
    val announcements: List<Announcement>,
) {
    companion object {
        val default = LiveInformation(
            version = 1,
            announcements = emptyList(),
        )
    }
}
