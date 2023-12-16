package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.collections.immutable.ImmutableList

data class LiveInformation(
    val announcements: ImmutableList<Announcement>,
)
