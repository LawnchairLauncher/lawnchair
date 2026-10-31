package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.dock.DockConfig
import app.lawnchair.dock.DockConfigManager
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Placeholder and suggestion switches, plus dock export and import. */
@Composable
fun DockLayoutSettings(prefs: PreferenceManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = DockConfigManager(context.applicationContext)

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = manager.export().encode()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
                toast(context.getString(R.string.dock_export_done))
            } catch (e: Exception) {
                toast(context.getString(R.string.dock_config_failed, e.message.orEmpty()))
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val result = manager.import(DockConfig.parse(text))
                val message = buildString {
                    append(context.getString(R.string.dock_import_done, result.placed))
                    if (result.notInstalled.isNotEmpty()) {
                        append('\n')
                        append(context.getString(R.string.dock_import_missing, result.notInstalled.joinToString()))
                    }
                }
                toast(message)
            } catch (e: Exception) {
                toast(context.getString(R.string.dock_config_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    PreferenceGroup(heading = stringResource(id = R.string.dock_layout)) {
        SwitchPreference(
            adapter = prefs.dockPlaceholders.getAdapter(),
            label = stringResource(id = R.string.dock_placeholders),
            description = stringResource(id = R.string.dock_placeholders_description),
        )
        SwitchPreference(
            adapter = prefs.dockSuggestions.getAdapter(),
            label = stringResource(id = R.string.dock_suggestions),
            description = stringResource(id = R.string.dock_suggestions_description),
        )
        ClickablePreference(
            label = stringResource(id = R.string.dock_export),
            subtitle = stringResource(id = R.string.dock_export_description),
            onClick = { exporter.launch("lawnchair-dock.json") },
        )
        ClickablePreference(
            label = stringResource(id = R.string.dock_import),
            subtitle = stringResource(id = R.string.dock_import_description),
            onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }
}
