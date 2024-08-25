package app.lawnchair.ui.preferences.data.liveinfo.model

import com.android.launcher3.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Announcement(
    val text: String,
    val url: String? = null,
    val active: Boolean = true,
    val test: Boolean = false,
    @SerialName("flavor-channel") val flavorChannel: String? = null,
) {

    val shouldBeVisible
        get(): Boolean {
            if (active.not()) return false
            if (text.isBlank()) return false
            if (test && BuildConfig.DEBUG.not()) return false
            if (flavorChannel != null && flavorChannel != BuildConfig.FLAVOR_channel) return false
            return true
        }
}
