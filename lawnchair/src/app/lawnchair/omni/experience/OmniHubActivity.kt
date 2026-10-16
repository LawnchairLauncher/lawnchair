@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.lawnchair.omni.experience

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.omni.OmniAskActivity
import app.lawnchair.omni.OmniIntegrationPreferences
import app.lawnchair.omni.experience.widgets.OmniWidgetStack
import app.lawnchair.omni.experience.widgets.WidgetStackActivity
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.destinations.SmartspaceDateAndTimePreferences
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias Work = (suspend () -> Unit) -> Unit

class OmniHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shares are drafts. Nothing is persisted or forwarded before the user presses Save.
        val draft = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(4000) else null
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                ProvideBottomSheetHandler {
                    Hub(intent.getStringExtra("page") ?: if (draft != null) "shelf" else "home", draft, intent.getBooleanExtra("new_note", false))
                }
            }
        }
    }
    companion object {
        fun intent(context: Context, page: String = "home") = Intent(context, OmniHubActivity::class.java).putExtra("page", page)
    }
}

@Composable
private fun Hub(initialPage: String, draft: String?, newNote: Boolean) {
    val context = LocalContext.current
    val store = remember { ExperienceStore.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    val work: Work = { operation ->
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { operation() }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    snackbar.showSnackbar(context.getString(R.string.omni_action_unavailable))
                } finally {
                    busy = false
                }
            }
        }
    }
    val pages = mapOf("shelf" to R.string.omni_shelf, "scenes" to R.string.omni_scenes, "folders" to R.string.omni_rule_folders, "aliases" to R.string.omni_aliases, "widgets" to R.string.omni_widget_stacks, "system" to R.string.omni_system, "date" to R.string.smartspace_date_and_time)
    var page by rememberSaveable { mutableStateOf(initialPage.takeIf { it in pages } ?: "home") }
    BackHandler(page != "home") { page = "home" }
    Box(Modifier.fillMaxSize()) {
        PreferenceScaffold(label = stringResource(pages[page] ?: R.string.omni_hub), isExpandedScreen = false) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                val current = state
                if (current == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    key(page) {
                        when (page) {
                            "scenes" -> ScenesScreen(current, store, work)
                            "folders" -> RulesScreen(current, store)
                            "aliases" -> AliasesScreen(current, store)
                            "widgets" -> StacksScreen(current)
                            "system" -> SystemScreen(current, work)
                            "date" -> CalendarScreen()
                            "shelf" -> ShelfScreen(current, store, work, draft, newNote)
                            else -> HubOverview(current) { page = it }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun HubOverview(state: ExperienceState, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PreferenceGroup(heading = stringResource(R.string.omni_personalize), description = stringResource(R.string.omni_hub_intro)) {
                HubEntry(R.string.smartspace_date_and_time, R.string.omni_date_summary, Icons.Rounded.CalendarMonth) { open("date") }
                HubEntry(R.string.omni_scenes, R.string.omni_scenes_help, Icons.Rounded.Palette) { open("scenes") }
                HubEntry(R.string.omni_widget_stacks, R.string.omni_stack_help, Icons.Rounded.Widgets) { open("widgets") }
                HubEntry(R.string.omni_rule_folders, R.string.omni_rules_help, Icons.Rounded.Folder) { open("folders") }
            }
        }
        item {
            PreferenceGroup(heading = stringResource(R.string.omni_tools)) {
                HubEntry(R.string.omni_shelf, R.string.omni_shelf_help, Icons.Rounded.Dashboard) { open("shelf") }
                HubEntry(R.string.omni_aliases, R.string.omni_alias_help, Icons.Rounded.Search) { open("aliases") }
                HubEntry(R.string.omni_system, R.string.omni_oem_help, Icons.Rounded.Link) { open("system") }
            }
        }
        item {
            Text(stringResource(R.string.omni_hub_counts, state.cards.size, state.scenes.size, state.rules.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
    }
}

@Composable
private fun HubEntry(title: Int, subtitle: Int, icon: ImageVector, onClick: () -> Unit) {
    PreferenceTemplate(
        title = { Text(stringResource(title)) },
        description = { Text(stringResource(subtitle)) },
        startWidget = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        endWidget = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CalendarScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        PreferenceGroup(heading = stringResource(R.string.preview_label), description = stringResource(R.string.omni_date_preview_help)) {
            val color = MaterialTheme.colorScheme.onSurface.toArgb()
            AndroidView(
                factory = { app.lawnchair.smartspace.IcuDateTextView(it).apply { textSize = 20f } },
                update = { it.setTextColor(color) },
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        SmartspaceDateAndTimePreferences()
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    PreferenceGroup(heading = title, showDividers = false) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ShelfScreen(state: ExperienceState, store: ExperienceStore, work: Work, draft: String?, newNote: Boolean) {
    val context = LocalContext.current
    var editNote by rememberSaveable { mutableStateOf(draft != null || newNote) }
    var noteId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftConsumed by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ShelfCard?>(null) }
    val inventory = remember { OmniAppInventory.get(context) }
    val revision by inventory.revision.collectAsStateWithLifecycle()
    val apps by produceState<List<AppInfo>>(emptyList(), revision) { value = inventory.visible() }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            work {
                val existing = store.read().cards.any { it.uri == uri.toString() }
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: context.getString(R.string.omni_document)
                    store.putCard(ShelfCard(ExperienceStore.newId(), title.take(160), uri = uri.toString(), mimeType = context.contentResolver.getType(uri)))
                } catch (error: Exception) {
                    if (!existing) runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    throw error
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Panel(stringResource(R.string.omni_shelf), stringResource(R.string.omni_shelf_help)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        noteId = null
                        editNote = true
                    }) { Text(stringResource(R.string.omni_new_note)) }
                    OutlinedButton(onClick = { documents.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.omni_pin_file)) }
                }
            }
        }
        item { ShelfMedia() }
        val scene = state.scenes.firstOrNull { it.id == state.activeScene }
        val sceneApps = scene?.apps.orEmpty().toSet()
        val launchApps = apps.filter { it.toComponentKey().toString() in sceneApps || it.title.toString().contains("omni", true) }.take(12)
        if (launchApps.isNotEmpty()) {
            item {
                Panel(scene?.name ?: stringResource(R.string.omni_apps)) {
                    launchApps.forEach { app ->
                        TextButton(onClick = { work { launchApp(context, app) } }) { Text(app.title.toString()) }
                    }
                }
            }
        }
        if (state.cards.isEmpty()) item { Panel(stringResource(R.string.omni_shelf_empty), stringResource(R.string.omni_shelf_empty_help)) {} }
        items(state.cards, key = { it.id }) { card ->
            Panel(card.title, card.text.takeIf { it.isNotBlank() }?.take(320)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.uri != null) TextButton(onClick = { work { openCard(context, card) } }) { Text(stringResource(R.string.omni_open)) }
                    if (card.text.isNotBlank()) {
                        TextButton(onClick = {
                            work { withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card.text), card.title)) } }
                        }) { Text(stringResource(R.string.omni_share)) }
                    }
                    if (card.uri == null) {
                        TextButton(onClick = {
                            noteId = card.id
                            editNote = true
                        }) { Text(stringResource(R.string.omni_edit)) }
                    }
                    TextButton(onClick = { removing = card }) { Text(stringResource(R.string.omni_remove)) }
                }
            }
        }
    }
    if (editNote) {
        val card = state.cards.firstOrNull { it.id == noteId }
        key(noteId) {
            TextEditor(
                title = stringResource(if (card == null) R.string.omni_new_note else R.string.omni_edit),
                firstLabel = stringResource(R.string.omni_title),
                secondLabel = stringResource(R.string.omni_note_text),
                initialFirst = card?.title.orEmpty(),
                initialSecond = card?.text ?: draft.takeUnless { draftConsumed }.orEmpty(),
                onDismiss = {
                    editNote = false
                    draftConsumed = true
                },
            ) { title, text ->
                withContext(Dispatchers.IO) {
                    store.putCard(ShelfCard(card?.id ?: ExperienceStore.newId(), title.ifBlank { context.getString(R.string.omni_new_note) }.take(160), text.take(4000)))
                }
            }
        }
    }
    removing?.let { card ->
        ConfirmRemoval(card.title, onDismiss = { removing = null }) { store.removeCard(card.id) }
    }
}

