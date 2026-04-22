package app.lawnchair.ui.preferences.data.liveinfo.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.launcher3.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class Announcement(
    val text: String,
    val url: String? = null,
    private val active: Boolean = true,
    private val test: Boolean = false,
    private val channel: String? = null,
    private val icon: String? = null,
) {

    val id: AnnouncementId get() = text to url

    val iconVector: ImageVector
        get() = when (icon) {
            "bug-report" -> Icons.Rounded.BugReport
            "check-circle" -> Icons.Rounded.CheckCircle
            "error" -> Icons.Rounded.Error
            "favorite" -> Icons.Rounded.Favorite
            "feedback" -> Icons.Rounded.Feedback
            "forum" -> Icons.Rounded.Forum
            "hub" -> Icons.Rounded.Hub
            "loyalty" -> Icons.Rounded.Loyalty
            "priority-high" -> Icons.Rounded.PriorityHigh
            "privacy-tip" -> Icons.Rounded.PrivacyTip
            "sos" -> Icons.Rounded.Sos
            "star" -> Icons.Rounded.Star
            "support" -> Icons.Rounded.Support
            "warning" -> Icons.Rounded.Warning
            else -> Icons.Rounded.NewReleases
        }

    val shouldBeVisible
        get(): Boolean {
            if (active.not()) return false
            if (text.isBlank()) return false
            if (test && BuildConfig.DEBUG.not()) return false
            if (channel != null && channel != BuildConfig.FLAVOR_channel) return false
            return true
        }
}
