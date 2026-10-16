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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.omni.OmniIntegrationPreferences
import app.lawnchair.omni.experience.widgets.OmniWidgetStack
import app.lawnchair.omni.experience.widgets.WidgetStackActivity
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
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
                Hub(intent.getStringExtra("page") ?: "shelf", draft, intent.getBooleanExtra("new_note", false))
            }
        }
    }
    companion object {
        fun intent(context: Context, page: String = "shelf") = Intent(context, OmniHubActivity::class.java).putExtra("page", page)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hub(initialPage: String, draft: String?, newNote: Boolean) {
    val context = LocalContext.current
    val store = remember { ExperienceStore.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val work: Work = { operation ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                snackbar.showSnackbar(context.getString(R.string.omni_action_unavailable))
            }
        }
    }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    val pages = listOf("shelf" to R.string.omni_shelf, "scenes" to R.string.omni_scenes, "folders" to R.string.omni_rule_folders, "aliases" to R.string.omni_aliases, "widgets" to R.string.omni_widget_stacks, "system" to R.string.omni_system)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.omni_hub)) }, navigationIcon = {
                TextButton(onClick = { (context as? android.app.Activity)?.finish() }) { Text(stringResource(R.string.omni_back)) }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                items(pages) { (id, title) -> FilterChip(selected = page == id, onClick = { page = id }, label = { Text(stringResource(title)) }) }
            }
            HorizontalDivider()
            val current = state
            if (current == null) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else {
                when (page) {
                    "scenes" -> ScenesScreen(current, store, work)
                    "folders" -> RulesScreen(current, store, work)
                    "aliases" -> AliasesScreen(current, store, work)
                    "widgets" -> StacksScreen(current)
                    "system" -> SystemScreen()
                    else -> ShelfScreen(current, store, work, draft, newNote)
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun ShelfScreen(state: ExperienceState, store: ExperienceStore, work: Work, draft: String?, newNote: Boolean) {
    val context = LocalContext.current
    var editNote by rememberSaveable { mutableStateOf(draft != null || newNote) }
    val inventory = remember { OmniAppInventory.get(context) }
    val revision by inventory.revision.collectAsStateWithLifecycle()
    val apps by produceState<List<AppInfo>>(emptyList(), revision) { value = inventory.visible() }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            work {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: context.getString(R.string.omni_document)
                store.putCard(ShelfCard(ExperienceStore.newId(), title.take(160), uri = uri.toString(), mimeType = context.contentResolver.getType(uri)))
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Panel(stringResource(R.string.omni_shelf), stringResource(R.string.omni_shelf_help)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editNote = true }) { Text(stringResource(R.string.omni_new_note)) }
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
        items(state.cards, key = { it.id }) { card ->
            Panel(card.title, card.text.takeIf { it.isNotBlank() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.uri != null) TextButton(onClick = { work { openCard(context, card) } }) { Text(stringResource(R.string.omni_open)) }
                    if (card.text.isNotBlank()) {
                        TextButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card.text), card.title))
                        }) { Text(stringResource(R.string.omni_share)) }
                    }
                    TextButton(onClick = { work { store.update { it.copy(cards = it.cards.filterNot { item -> item.id == card.id }) } } }) { Text(stringResource(R.string.omni_remove)) }
                }
            }
        }
    }
    if (editNote) {
        TextEditor(stringResource(R.string.omni_new_note), stringResource(R.string.omni_title), stringResource(R.string.omni_note_text), initialSecond = draft.orEmpty(), onDismiss = { editNote = false }) { title, text ->
            work { store.putCard(ShelfCard(ExperienceStore.newId(), title.ifBlank { context.getString(R.string.omni_new_note) }.take(160), text.take(4000))) }
            editNote = false
        }
    }
}

