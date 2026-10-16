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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetStackActivity : ComponentActivity() {
    private val errors = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        android.util.Log.w("OmniWidgetStack", "Widget configuration unavailable", error)
        runOnUiThread {
            Toast.makeText(this, R.string.omni_action_unavailable, Toast.LENGTH_LONG).show()
            finish()
        }
    }
    private var outerId = -1
    private var pendingId = -1
    private lateinit var host: StackHost
    private var members by mutableStateOf<List<Pair<Int, String>>?>(null)
    private var selected by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var removing by mutableStateOf<Int?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        outerId = savedInstanceState?.getInt("outer") ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        pendingId = savedInstanceState?.getInt("pending") ?: -1
        host = StackHost(this, outerId)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                StackEditor()
            }
        }
        lifecycleScope.launch(errors) {
            val info = withContext(Dispatchers.IO) { AppWidgetManager.getInstance(this@WidgetStackActivity).getAppWidgetInfo(outerId) }
            if (outerId <= 0 || info?.provider != OmniWidgetStack.component(this@WidgetStackActivity)) {
                finish()
                return@launch
            }
            render()
        }
    }
    private suspend fun render() {
        val stack = withContext(Dispatchers.IO) {
            ExperienceStore.get(this@WidgetStackActivity).read().stacks.firstOrNull { it.outerId == outerId } ?: WidgetStack(outerId)
        }
        val labels = withContext(Dispatchers.IO) {
            stack.widgetIds.map { id ->
                val info = AppWidgetManager.getInstance(this@WidgetStackActivity).getAppWidgetInfo(id)
                id to (info?.loadLabel(packageManager) ?: getString(R.string.omni_action_unavailable))
            }
        }
        members = labels
        selected = stack.selected
    }

    private fun mutate(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        lifecycleScope.launch(errors) {
            try {
                operation()
                render()
            } finally {
                busy = false
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
                if (busy || members == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        PreferenceGroup(description = stringResource(R.string.omni_stack_help)) {
                            Column(Modifier.padding(16.dp)) {
                                val limit = if (DeviceTierManager.getInstance(this@WidgetStackActivity).tier == DeviceTier.LOW) 4 else 8
                                Text(stringResource(R.string.omni_selected_apps, members.orEmpty().size, limit))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && members != null && members.orEmpty().size < limit && pendingId < 0, onClick = {
                                        mutate {
                                            pendingId = withContext(Dispatchers.IO) { host.allocateAppWidgetId() }
                                            try {
                                                startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId), PICK)
                                            } catch (_: Exception) {
                                                discard()
                                            }
                                        }
                                    }) { Text(stringResource(R.string.omni_stack_add)) }
                                    TextButton(enabled = !busy && members != null, onClick = {
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, outerId))
                                        finish()
                                    }) { Text(stringResource(R.string.omni_done)) }
                                }
                            }
                        }
                    }
                    if (members?.isEmpty() == true) {
                        item {
                            PreferenceGroup(description = stringResource(R.string.omni_stack_empty)) {}
                        }
                    }
                    itemsIndexed(members.orEmpty(), key = { _, member -> member.first }) { index, (id, label) ->
                        PreferenceGroup(heading = label) {
                            Column {
                                SwitchPreference(checked = index == selected, onCheckedChange = { if (it) mutate { changeStack { stack -> stack.copy(selected = index) } } }, label = stringResource(R.string.omni_stack_show), enabled = !busy)
                                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy && index > 0, onClick = { mutate { changeStack { it.moveMember(index, index - 1) } } }) { Text(stringResource(R.string.omni_move_up)) }
                                    TextButton(enabled = !busy && index < members.orEmpty().lastIndex, onClick = { mutate { changeStack { it.moveMember(index, index + 1) } } }) { Text(stringResource(R.string.omni_move_down)) }
                                    TextButton(enabled = !busy, onClick = { removing = id }) { Text(stringResource(R.string.omni_remove)) }
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
                        withContext(Dispatchers.IO) { host.deleteAppWidgetId(id) }
                    }
                }) { Text(stringResource(R.string.omni_remove)) }
            }, dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } })
        }
    }

    @Deprecated("Legacy widget provider configuration contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in setOf(PICK, BIND, CONFIGURE)) return
        lifecycleScope.launch(errors) {
            if (resultCode != Activity.RESULT_OK || pendingId <= 0) {
                discard()
                render()
                return@launch
            }
            val manager = AppWidgetManager.getInstance(this@WidgetStackActivity)
            var info = withContext(Dispatchers.IO) { manager.getAppWidgetInfo(pendingId) }
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
                    host.startAppWidgetConfigureActivityForResult(this@WidgetStackActivity, pendingId, 0, CONFIGURE, null)
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
                    val limit = if (DeviceTierManager.getInstance(this@WidgetStackActivity).tier == DeviceTier.LOW) 4 else 8
                    require(old.widgetIds.size < limit)
                    state.copy(stacks = state.stacks.filterNot { it.outerId == outerId } + old.copy(widgetIds = (old.widgetIds + id).distinct()))
                }
            }
            pendingId = -1
            render()
        }
    }
    private suspend fun discard() {
        if (pendingId > 0) withContext(Dispatchers.IO) { host.deleteAppWidgetId(pendingId) }
        pendingId = -1
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("outer", outerId)
        outState.putInt("pending", pendingId)
    }
    override fun onDestroy() {
        if (isFinishing && pendingId > 0) {
            val id = pendingId
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runCatching { host.deleteAppWidgetId(id) } }
        }
        super.onDestroy()
    }
    companion object {
        private const val PICK = 10
        private const val BIND = 11
        private const val CONFIGURE = 12
        fun intent(context: Context, id: Int) = Intent(context, WidgetStackActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }
}