private suspend fun launchApp(context: Context, app: AppInfo) {
    withContext(Dispatchers.Main) {
        (context.getSystemService(android.content.pm.LauncherApps::class.java) ?: error("launcher_service_unavailable")).startMainActivity(app.componentName, app.user, null, null)
    }
}
private suspend fun openCard(context: Context, card: ShelfCard) = withContext(Dispatchers.Main) {
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(card.uri), card.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

@Composable
private fun ScenesScreen(state: ExperienceState, store: ExperienceStore, work: Work) {
    val context = LocalContext.current
    var create by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<HomeScene?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Panel(stringResource(R.string.omni_scenes), stringResource(R.string.omni_scenes_help)) {
                Toggle(stringResource(R.string.omni_automatic), state.automaticScenes) { enabled -> work { store.update { it.copy(automaticScenes = enabled) } } }
                Text(stringResource(R.string.omni_scene_priority), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { create = true }, enabled = state.scenes.size < 20) { Text(stringResource(R.string.omni_capture_scene)) }
                if (state.undoScene != null) TextButton(onClick = { work { SceneController.undo(context) } }) { Text(stringResource(R.string.omni_undo)) }
            }
        }
        if (state.scenes.isEmpty()) item { Panel(stringResource(R.string.omni_scenes_empty), stringResource(R.string.omni_scenes_help)) {} }
        items(state.scenes, key = { it.id }) { scene ->
            Panel(scene.name, if (scene.id == state.activeScene) stringResource(R.string.omni_active) else null) {
                FlowRow {
                    TextButton(onClick = { work { SceneController.apply(context, scene.id) } }) { Text(stringResource(R.string.omni_apply)) }
                    TextButton(onClick = { editingId = scene.id }) { Text(stringResource(R.string.omni_edit)) }
                    TextButton(onClick = { removing = scene }) { Text(stringResource(R.string.omni_remove)) }
                    val index = state.scenes.indexOfFirst { it.id == scene.id }
                    TextButton(enabled = index > 0, onClick = { work { store.update { it.moveScene(scene.id, -1) } } }) { Text(stringResource(R.string.omni_move_up)) }
                    TextButton(enabled = index < state.scenes.lastIndex, onClick = { work { store.update { it.moveScene(scene.id, 1) } } }) { Text(stringResource(R.string.omni_move_down)) }
                }
            }
        }
    }
    removing?.let { scene ->
        ConfirmRemoval(scene.name, onDismiss = { removing = null }) {
            store.update { it.copy(scenes = it.scenes.filterNot { s -> s.id == scene.id }, activeScene = it.activeScene.takeUnless { id -> id == scene.id }) }
        }
    }
    if (create) {
        TextEditor(stringResource(R.string.omni_capture_scene), stringResource(R.string.omni_title), onDismiss = { create = false }) { name, _ ->
            withContext(Dispatchers.IO) {
                val scene = SceneController.capture(context, name.trim().take(80))
                store.update { it.copy(scenes = it.scenes + scene) }
            }
        }
    }
    state.scenes.firstOrNull { it.id == editingId }?.let { scene ->
        SceneEditor(scene, onDismiss = { editingId = null }) { value ->
            withContext(Dispatchers.IO) { store.update { it.copy(scenes = it.scenes.map { s -> if (s.id == value.id) value else s }) } }
        }
    }
}

