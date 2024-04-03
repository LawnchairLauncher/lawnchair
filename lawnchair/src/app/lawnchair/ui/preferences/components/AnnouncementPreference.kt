package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.asState
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.data.liveinfo.model.Announcement
import app.lawnchair.ui.util.addIf
import com.android.launcher3.BuildConfig
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AnnouncementPreference() {
    val liveInformationManager = liveInformationManager()

    val enabled by liveInformationManager.enabled.asState()
    val showAnnouncements by liveInformationManager.showAnnouncements.asState()
    val liveInformation by liveInformationManager.liveInformation.asState()

    if (enabled && showAnnouncements) {
        AnnouncementPreference(
            announcements = liveInformation.announcementsImmutable,
        )
    }
}

@Composable
fun AnnouncementPreference(
    announcements: ImmutableList<Announcement>,
) {
    Column {
        announcements.forEachIndexed { index, announcement ->
            var show by remember { mutableStateOf(true) }
            AnnouncementItem(show, { show = false }, announcement)
            if (index != announcements.lastIndex && show && (!announcement.test || BuildConfig.DEBUG)) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AnnouncementItem(
    show: Boolean,
    onClose: () -> Unit,
    announcement: Announcement,
) {
    ExpandAndShrink(
        visible = show && announcement.active &&
            announcement.text.isNotBlank() &&
            (!announcement.test || BuildConfig.DEBUG),
    ) {
        AnnouncementItemContent(
            text = announcement.text,
            url = announcement.url,
            onClose = onClose,
        )
    }
}

@Composable
private fun AnnouncementItemContent(
    text: String,
    url: String?,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(16.dp, 0.dp, 16.dp, 0.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        AnnouncementPreferenceItemContent(text = text, url = url, onClose = onClose)
    }
}

@Composable
private fun AnnouncementPreferenceItemContent(
    text: String,
    url: String?,
    onClose: (() -> Unit)?,
) {
    val context = LocalContext.current
    val hasLink = !url.isNullOrBlank()

    PreferenceTemplate(
        modifier = Modifier
            .fillMaxWidth()
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (hasLink) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Launch,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(16.dp).offset(x = (8).dp, y = (-16).dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            tint = MaterialTheme.colorScheme.surfaceTint,
                            contentDescription = null,
                        )
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun InfoPreferenceWithoutLinkPreview() {
    AnnouncementPreferenceItemContent(
        text = "Very important announcement ",
        url = "",
        onClose = null,
    )
}

@Preview
@Composable
private fun InfoPreferenceWithLinkPreview() {
    AnnouncementPreferenceItemContent(
        text = "Very important announcement with a very important link",
        url = "https://lawnchair.app/",
        onClose = null,
    )
}
