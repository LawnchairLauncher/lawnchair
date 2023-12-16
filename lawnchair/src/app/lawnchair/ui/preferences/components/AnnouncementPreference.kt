package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.asState
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.data.liveinfo.model.Announcement
import app.lawnchair.ui.util.addIf
import com.android.launcher3.BuildConfig

@Composable
fun AnnouncementPreference() {
    val liveInformationManager = liveInformationManager()
    val announcements = liveInformationManager.announcements.asState()

    announcements.value.forEach { announcement ->
        ExpandAndShrink(
            visible = announcement.active
                && announcement.text.isNotBlank()
                && (!announcement.test || BuildConfig.DEBUG),
        ) {
            Column {
                AnnouncementPreference(announcement)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AnnouncementPreference(
    announcement: Announcement,
    modifier: Modifier = Modifier,
) {
    AnnouncementPreference(
        text = announcement.text,
        url = announcement.url,
        modifier = modifier,
    )
}

@Composable
private fun AnnouncementPreference(
    text: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLink = url.isNotBlank()

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = androidx.compose.material.MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = modifier
                .addIf(hasLink) {
                    clickable {
                        val webpage = Uri.parse(url)
                        val intent = Intent(Intent.ACTION_VIEW, webpage)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                },
            title = {},
            description = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
            },
            endWidget = {
                if (hasLink) {
                    Icon(
                        imageVector = Icons.Rounded.Launch,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}

@Preview
@Composable
private fun InfoPreferenceWithoutLinkPreview() {
    AnnouncementPreference(
        text = "Very important announcement ",
        url = "",
    )
}

@Preview
@Composable
private fun InfoPreferenceWithLinkPreview() {
    AnnouncementPreference(
        text = "Very important announcement with a very important link",
        url = "https://lawnchair.app/",
    )
}
