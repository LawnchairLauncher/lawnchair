package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveInformation(
    val announcements: List<Announcement>,
) {

    companion object {
        val default = LiveInformation(
            announcements = emptyList(),
        )
    }
}