@Composable
private fun SceneEditor(scene: HomeScene, onDismiss: () -> Unit, onSave: suspend (HomeScene) -> Unit) {
    var name by rememberSaveable { mutableStateOf(scene.name) }
    var start by rememberSaveable { mutableStateOf(timeString(scene.startMinute)) }
    var end by rememberSaveable { mutableStateOf(timeString(scene.endMinute)) }
    var automatic by rememberSaveable { mutableStateOf(scene.automatic) }
    var charging by rememberSaveable { mutableStateOf(scene.charging) }
    var headset by rememberSaveable { mutableStateOf(scene.headset) }
    var theme by rememberSaveable { mutableStateOf(scene.strings["theme"] ?: "system") }
    val picker = rememberAppPicker()
    val apps = picker.apps
    var pinned by rememberStringSet(scene.apps.toSet())
    var query by rememberSaveable { mutableStateOf("") }
    val matchingApps = rememberMatchingApps(apps, query)
    SaveDialog(
        title = stringResource(R.string.omni_edit_scene),
        onDismiss = onDismiss,
        valid = name.isNotBlank() && parseSceneTime(start) != null && parseSceneTime(end) != null,
        onSave = {
            onSave(scene.copy(name = name.trim(), strings = scene.strings + ("theme" to theme), apps = pinned.toList(), automatic = automatic, startMinute = parseSceneTime(start)!!, endMinute = parseSceneTime(end)!!, charging = charging, headset = headset))
        },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.omni_title)) }) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("system" to R.string.omni_theme_system, "light" to R.string.omni_theme_light, "dark" to R.string.omni_dark).forEach { (id, label) ->
                        FilterChip(theme == id, { theme = id }, label = { Text(stringResource(label)) })
                    }
                }
            }
            item { Toggle(stringResource(R.string.omni_automatic), automatic) { automatic = it } }
            item { OutlinedTextField(start, { start = it.map { c -> if (c.isDigit()) ('0'.code + Character.digit(c, 10)).toChar() else c }.joinToString("").take(5) }, label = { Text(stringResource(R.string.omni_start_time)) }, singleLine = true, isError = parseSceneTime(start) == null, supportingText = { Text(stringResource(R.string.omni_time_help)) }) }
            item { OutlinedTextField(end, { end = it.map { c -> if (c.isDigit()) ('0'.code + Character.digit(c, 10)).toChar() else c }.joinToString("").take(5) }, label = { Text(stringResource(R.string.omni_end_time)) }, singleLine = true, isError = parseSceneTime(end) == null, supportingText = { Text(stringResource(R.string.omni_time_help)) }) }
            item { ConditionPicker(stringResource(R.string.omni_when_charging), charging) { charging = it } }
            item { ConditionPicker(stringResource(R.string.omni_when_headset), headset) { headset = it } }
            item { Text(stringResource(R.string.omni_selected_apps, pinned.size, 12)) }
            item { OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.omni_search_apps)) }, singleLine = true) }
            item { AppPickerNotice(picker, matchingApps.isEmpty()) }
            items(matchingApps, key = { it.toComponentKey().toString() }) { app ->
                val key = app.toComponentKey().toString()
                SwitchPreference(
                    checked = key in pinned,
                    onCheckedChange = { enabled -> if (!enabled || pinned.size < 12) pinned = if (enabled) pinned + key else pinned - key },
                    label = app.title.toString(),
                    description = appIdentity(app),
                )
            }
        }
    }
}
private fun timeString(minute: Int) = String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun RulesScreen(state: ExperienceState, store: ExperienceStore) {
    val context = LocalContext.current
    var create by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<FolderRule?>(null) }
    val folderTitles by produceState<Map<Int, String>>(emptyMap()) {
        FolderService.INSTANCE.get(context).getFoldersFlow().collect { folders ->
            value = folders.associate { it.id to it.title.toString() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Panel(stringResource(R.string.omni_rule_folders), stringResource(R.string.omni_rules_help)) {
                Button(onClick = { create = true }) { Text(stringResource(R.string.omni_add_rule_folder)) }
            }
        }
        if (state.rules.isEmpty()) item { Panel(stringResource(R.string.omni_rules_empty), stringResource(R.string.omni_rules_help)) {} }
        items(state.rules, key = { it.folderId }) { rule ->
            Panel(folderTitles[rule.folderId] ?: rule.nameContains.ifBlank { rule.packagePrefix.ifBlank { context.getString(R.string.omni_rule_folders) } }) {
                TextButton(onClick = { editingId = rule.folderId }) { Text(stringResource(R.string.omni_edit)) }
                TextButton(onClick = { removing = rule }) { Text(stringResource(R.string.omni_remove)) }
            }
        }
    }
    if (create) {
        RuleEditor(null, onDismiss = { create = false }) { title, rule ->
            withContext(Dispatchers.IO) { FolderService.INSTANCE.get(context).createRuleFolder(title.trim(), rule) }
        }
    }
    removing?.let { rule ->
        ConfirmRemoval(folderTitles[rule.folderId].orEmpty(), onDismiss = { removing = null }) {
            FolderService.INSTANCE.get(context).deleteFolderInfo(rule.folderId)
        }
    }
    state.rules.firstOrNull { it.folderId == editingId }?.let { rule ->
        RuleEditor(rule, onDismiss = { editingId = null }) { _, updated ->
            withContext(Dispatchers.IO) { store.update { it.copy(rules = it.rules.map { r -> if (r.folderId == updated.folderId) updated else r }) } }
        }
    }
}

