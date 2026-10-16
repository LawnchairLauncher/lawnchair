package app.lawnchair.omni.experience.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.omni.experience.WidgetStack
import app.lawnchair.util.DeviceTier
import app.lawnchair.util.DeviceTierManager
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetStackActivity : ComponentActivity() {
    private var outerId = -1
    private var pendingId = -1
    private lateinit var host: StackHost
    private lateinit var body: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        outerId = savedInstanceState?.getInt("outer") ?: intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        pendingId = savedInstanceState?.getInt("pending") ?: -1
        host = StackHost(this, outerId)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad + (24 * resources.displayMetrics.density).toInt(), pad, pad)
        }
        setContentView(body)
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { AppWidgetManager.getInstance(this@WidgetStackActivity).getAppWidgetInfo(outerId) }
            if (outerId <= 0 || info?.provider != OmniWidgetStack.component(this@WidgetStackActivity)) {
                finish()
                return@launch
            }
            render()
        }
    }
    private suspend fun render() {
        val state = withContext(Dispatchers.IO) { ExperienceStore.get(this@WidgetStackActivity).read() }
        val stack = state.stacks.firstOrNull { it.outerId == outerId } ?: WidgetStack(outerId)
        body.removeAllViews()
        body.addView(
            TextView(this).apply {
                text = getString(R.string.omni_widget_stacks)
                textSize = 24f
            },
        )
        body.addView(TextView(this).apply { text = getString(R.string.omni_stack_help) })
        stack.widgetIds.forEachIndexed { index, id ->
            val info = withContext(Dispatchers.IO) { AppWidgetManager.getInstance(this@WidgetStackActivity).getAppWidgetInfo(id) }
            val label = withContext(Dispatchers.IO) { info?.loadLabel(packageManager) ?: getString(R.string.omni_action_unavailable) }
            body.addView(
                Button(this).apply {
                    text = getString(R.string.omni_stack_remove, index + 1, label)
                    setOnClickListener {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                host.deleteAppWidgetId(id)
                                ExperienceStore.get(this@WidgetStackActivity).update { current -> current.copy(stacks = current.stacks.map { if (it.outerId == outerId) it.copy(widgetIds = it.widgetIds - id, selected = 0) else it }) }
                            }
                            render()
                        }
                    }
                },
            )
        }
        val limit = if (DeviceTierManager.getInstance(this).tier == DeviceTier.LOW) 4 else 8
        body.addView(
            Button(this).apply {
                text = getString(R.string.omni_stack_add)
                isEnabled = stack.widgetIds.size < limit && pendingId < 0
                setOnClickListener {
                    lifecycleScope.launch {
                        pendingId = withContext(Dispatchers.IO) { host.allocateAppWidgetId() }
                        try {
                            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId), PICK)
                        } catch (_: Exception) {
                            discard()
                            render()
                        }
                    }
                }
            },
        )
        body.addView(
            Button(this).apply {
                text = getString(R.string.omni_done)
                setOnClickListener {
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, outerId))
                    finish()
                }
            },
        )
    }

    @Deprecated("Legacy widget provider configuration contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in setOf(PICK, BIND, CONFIGURE)) return
        lifecycleScope.launch {
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
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { host.deleteAppWidgetId(id) }
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