private suspend fun launchApp(context: Context, app: AppInfo) {
    withContext(Dispatchers.Main) {
        context.getSystemService(android.content.pm.LauncherApps::class.java).startMainActivity(app.componentName, app.user, null, null)
    }
}
private suspend fun openCard(context: Context, card: ShelfCard) = withContext(Dispatchers.Main) {
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(card.uri), card.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

@Composable
private fun ScenesScreen(state: ExperienceState, store: ExperienceStore, work: Work) {
    val context = LocalContext.current
    var create by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HomeScene?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Panel(stringResource(R.string.omni_scenes), stringResource(R.string.omni_scenes_help)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.omni_automatic))
                    Switch(state.automaticScenes, { enabled -> work { store.update { it.copy(automaticScenes = enabled) } } })
                }
                Button(onClick = { create = true }, enabled = state.scenes.size < 20) { Text(stringResource(R.string.omni_capture_scene)) }
                if (state.undoScene != null) TextButton(onClick = { work { SceneController.undo(context) } }) { Text(stringResource(R.string.omni_undo)) }
            }
        }
        items(state.scenes, key = { it.id }) { scene ->
            Panel(scene.name, if (scene.id == state.activeScene) stringResource(R.string.omni_active) else null) {
                Row {
                    TextButton(onClick = { work { SceneController.apply(context, scene.id) } }) { Text(stringResource(R.string.omni_apply)) }
                    TextButton(onClick = { editing = scene }) { Text(stringResource(R.string.omni_edit)) }
                    TextButton(onClick = { work { store.update { it.copy(scenes = it.scenes.filterNot { s -> s.id == scene.id }, activeScene = it.activeScene.takeUnless { id -> id == scene.id }) } } }) { Text(stringResource(R.string.omni_remove)) }
                }
            }
        }
    }
    if (create) {
        TextEditor(stringResource(R.string.omni_capture_scene), stringResource(R.string.omni_title), onDismiss = { create = false }) { name, _ ->
            work {
                val scene = SceneController.capture(context, name.take(80))
                store.update { it.copy(scenes = it.scenes + scene) }
            }
            create = false
        }
    }
    editing?.let { scene ->
        SceneEditor(scene, onDismiss = { editing = null }) { value ->
            work { store.update { it.copy(scenes = it.scenes.map { s -> if (s.id == value.id) value else s }) } }
            editing = null
        }
    }
}

@Composable
private fun SceneEditor(scene: HomeScene, onDismiss: () -> Unit, onSave: (HomeScene) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(scene.name) }
    var start by remember { mutableStateOf(timeString(scene.startMinute)) }
    var end by remember { mutableStateOf(timeString(scene.endMinute)) }
    var automatic by remember { mutableStateOf(scene.automatic) }
    var charging by remember { mutableStateOf(scene.charging == true) }
    var headset by remember { mutableStateOf(scene.headset == true) }
    var dark by remember { mutableStateOf(scene.strings["theme"] == "dark") }
    val apps by produceState<List<AppInfo>>(emptyList()) { value = OmniAppInventory.get(context).visible() }
    var pinned by remember { mutableStateOf(scene.apps.toSet()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.omni_edit_scene)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.omni_title)) }) }
            item { Toggle(stringResource(R.string.omni_dark), dark) { dark = it } }
            item { Toggle(stringResource(R.string.omni_automatic), automatic) { automatic = it } }
            item { OutlinedTextField(start, { start = it.take(5) }, label = { Text(stringResource(R.string.omni_start_time)) }) }
            item { OutlinedTextField(end, { end = it.take(5) }, label = { Text(stringResource(R.string.omni_end_time)) }) }
            item { Toggle(stringResource(R.string.omni_when_charging), charging) { charging = it } }
            item { Toggle(stringResource(R.string.omni_when_headset), headset) { headset = it } }
            item { Text(stringResource(R.string.omni_scene_apps)) }
            items(apps, key = { it.toComponentKey().toString() }) { app ->
                val key = app.toComponentKey().toString()
                Toggle(app.title.toString(), key in pinned) { enabled -> if (!enabled || pinned.size < 12) pinned = if (enabled) pinned + key else pinned - key }
            }
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && parseSceneTime(start) != null && parseSceneTime(end) != null, onClick = {
            onSave(scene.copy(name = name, strings = scene.strings + ("theme" to if (dark) "dark" else "light"), apps = pinned.toList(), automatic = automatic, startMinute = parseSceneTime(start)!!, endMinute = parseSceneTime(end)!!, charging = true.takeIf { charging }, headset = true.takeIf { headset }))
        }) { Text(stringResource(R.string.omni_save)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
private fun timeString(minute: Int) = String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun RulesScreen(state: ExperienceState, store: ExperienceStore, work: Work) {
    val context = LocalContext.current
    var create by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FolderRule?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Panel(stringResource(R.string.omni_rule_folders), stringResource(R.string.omni_rules_help)) {
                Button(onClick = { create = true }) { Text(stringResource(R.string.omni_add_rule_folder)) }
            }
        }
        items(state.rules, key = { it.folderId }) { rule ->
            Panel(rule.nameContains.ifBlank { rule.packagePrefix.ifBlank { context.getString(R.string.omni_rule_folders) } }) {
                TextButton(onClick = { editing = rule }) { Text(stringResource(R.string.omni_edit)) }
                TextButton(onClick = { work { FolderService.INSTANCE.get(context).deleteFolderInfo(rule.folderId) } }) { Text(stringResource(R.string.omni_remove)) }
            }
        }
    }
    if (create) {
        RuleEditor(null, onDismiss = { create = false }) { title, rule ->
            work { FolderService.INSTANCE.get(context).createRuleFolder(title, rule) }
            create = false
        }
    }
    editing?.let { rule ->
        RuleEditor(rule, onDismiss = { editing = null }) { _, updated ->
            work { store.update { it.copy(rules = it.rules.map { r -> if (r.folderId == updated.folderId) updated else r }) } }
            editing = null
        }
    }
}