@Composable
private fun RuleEditor(rule: FolderRule?, onDismiss: () -> Unit, onSave: suspend (String, FolderRule) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(rule?.nameContains.orEmpty()) }
    var pkg by rememberSaveable { mutableStateOf(rule?.packagePrefix.orEmpty()) }
    var games by rememberSaveable { mutableStateOf(rule?.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) }
    var include by rememberStringSet(rule?.include.orEmpty())
    var exclude by rememberStringSet(rule?.exclude.orEmpty())
    val picker = rememberAppPicker()
    val apps = picker.apps
    val inventory = remember { OmniAppInventory.get(context) }
    val preview = FolderRule(rule?.folderId ?: 0, name, pkg, if (games) android.content.pm.ApplicationInfo.CATEGORY_GAME else null, include, exclude)
    val matching by produceState<Int?>(null, apps, preview) {
        value = withContext(Dispatchers.Default) {
            apps.count { preview.matches(it.toComponentKey().toString(), it.title.toString(), it.componentName?.packageName.orEmpty(), inventory.category(it)) }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val matchingApps = rememberMatchingApps(apps, query)
    SaveDialog(
        title = stringResource(R.string.omni_rule_folders),
        onDismiss = onDismiss,
        valid = (rule != null || title.isNotBlank()) && (name.isNotBlank() || pkg.isNotBlank() || games || include.isNotEmpty()),
        onSave = { onSave(title, FolderRule(rule?.folderId ?: 0, name.trim(), pkg.trim(), if (games) android.content.pm.ApplicationInfo.CATEGORY_GAME else null, include, exclude)) },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rule == null) item { OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.omni_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.omni_name_contains)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(pkg, { pkg = it.take(120) }, label = { Text(stringResource(R.string.omni_package_prefix)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Toggle(stringResource(R.string.omni_games_only), games) { games = it } }
            if (!picker.loading && !picker.failed && matching != null) {
                item {
                    Text(stringResource(R.string.omni_rule_preview, matching!!), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            item { Text(stringResource(R.string.omni_rule_overrides)) }
            item { OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.omni_search_apps)) }, singleLine = true) }
            item { AppPickerNotice(picker, matchingApps.isEmpty()) }
            items(matchingApps, key = { it.toComponentKey().toString() }) { app ->
                val key = app.toComponentKey().toString()
                Column {
                    Text(app.title.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(appIdentity(app), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(key in include, {
                            include = if (key in include) include - key else include + key
                            exclude = exclude - key
                        }, label = { Text(stringResource(R.string.omni_always_include)) })
                        FilterChip(key in exclude, {
                            exclude = if (key in exclude) exclude - key else exclude + key
                            include = include - key
                        }, label = { Text(stringResource(R.string.omni_exclude)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasesScreen(state: ExperienceState, store: ExperienceStore) {
    val picker = rememberAppPicker()
    val apps = picker.apps
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val matchingApps = rememberMatchingApps(apps, query, state.aliases)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PreferenceGroup(showDividers = false) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.omni_alias_help), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.omni_search_apps)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    AppPickerNotice(picker, matchingApps.isEmpty())
                }
            }
        }
        items(matchingApps, key = { it.toComponentKey().toString() }) { app ->
            PreferenceGroup {
                PreferenceTemplate(
                    title = { Text(app.title.toString()) },
                    description = {
                        Text(appIdentity(app), style = MaterialTheme.typography.bodySmall)
                        state.aliases[app.toComponentKey().toString()]?.let { Text(it) }
                    },
                    endWidget = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { selectedKey = app.toComponentKey().toString() },
                )
            }
        }
    }
    apps.firstOrNull { it.toComponentKey().toString() == selectedKey }?.let { app ->
        TextEditor(app.title.toString(), stringResource(R.string.omni_aliases), initialFirst = state.aliases[app.toComponentKey().toString()].orEmpty(), allowEmpty = true, onDismiss = { selectedKey = null }) { aliases, _ ->
            withContext(Dispatchers.IO) {
                val key = app.toComponentKey().toString()
                store.update { it.copy(aliases = if (aliases.isBlank()) it.aliases - key else it.aliases + (key to aliases.trim().take(200))) }
            }
        }
    }
}

@Composable
private fun StacksScreen(state: ExperienceState) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Panel(stringResource(R.string.omni_widget_stacks), stringResource(R.string.omni_stack_help)) {
                Button(onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(OmniWidgetStack.component(context), null, null)
                    } else {
                        Toast.makeText(context, R.string.omni_stack_picker_hint, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.omni_add_stack_home)) }
            }
        }
        items(state.stacks, key = { it.outerId }) { stack ->
            Panel(if (stack.widgetIds.isEmpty()) stringResource(R.string.omni_stack_empty) else stringResource(R.string.omni_stack_position, stack.selected + 1, stack.widgetIds.size)) {
                TextButton(onClick = { context.startActivity(WidgetStackActivity.intent(context, stack.outerId)) }) { Text(stringResource(R.string.omni_edit)) }
            }
        }
    }
}

