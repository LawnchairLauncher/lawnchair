package app.lawnchair.omni.experience.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.omni.experience.WidgetStack
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.DeviceTier
import app.lawnchair.util.DeviceTierManager
import com.android.launcher3.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class WidgetStackActivity : ComponentActivity() {
    private val errors = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        android.util.Log.w("OmniWidgetStack", "Widget editor operation failed", error)
        busy = false
        loading = false
        failed = true
    }
    private val calls = BoundedWidgetCalls.shared
    private var outerId = -1
    private var pendingId = -1
    private var host: StackHost? = null
    private var loadJob: Job? = null
    private var labelJob: Job? = null
    private var operationJob: Job? = null
    private var loadVersion = 0
    private var loading by mutableStateOf(true)
    private var failed by mutableStateOf(false)
    private var limit by mutableIntStateOf(4)
    private var validStack by mutableStateOf(false)
    private var members by mutableStateOf<List<Pair<Int, String>>?>(null)
    private var selected by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var removing by mutableStateOf<Int?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        outerId = savedInstanceState?.getInt("outer") ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        pendingId = savedInstanceState?.getInt("pending") ?: -1
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                StackEditor()
            }
        }
        load()
    }

    private fun showError(error: Exception) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        android.util.Log.w("OmniWidgetStack", "Widget editor unavailable", error)
        failed = true
    }

    private fun load() {
        val version = ++loadVersion
        loadJob?.cancel()
        labelJob?.cancel()
        loadJob = lifecycleScope.launch(errors) {
            loading = true
            failed = false
            try {
                render()
            } catch (error: Exception) {
                showError(error)
            } finally {
                if (version == loadVersion) loading = false
            }
        }
    }

    /** Only strings/IDs reach Compose. No system-service query or tier detection in composition. */
    private suspend fun render() {
        val app = applicationContext
        val id = outerId
        validStack = false
        val (stack, capacity) = calls.call {
            require(id > 0 && AppWidgetManager.getInstance(app).getAppWidgetInfo(id)?.provider == OmniWidgetStack.component(app)) { "Stack is no longer bound" }
            val capacity = if (DeviceTierManager.getInstance(app).tier == DeviceTier.LOW) 4 else 8
            // The blocking bridge is confined to the bounded worker, never the main dispatcher.
            val saved = runBlocking { ExperienceStore.get(app).read().stacks.firstOrNull { it.outerId == id } ?: WidgetStack(id) }
            saved to capacity
        }
        limit = capacity
        validStack = true
        val ids = stack.widgetIds.distinct().take(8)
        selected = stack.selected.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        members = ids.mapIndexed { index, widgetId -> widgetId to getString(R.string.omni_stack_member, index + 1) }
        // Show usable controls immediately. A slow or removed provider cannot hold the page open.
        loading = false
        labelJob?.cancel()
        labelJob = lifecycleScope.launch {
            for (widgetId in ids) {
                val fallback = members.orEmpty().firstOrNull { it.first == widgetId }?.second.orEmpty()
                val label = try {
                    calls.call(timeoutMillis = 1_000) {
                        AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId)?.loadLabel(app.packageManager) ?: fallback
                    }
                } catch (error: Exception) {
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    android.util.Log.w("OmniWidgetStack", "Widget label unavailable: $widgetId", error)
                    fallback
                }
                members = members.orEmpty().map { if (it.first == widgetId) widgetId to label else it }
            }
        }
    }

    private suspend fun editorHost(): StackHost {
        host?.let { return it }
        val app = applicationContext
        val id = outerId
        return calls.call { StackHost(app, id) }.also { host = it }
    }

    private fun mutate(refresh: Boolean = true, operation: suspend () -> Unit) {
        if (busy || loading || !validStack) return
        ++loadVersion
        loadJob?.cancel()
        labelJob?.cancel()
        busy = true
        failed = false
        operationJob = lifecycleScope.launch(errors) {
            try {
                operation()
                if (refresh) render()
            } catch (error: Exception) {
                showError(error)
            } finally {
                busy = false
                loading = false
            }
        }
    }

    private suspend fun changeStack(transform: (WidgetStack) -> WidgetStack) = withContext(Dispatchers.IO) {
        ExperienceStore.get(this@WidgetStackActivity).update { state ->
            state.copy(stacks = state.stacks.map { if (it.outerId == outerId) transform(it) else it })
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun StackEditor() {
        PreferenceScaffold(label = stringResource(R.string.omni_widget_stacks), isExpandedScreen = false) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        PreferenceGroup(description = stringResource(R.string.omni_stack_help)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (loading && members == null) stringResource(R.string.omni_stack_loading) else stringResource(R.string.omni_selected_apps, members.orEmpty().size, limit))
                                if (failed) {
                                    Text(stringResource(R.string.omni_stack_load_failed))
                                    TextButton(enabled = !busy && !loading, onClick = ::load) { Text(stringResource(R.string.omni_retry)) }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && !loading && validStack && members != null && members.orEmpty().size < limit && pendingId < 0, onClick = {
                                        mutate(refresh = false) {
                                            val widgetHost = editorHost()
                                            pendingId = calls.call(discard = { abandoned -> widgetHost.deleteAppWidgetId(abandoned) }) { widgetHost.allocateAppWidgetId() }
                                            try {
                                                startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId), PICK)
                                            } catch (_: Exception) {
                                                discard()
                                            }
                                        }
                                    }) { Text(stringResource(R.string.omni_stack_add)) }
                                    TextButton(enabled = !busy && !loading && validStack && members != null, onClick = {
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, outerId))
                                        finish()
                                    }) { Text(stringResource(R.string.omni_done)) }
                                }
                            }
                        }
                    }
                    if (members?.isEmpty() == true) {
                        item {
                            PreferenceGroup(showDividers = false) {
                                Text(stringResource(R.string.omni_stack_empty), Modifier.padding(16.dp))
                            }
                        }
                    }
                    itemsIndexed(members.orEmpty(), key = { _, member -> member.first }) { index, (id, label) ->
                        PreferenceGroup(heading = label) {
                            Column {
                                SwitchPreference(checked = index == selected, onCheckedChange = { if (it) mutate { changeStack { stack -> stack.copy(selected = index) } } }, label = stringResource(R.string.omni_stack_show), enabled = !busy && !loading)
                                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy && !loading && index > 0, onClick = { mutate { changeStack { it.moveMember(index, index - 1) } } }) { Text(stringResource(R.string.omni_move_up)) }
                                    TextButton(enabled = !busy && !loading && index < members.orEmpty().lastIndex, onClick = { mutate { changeStack { it.moveMember(index, index + 1) } } }) { Text(stringResource(R.string.omni_move_down)) }
                                    TextButton(enabled = !busy && !loading, onClick = { removing = id }) { Text(stringResource(R.string.omni_remove)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        removing?.let { id ->
            AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.omni_remove)) }, text = { Text(stringResource(R.string.omni_delete_confirm)) }, confirmButton = {
                TextButton(onClick = {
                    removing = null
                    mutate {
                        changeStack { it.removeMember(id) }
                        val widgetHost = editorHost()
                        calls.call { widgetHost.deleteAppWidgetId(id) }
                    }
                }) { Text(stringResource(R.string.omni_remove)) }
            }, dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } })
        }
    }

    @Deprecated("Legacy widget provider configuration contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in setOf(PICK, BIND, CONFIGURE)) return
        ++loadVersion
        loadJob?.cancel()
        labelJob?.cancel()
        val previousOperation = operationJob
        operationJob = lifecycleScope.launch(errors) {
            previousOperation?.join()
            busy = true
            failed = false
            try {
                if (resultCode != Activity.RESULT_OK || pendingId <= 0) {
                    discard()
                    render()
                    return@launch
                }
                val app = applicationContext
                val candidateId = pendingId
                val info = calls.call { AppWidgetManager.getInstance(app).getAppWidgetInfo(candidateId) }
                if (info == null && requestCode == PICK) {
                    val component = data?.getParcelableExtra<android.content.ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
                    if (component == null) {
                        discard()
                        render()
                        return@launch
                    }
                    try {
                        startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component), BIND)
                    } catch (_: Exception) {
                        discard()
                        render()
                    }
                    return@launch
                }
                if (info == null || info.provider == OmniWidgetStack.component(this@WidgetStackActivity)) {
                    discard()
                    render()
                    return@launch
                }
                if (requestCode != CONFIGURE && info.configure != null) {
                    try {
                        editorHost().startAppWidgetConfigureActivityForResult(this@WidgetStackActivity, pendingId, 0, CONFIGURE, null)
                    } catch (_: Exception) {
                        discard()
                        render()
                    }
                    return@launch
                }
                val id = pendingId
                withContext(Dispatchers.IO) {
                    ExperienceStore.get(this@WidgetStackActivity).update { state ->
                        val old = state.stacks.firstOrNull { it.outerId == outerId } ?: WidgetStack(outerId)
                        require(old.widgetIds.size < limit)
                        state.copy(stacks = state.stacks.filterNot { it.outerId == outerId } + old.copy(widgetIds = (old.widgetIds + id).distinct()))
                    }
                }
                pendingId = -1
                render()
            } catch (error: Exception) {
                showError(error)
            } finally {
                busy = false
                loading = false
            }
        }
    }
    private suspend fun discard() {
        if (pendingId > 0) {
            val id = pendingId
            val widgetHost = editorHost()
            calls.call { widgetHost.deleteAppWidgetId(id) }
            pendingId = -1
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("outer", outerId)
        outState.putInt("pending", pendingId)
    }
    override fun onDestroy() {
        if (isFinishing && pendingId > 0) {
            val id = pendingId
            val app = applicationContext
            val stackId = outerId
            val worker = calls
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    worker.call {
                        val committed = runBlocking { ExperienceStore.get(app).read().stacks.any { id in it.widgetIds } }
                        if (!committed) StackHost(app, stackId).deleteAppWidgetId(id)
                    }
                }
            }
        }
        loadJob?.cancel()
        labelJob?.cancel()
        super.onDestroy()
    }
    companion object {
        private const val PICK = 10
        private const val BIND = 11
        private const val CONFIGURE = 12
        fun intent(context: Context, id: Int) = Intent(context, WidgetStackActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }
}
