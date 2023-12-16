package app.lawnchair.ui.preferences.data.liveinfo

data class LiveInformation(
    val announcements: List<Announcement>,
) {

    data class Announcement(
        val text: String,
        val url: String,
        val isActive: Boolean,
    )
}
