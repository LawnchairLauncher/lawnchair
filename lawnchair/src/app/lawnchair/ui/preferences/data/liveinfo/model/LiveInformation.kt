package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

@Serializable
data class LiveInformation(
    private val version: Int,
    private val announcements: List<Announcement>,
) {

    val announcementsImmutable: ImmutableList<Announcement>
        get() = announcements.toImmutableList()

    companion object {
        val default = LiveInformation(
            version = 1,
            announcements = emptyList(),
        )
    }
}
