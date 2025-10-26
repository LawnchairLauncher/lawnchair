package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TextPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.search.SearchPopupPreference
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import app.lawnchair.ui.preferences.navigation.FeatureFlags
import com.android.launcher3.R
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.settings.SettingsActivity.DEVELOPER_OPTIONS_KEY
import com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY
import com.android.systemui.shared.system.BlurUtils
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.runBlocking

/**
 * A screen to house unfinished preferences and debug flags
 */
@Composable
fun DebugMenuPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val liveInfoManager = liveInformationManager()
    val flags = remember { prefs.debugFlags }
    val flags2 = remember { prefs2.debugFlags }
    val textFlags = remember { prefs2.textFlags }
    val navController = LocalNavController.current

    val enableDebug = prefs.enableDebugMenu.getAdapter()

    PreferenceLayout(
        label = "Debug menu",
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        MainSwitchPreference(adapter = enableDebug, label = "Show debug menu") {
            PreferenceGroup {
                ClickablePreference(
                    label = "Feature flags (View)",
                    onClick = {
                        try {
                            Intent(context, SettingsActivity::class.java)
                                .putExtra(
                                    EXTRA_FRAGMENT_HIGHLIGHT_KEY,
                                    DEVELOPER_OPTIONS_KEY,
                                )
                                .also { context.startActivity(it) }
                        } catch (e: Exception) {
                            /* This is really unlikely, we are just highlighting the option,
                                not directly opening like Lawnchair 14 and older unless they
                                changed the entire preferences system */
                            Toast.makeText(context, "Failed to open developer settings!", Toast.LENGTH_SHORT)
                                .show()
                            Log.e("DebugMenuPreferences", "Failed to open developer settings!", e)
                        }
                    },
                )
                ClickablePreference(
                    label = "Feature flags (Compose)",
                    onClick = {
                        navController.navigate(FeatureFlags)
                    },
                )
                ClickablePreference(
                    label = "Crash launcher",
                    onClick = { throw RuntimeException("User triggered crash") },
                )
                ClickablePreference(
                    label = "Reset live information",
                    onClick = {
                        runBlocking {
                            liveInfoManager.liveInformation.set(LiveInformation())
                            liveInfoManager.dismissedAnnouncementIds.set(emptySet())
                        }
                    },
                )
                SwitchPreference(
                    label = "Hide version info in About screen",
                    adapter = prefs.hideVersionInfo.getAdapter(),
                )
                SearchPopupPreference(
                    title = "Set custom pseudonym version",
                    initialValue = prefs.pseudonymVersion.get(),
                    placeholder = stringResource(R.string.custom),
                    onConfirm = prefs.pseudonymVersion.getAdapter()::onChange,
                    isErrorCheck = { it.isEmpty() },
                )
            }

            PreferenceGroup(heading = "Debug flags") {
                flags2.forEach {
                    SwitchPreference(
                        adapter = it.getAdapter(),
                        label = it.key.name,
                    )
                }
                flags.forEach {
                    SwitchPreference(
                        adapter = it.getAdapter(),
                        label = it.key,
                    )
                }
                textFlags.forEach {
                    TextPreference(
                        adapter = it.getAdapter(),
                        label = it.key.name,
                    )
                }
            }

            PreferenceGroup(heading = "Launcher3 Feature Diagnostic") {
                var apmSupport = false
                if (LocalContext.current.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    apmSupport = true
                }
                PreferenceCategory(
                    label = "Blur effect",
                    description = BlurUtils.supportsBlursOnWindows().toString(),
                    iconResource = R.drawable.ic_search,
                    onNavigate = { null },
                    isSelected = false,
                )
                PreferenceCategory(
                    label = "App Prediction",
                    description = apmSupport.toString(),
                    iconResource = R.drawable.ic_search,
                    onNavigate = { null },
                    isSelected = false,
                )
            }
        }
    }
}

private val PreferenceManager2.debugFlags: List<Preference<Boolean, Boolean, Preferences.Key<Boolean>>>
    get() = listOf(showComponentNames, legacyPopupOptionsMigrated)

private val PreferenceManager2.textFlags: List<Preference<String, String, Preferences.Key<String>>>
    get() = listOf(additionalFonts, launcherPopupOrder)

private val PreferenceManager.debugFlags
    get() = listOf(ignoreFeedWhitelist)