@Composable
private fun SystemScreen(state: ExperienceState, work: Work) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var chooseWorkspace by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val statusResult by produceState<Result<OemIntegration.Status>?>(null, revision) {
        value = null
        value = try {
            Result.success(OemIntegration.status(context))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val targetResult by produceState<Result<List<Pair<String, Intent>>>?>(null, revision) {
        value = null
        value = try {
            Result.success(OmniAskActivity.workspaceTargets(context))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val targets = targetResult?.getOrNull()
    val preferred = targets?.firstOrNull { it.second.component?.flattenToString() == state.preferredWorkspace }
    val workspaceSummary = when {
        targetResult?.isFailure == true -> stringResource(R.string.omni_apps_failed)
        targets == null -> stringResource(R.string.omni_apps_loading)
        state.preferredWorkspace == null -> stringResource(R.string.omni_reset_workspace)
        preferred != null -> preferred.first + "\n" + preferred.second.component!!.packageName
        else -> stringResource(R.string.omni_workspace_selection_missing)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PreferenceGroup(heading = stringResource(R.string.omni_android_capabilities)) {
                if (statusResult == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (statusResult?.isFailure == true) {
                    Text(stringResource(R.string.omni_integration_load_failed), modifier = Modifier.padding(16.dp))
                    TextButton(onClick = { revision++ }) { Text(stringResource(R.string.omni_retry)) }
                }
                statusResult?.getOrNull()?.let {
                    SystemStatus(R.string.omni_system_app, if (it.systemApp) R.string.omni_install_system else R.string.omni_install_regular)
                    SystemStatus(
                        R.string.omni_enterprise_owner,
                        when {
                            it.deviceOwner -> R.string.omni_role_device_owner
                            it.profileOwner -> R.string.omni_role_profile_owner
                            else -> R.string.omni_role_personal
                        },
                    )
                    SystemStatus(R.string.omni_recents_integration, if (it.quickstep) R.string.omni_status_available else R.string.omni_recents_system)
                }
                app.lawnchair.ui.preferences.components.controls.ClickablePreference(
                    label = stringResource(R.string.omni_integration_details),
                    onClick = { showDetails = true },
                )
                app.lawnchair.ui.preferences.components.controls.ClickablePreference(
                    label = stringResource(R.string.omni_default_home),
                    onClick = { work { withContext(Dispatchers.Main) { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } } },
                )
            }
        }
        item { OmniIntegrationPreferences() }
        item {
            PreferenceGroup(heading = stringResource(R.string.omni_preferred_workspace)) {
                app.lawnchair.ui.preferences.components.controls.ClickablePreference(
                    label = workspaceSummary,
                    subtitle = stringResource(R.string.omni_workspace_selection_help),
                    onClick = { chooseWorkspace = true },
                )
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.omni_android_capabilities)) },
            text = { Text(stringResource(R.string.omni_oem_help), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    if (chooseWorkspace) {
        AlertDialog(
            onDismissRequest = { chooseWorkspace = false },
            title = { Text(stringResource(R.string.omni_preferred_workspace)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = {
                        work { ExperienceStore.get(context).update { it.copy(preferredWorkspace = null) } }
                        chooseWorkspace = false
                    }) { Text(stringResource(R.string.omni_reset_workspace)) }
                    if (targetResult == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (targetResult?.isFailure == true) {
                        Text(stringResource(R.string.omni_apps_failed))
                        TextButton(onClick = { revision++ }) { Text(stringResource(R.string.omni_retry)) }
                    }
                    targets.orEmpty().forEach { (label, intent) ->
                        PreferenceTemplate(
                            title = { Text(label) },
                            description = { Text(intent.component!!.packageName) },
                            modifier = Modifier.clickable {
                                work { ExperienceStore.get(context).update { it.copy(preferredWorkspace = intent.component!!.flattenToString()) } }
                                chooseWorkspace = false
                            },
                        )
                    }
                    if (targets?.isEmpty() == true) Text(stringResource(R.string.omni_workspace_required))
                }
            },
            confirmButton = { TextButton(onClick = { chooseWorkspace = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchPreference(checked = checked, onCheckedChange = onChange, label = label)
}

@Composable
private fun TextEditor(
    title: String,
    firstLabel: String,
    onDismiss: () -> Unit,
    secondLabel: String? = null,
    initialFirst: String = "",
    initialSecond: String = "",
    allowEmpty: Boolean = false,
    onSave: suspend (String, String) -> Unit,
) {
    var first by rememberSaveable { mutableStateOf(initialFirst) }
    var second by rememberSaveable { mutableStateOf(initialSecond) }
    SaveDialog(title = title, onDismiss = onDismiss, valid = allowEmpty || first.isNotBlank() || second.isNotBlank(), onSave = { onSave(first.trim(), second) }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it.take(200) }, label = { Text(firstLabel) })
            if (secondLabel != null) OutlinedTextField(second, { second = it.take(4000) }, label = { Text(secondLabel) }, minLines = 3, maxLines = 6)
        }
    }
}

@Composable
private fun appIdentity(app: AppInfo): String {
    val packageName = app.componentName?.packageName.orEmpty()
    return if (app.user == android.os.Process.myUserHandle()) {
        packageName
    } else {
        stringResource(R.string.omni_app_other_profile, packageName)
    }
}

@Composable
private fun ConditionPicker(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to R.string.omni_condition_any, true to R.string.omni_condition_yes, false to R.string.omni_condition_no).forEach { (condition, title) ->
                FilterChip(selected = value == condition, onClick = { onChange(condition) }, label = { Text(stringResource(title)) })
            }
        }
    }
}

/** Keep unsaved input on failure and prevent repeat submissions or dismissing an active write. */
@Composable
private fun SaveDialog(title: String, onDismiss: () -> Unit, valid: Boolean, onSave: suspend () -> Unit, actionLabel: Int = R.string.omni_save, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) Text(stringResource(R.string.omni_save_failed), color = MaterialTheme.colorScheme.error)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f, fill = false)) { content() }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !saving, onClick = {
                saving = true
                failed = false
                scope.launch {
                    try {
                        onSave()
                        onDismiss()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            }) { Text(stringResource(actionLabel)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ConfirmRemoval(title: String, onDismiss: () -> Unit, remove: suspend () -> Unit) {
    SaveDialog(title = title, onDismiss = onDismiss, valid = true, actionLabel = R.string.omni_remove, onSave = { withContext(Dispatchers.IO) { remove() } }) {
        Text(stringResource(R.string.omni_delete_confirm))
    }
}

@Composable
private fun rememberStringSet(initial: Set<String>) = rememberSaveable(
    stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
) { mutableStateOf(initial) }

@Composable
private fun SystemStatus(label: Int, summary: Int) {
    PreferenceTemplate(
        title = { Text(stringResource(label)) },
        description = { Text(stringResource(summary)) },
    )
}