@Composable
private fun RuleEditor(rule: FolderRule?, onDismiss: () -> Unit, onSave: (String, FolderRule) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(rule?.nameContains.orEmpty()) }
    var pkg by remember { mutableStateOf(rule?.packagePrefix.orEmpty()) }
    var games by remember { mutableStateOf(rule?.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) }
    var include by remember { mutableStateOf(rule?.include.orEmpty()) }
    var exclude by remember { mutableStateOf(rule?.exclude.orEmpty()) }
    val apps by produceState<List<AppInfo>>(emptyList()) { value = OmniAppInventory.get(context).visible() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.omni_rule_folders)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rule == null) item { OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.omni_title)) }) }
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.omni_name_contains)) }) }
            item { OutlinedTextField(pkg, { pkg = it.take(120) }, label = { Text(stringResource(R.string.omni_package_prefix)) }) }
            item { Toggle(stringResource(R.string.omni_games_only), games) { games = it } }
            item { Text(stringResource(R.string.omni_rule_overrides)) }
            items(apps, key = { it.toComponentKey().toString() }) { app ->
                val key = app.toComponentKey().toString()
                Column {
                    Text(app.title.toString())
                    Row {
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
    }, confirmButton = { TextButton(enabled = (rule != null || title.isNotBlank()) && (name.isNotBlank() || pkg.isNotBlank() || games || include.isNotEmpty()), onClick = { onSave(title, FolderRule(rule?.folderId ?: 0, name, pkg, if (games) android.content.pm.ApplicationInfo.CATEGORY_GAME else null, include, exclude)) }) { Text(stringResource(R.string.omni_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun AliasesScreen(state: ExperienceState, store: ExperienceStore, work: Work) {
    val context = LocalContext.current
    val apps by produceState<List<AppInfo>>(emptyList()) { value = OmniAppInventory.get(context).visible() }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Panel(stringResource(R.string.omni_aliases), stringResource(R.string.omni_alias_help)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.omni_search_apps)) })
            }
        }
        items(apps.filter { it.title.toString().contains(query, true) }, key = { it.toComponentKey().toString() }) { app ->
            Panel(app.title.toString(), state.aliases[app.toComponentKey().toString()]) { TextButton(onClick = { selected = app }) { Text(stringResource(R.string.omni_edit)) } }
        }
    }
    selected?.let { app ->
        TextEditor(app.title.toString(), stringResource(R.string.omni_aliases), initialFirst = state.aliases[app.toComponentKey().toString()].orEmpty(), allowEmpty = true, onDismiss = { selected = null }) { aliases, _ ->
            work {
                val key = app.toComponentKey().toString()
                store.update { it.copy(aliases = if (aliases.isBlank()) it.aliases - key else it.aliases + (key to aliases.take(200))) }
            }
            selected = null
        }
    }
}

@Composable
private fun StacksScreen(state: ExperienceState) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
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
            Panel(stringResource(R.string.omni_stack_position, stack.selected + 1, stack.widgetIds.size)) {
                TextButton(onClick = { context.startActivity(WidgetStackActivity.intent(context, stack.outerId)) }) { Text(stringResource(R.string.omni_edit)) }
            }
        }
    }
}

@Composable
private fun SystemScreen() {
    val context = LocalContext.current
    val status by produceState<OemIntegration.Status?>(null) { value = OemIntegration.status(context) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Panel(stringResource(R.string.omni_system), stringResource(R.string.omni_oem_help)) {
                status?.let {
                    Text(stringResource(R.string.omni_system_status, it.systemApp, it.deviceOwner || it.profileOwner, it.quickstep))
                }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text(stringResource(R.string.omni_default_home)) }
            }
        }
        item { OmniIntegrationPreferences() }
        item {
            Panel(stringResource(R.string.omni_preferred_workspace)) {
                val scope = rememberCoroutineScope()
                TextButton(onClick = { scope.launch(Dispatchers.IO) { ExperienceStore.get(context).update { it.copy(preferredWorkspace = null) } } }) { Text(stringResource(R.string.omni_choose_workspace)) }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(top = 12.dp, end = 8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun TextEditor(
    title: String,
    firstLabel: String,
    secondLabel: String? = null,
    initialFirst: String = "",
    initialSecond: String = "",
    allowEmpty: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var first by rememberSaveable { mutableStateOf(initialFirst) }
    var second by rememberSaveable { mutableStateOf(initialSecond) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it.take(200) }, label = { Text(firstLabel) })
            if (secondLabel != null) OutlinedTextField(second, { second = it.take(4000) }, label = { Text(secondLabel) }, minLines = 3, maxLines = 6)
        }
    }, confirmButton = { TextButton(enabled = allowEmpty || first.isNotBlank() || second.isNotBlank(), onClick = { onSave(first, second) }) { Text(stringResource(R.string.omni_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
