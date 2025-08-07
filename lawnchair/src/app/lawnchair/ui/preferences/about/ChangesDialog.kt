package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupItem
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R
import java.time.Instant
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesDialog(
    changelogState: ChangelogState?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val commits = changelogState?.commits
    val currentBuild = changelogState?.currentBuildNumber ?: 0
    val latestBuild = changelogState?.latestBuildNumber ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(
                text = stringResource(R.string.changes_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.changes_dialog_build_format,
                    currentBuild,
                    latestBuild,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 600.dp)
                .fillMaxWidth(),
        ) {
            if (commits != null) {
                itemsIndexed(commits) { index, commit ->
                    PreferenceGroupItem(
                        cutTop = index != 0,
                        cutBottom = index != commits.lastIndex,
                    ) {
                        CommitItem(commit = commit)
                    }
                    Spacer(Modifier.height(3.dp))
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.changes_dialog_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onDismiss,
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onDownload()
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.download_update))
            }
        }
    }
}

@Composable
private fun CommitItem(
    commit: GitHubCommit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val message = commit.commit.message
    val title = message.substringBefore("\n").take(100)
    val description = message.substringAfter("\n", "").take(200)

    PreferenceTemplate(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        description = {
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            val timeAgo = getTimeAgo(context, commit.commit.author.date)
            Text(
                text = stringResource(
                    R.string.changes_dialog_commit_info,
                    commit.commit.author.name,
                    commit.sha.take(7),
                    timeAgo,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        modifier = modifier.clickable {
            openCommitInBrowser(context, commit.sha)
        },
    )
}

private fun openCommitInBrowser(context: Context, commitSha: String) {
    val commitUrl = "https://github.com/LawnchairLauncher/lawnchair/commit/$commitSha"
    val intent = Intent(Intent.ACTION_VIEW, commitUrl.toUri())
    context.startActivity(intent)
}

private fun getTimeAgo(context: Context, dateString: String): String {
    return try {
        val commitDate = Instant.parse(dateString)
        val currentTime = Instant.now()
        val diffMillis = currentTime.toEpochMilli() - commitDate.toEpochMilli()

        when {
            diffMillis < TimeUnit.MINUTES.toMillis(1) -> {
                context.getString(R.string.time_just_now)
            }

            diffMillis < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
                context.getString(R.string.time_minutes_ago, minutes)
            }

            diffMillis < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
                context.getString(R.string.time_hours_ago, hours)
            }

            diffMillis < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
                context.getString(R.string.time_days_ago, days)
            }

            diffMillis < TimeUnit.DAYS.toMillis(30) -> {
                val weeks = TimeUnit.MILLISECONDS.toDays(diffMillis) / 7
                context.getString(R.string.time_weeks_ago, weeks)
            }

            diffMillis < TimeUnit.DAYS.toMillis(365) -> {
                val months = TimeUnit.MILLISECONDS.toDays(diffMillis) / 30
                context.getString(R.string.time_months_ago, months)
            }

            else -> {
                val years = TimeUnit.MILLISECONDS.toDays(diffMillis) / 365
                context.getString(R.string.time_years_ago, years)
            }
        }
    } catch (e: Exception) {
        // If date parsing fails, fallback to date prefix
        dateString.substringBefore("T")
    }
}
