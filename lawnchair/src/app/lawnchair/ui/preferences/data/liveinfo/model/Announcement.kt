package app.lawnchair.ui.preferences.data.liveinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class Announcement(
    val text: String,
    val url: String? = null,
    val active: Boolean = true,
    val test: Boolean = false,
)
