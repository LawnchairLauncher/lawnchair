package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.edit
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.CreateBackup
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN
import com.android.launcher3.util.OnboardingPrefs.HOTSEAT_DISCOVERY_TIP_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP

@Composable
fun FeatureFlagsPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    PreferenceLayoutLazyColumn(
        modifier = modifier,
        label = "Feature Flags",
    ) {
        preferenceCategory("Server flags", "Long press to reset")
        item(key = "server flag info") {
            PreferenceTemplate(
                title = {
                    Text("To change server flags, update the .aconfig files found at the aconfig/ directory, then rebuild Launcher3.")
                },
            )
        }

        preferenceCategory("Gesture Navigation Sandbox", null)
        items(
            gestureSandboxActions,
            key = { it.title },
        ) {
            IntentPreference(
                label = it.title,
                intent = it.intent,
            )
        }

        preferenceCategory(
            "Onboarding Flows",
            "Reset these if you want to see the education again.",
        )
        items(
            onboardingPrefs,
            key = { it.title },
        ) { item ->
            OnboardingPreference(
                title = item.title,
                onEdit = {
                    LauncherPrefs.getPrefs(context)
                        .edit {
                            item.keys.forEach { key -> remove(key) }
                        }
                    Toast.makeText(context, "Reset $item.title", Toast.LENGTH_SHORT).show()
                },
            )
        }

        preferenceCategory(
            "Workspace grid layout",
            "To share your current workspace, use Lawnchair's backup and restore system.",
        )
        item(key = "open_backup_system") {
            val navController = LocalNavController.current
            ClickablePreference(
                "Export",
            ) {
                navController.navigate(CreateBackup)
            }
        }
        item(key = "workspace_import") {
            val opener = restoreBackupOpener()
            ClickablePreference(
                "Import",
                onClick = opener,
            )
        }
    }
}

// onboarding preferences
private data class OnboardingPref(
    val title: String,
    val keys: List<String>,
)

private val onboardingPrefs = listOf(
    OnboardingPref(
        "All Apps Bounce",
        listOf(HOME_BOUNCE_SEEN.sharedPrefKey, HOME_BOUNCE_COUNT.sharedPrefKey),
    ),
    OnboardingPref(
        "Hybrid Hotseat Education",
        listOf(HOTSEAT_DISCOVERY_TIP_COUNT.sharedPrefKey, HOTSEAT_LONGPRESS_TIP_SEEN.sharedPrefKey),
    ),
    OnboardingPref("Taskbar Education", listOf(TASKBAR_EDU_TOOLTIP_STEP.sharedPrefKey)),
    OnboardingPref("All Apps Visited Count", listOf(ALL_APPS_VISITED_COUNT.sharedPrefKey)),
)

@Composable
fun OnboardingPreference(
    title: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickablePreference(
        label = title,
        subtitle = "Tap to reset",
        modifier = modifier,
    ) {
        onEdit()
    }
}

// gesture sandbox
private data class GestureSandboxDebugAction(
    val title: String,
    val intent: Intent,
)

private val launchSandboxIntent =
    Intent("com.android.quickstep.action.GESTURE_SANDBOX")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private val gestureSandboxActions: List<GestureSandboxDebugAction> =
    listOf(
        GestureSandboxDebugAction(
            title = "Launch Gesture Tutorial Steps menu",
            intent = Intent(launchSandboxIntent).putExtra("use_tutorial_menu", true),
        ),
        GestureSandboxDebugAction(
            title = "Launch Back Tutorial",
            intent = Intent(launchSandboxIntent).putExtra("use_tutorial_menu", false)
                .putExtra("tutorial_steps", arrayOf("BACK_NAVIGATION")),
        ),
        GestureSandboxDebugAction(
            title = "Launch Home Tutorial",
            intent = Intent(launchSandboxIntent).putExtra("use_tutorial_menu", false)
                .putExtra("tutorial_steps", arrayOf("HOME_NAVIGATION")),
        ),
        GestureSandboxDebugAction(
            title = "Launch Overview Tutorial",
            intent = Intent(launchSandboxIntent).putExtra("use_tutorial_menu", false)
                .putExtra("tutorial_steps", arrayOf("OVERVIEW_NAVIGATION")),
        ),
    )

@Composable
private fun IntentPreference(
    label: String,
    intent: Intent,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ClickablePreference(
        label = label,
        modifier = modifier,
    ) {
        context.startActivity(intent)
    }
}

private fun LazyListScope.preferenceCategory(heading: String, description: String? = null) {
    item(key = heading) {
        PreferenceTemplate(
            title = {
                Text(
                    heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { this.heading() },
                )
            },
            description = {
                description?.let { Text(description) }
            },
        )
    }
}
