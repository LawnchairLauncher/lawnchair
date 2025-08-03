package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android.launcher3.R
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChangesDialog(
    currentBuild: Int,
    latestBuild: Int,
    repository: NightlyBuildsRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var commits by remember { mutableStateOf<List<GitHubCommit>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                commits = repository.getCommitsSinceCurrentVersion()
                isLoading = false
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.changes_dialog_error)
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.changes_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.changes_dialog_build_format, currentBuild, latestBuild),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(16.dp),
                        )
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage ?: stringResource(R.string.changes_dialog_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    commits != null -> {
                        val commitCount = commits?.size ?: 0
                        Text(
                            text = if (commitCount > 0) {
                                val commitText = if (commitCount > 1) {
                                    stringResource(R.string.changes_dialog_commit_count_plural, commitCount)
                                } else {
                                    stringResource(R.string.changes_dialog_commit_count, commitCount)
                                }
                                "$commitText ${stringResource(R.string.changes_dialog_commit_count_since_version)}"
                            } else {
                                stringResource(R.string.changes_dialog_no_changes)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        commits?.forEach { commit ->
                            CommitItem(commit = commit)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun CommitItem(commit: GitHubCommit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openCommitInBrowser(context, commit.sha)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            val message = commit.commit.message
            val title = message.substringBefore("\n").take(100)
            val description = message.substringAfter("\n", "").take(200)

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            val timeAgo = getTimeAgo(context, commit.commit.author.date)
            Text(
                text = stringResource(R.string.changes_dialog_commit_info, commit.commit.author.name, commit.sha.take(7), timeAgo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun openCommitInBrowser(context: Context, commitSha: String) {
    val commitUrl = "https://github.com/LawnchairLauncher/lawnchair/commit/$commitSha"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(commitUrl))
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
