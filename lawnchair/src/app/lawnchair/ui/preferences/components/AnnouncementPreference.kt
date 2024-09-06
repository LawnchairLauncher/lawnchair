package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.android.launcher3.R

@Composable
fun AnnouncementPreference() {
    val liveInformationManager = liveInformationManager()

    val enabled by liveInformationManager.enabled.asState()
    val showAnnouncements by liveInformationManager.showAnnouncements.asState()
    val liveInformation by liveInformationManager.liveInformation.asState()

    if (enabled && showAnnouncements) {
        AnnouncementPreference(
            announcements = liveInformation.announcements,
        )
    }
}

@Composable
fun AnnouncementPreference(
    announcements: List<Announcement>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        announcements.forEachIndexed { index, announcement ->
            var show by rememberSaveable { mutableStateOf(true) }
            AnnouncementItem(show, announcement) { show = false }
            if (index != announcements.lastIndex && show && announcement.active && (!announcement.test || BuildConfig.DEBUG)) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AnnouncementItem(
    show: Boolean,
    announcement: Announcement,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    ExpandAndShrink(
        modifier = modifier,
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
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onClose()
                }
                SwipeToDismissBoxValue.EndToStart -> return@rememberSwipeToDismissBoxState false
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            return@rememberSwipeToDismissBoxState true
        },
    )

    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Surface(
                modifier = modifier
                    .alpha(
                        if (state.dismissDirection != SwipeToDismissBoxValue.StartToEnd) 1f else calculateAlpha(state.progress),
                    )
                    .fillMaxSize()
                    .padding(16.dp, 0.dp, 16.dp, 0.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            ) {
                PreferenceTemplate(
                    {},
                    description = {
                        Text(stringResource(R.string.accessibility_close))
                    },
                )
            }
        },
    ) {
        Surface(
            modifier = modifier
                .alpha(
                    if (state.dismissDirection != SwipeToDismissBoxValue.StartToEnd) 1f else calculateAlpha(state.progress),
                )
                .padding(16.dp, 0.dp, 16.dp, 0.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            AnnouncementPreferenceItemContent(text = text, url = url)
        }
    }
}

private fun calculateAlpha(progress: Float): Float {
    return when {
        progress < 0.5f -> 1f // Fully opaque until halfway
        else -> 1f - (progress - 0.5f) * 2 // Fade out linearly from halfway to the end
    }
}

@Composable
private fun AnnouncementPreferenceItemContent(
    text: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLink = !url.isNullOrBlank()

    PreferenceTemplate(
        modifier = modifier
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
    )
}

@Preview
@Composable
private fun InfoPreferenceWithLinkPreview() {
    AnnouncementPreferenceItemContent(
        text = "Very important announcement with a very important link",
        url = "https://lawnchair.app/",
    )
}
