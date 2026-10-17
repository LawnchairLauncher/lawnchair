package app.lawnchair.omni

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import app.lawnchair.omni.experience.ExperienceStore
import com.android.launcher3.R
import com.omnilink.sdk.OmniJson
import com.omnilink.sdk.OmniLinkConstants
import com.omnilink.sdk.PublicOmniRequest
import com.omnilink.sdk.PublicRequestKind
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString

/** A user tap stays owned by the visible Home window until the external target is launched. */
object OmniSearchHandoff {
    private const val TAG = "OmniSearchHandoff"

    // Main-only, one request/chooser per window. No Activity survives terminal cleanup.
    private val sessions = WeakHashMap<ComponentActivity, Job>()
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(activity: ComponentActivity, query: String = "", origin: String = "search", onComplete: () -> Unit = {}) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (sessions[activity]?.isActive == true) return
        val text = query.trim()
        if (text.length > OmniSearchPolicy.MAX_QUERY_CHARS || activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }
        Log.i(TAG, "requested origin=$origin chars=${text.length}")
        val appContext = activity.applicationContext
        lateinit var job: Job
        job = activity.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            var dialog: AlertDialog? = null
            try {
                // The compatibility Activity may enter here during onCreate. Do not launch an
                // external task before this window has actually resumed on Android 11/OEM builds.
                activity.withResumed { Unit }
                dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.omni_search_provider)
                    .setMessage(R.string.omni_opening_workspace)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> job.cancel() }
                    .setOnCancelListener { job.cancel() }
                    .show()
                val targets = OmniAskActivity.workspaceTargets(appContext)
                val preferred = optionalPreference {
                    ExperienceStore.get(appContext).read().preferredWorkspace
                }
                val json = OmniJson.instance.encodeToString(
                    PublicOmniRequest(
                        kind = if (text.isEmpty()) PublicRequestKind.OPEN_OMNI else PublicRequestKind.ASK_OMNI,
                        text = text.takeIf(String::isNotEmpty),
                    ),
                )
                val preferredTarget = targets.firstOrNull { it.second.component?.flattenToString() == preferred }
                val target = preferredTarget ?: targets.singleOrNull() ?: run {
                    dialog?.dismiss()
                    dialog = null
                    if (targets.isEmpty()) {
                        Toast.makeText(activity, R.string.omni_workspace_required, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    awaitSelection(activity, targets) { dialog = it } ?: return@launch
                }
                currentCoroutineContext().ensureActive()
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                val outgoing = Intent(target.second).putExtra(OmniLinkConstants.EXTRA_PUBLIC_REQUEST_JSON, json)
                activity.startActivity(outgoing)
                Log.i(TAG, "started target=${outgoing.component?.flattenToShortString()}")
                // Remembering a choice must never delay launching its Activity. Persistence is
                // optional and captures application context and a component string only.
                if (preferredTarget == null && targets.size > 1) {
                    val component = target.second.component?.flattenToString()
                    persistence.launch {
                        optionalPreference { ExperienceStore.get(appContext).update { it.copy(preferredWorkspace = component) } }
                    }
                }
            } catch (cancelled: CancellationException) {
                // Also covers independently cancelled provider/store work: never leave an empty
                // transparent Activity over Home after its child coroutine silently terminates.
                Log.i(TAG, "cancelled origin=$origin")
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "handoff failed origin=$origin", error)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val message = if (error is ActivityNotFoundException || error is SecurityException) {
                        R.string.omni_workspace_required
                    } else {
                        R.string.omni_workspace_unavailable
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                try {
                    dialog?.dismiss()
                } finally {
                    if (sessions[activity] === job) sessions.remove(activity)
                    onComplete()
                }
            }
        }
        sessions[activity] = job
        job.start()
    }

    private suspend fun <T> optionalPreference(block: suspend () -> T): T? = try {
        withTimeoutOrNull(400L) { withContext(Dispatchers.IO) { block() } }
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "optional preference operation cancelled")
        null
    } catch (error: Exception) {
        Log.w(TAG, "optional preference operation unavailable", error)
        null
    }

    private suspend fun awaitSelection(
        activity: ComponentActivity,
        targets: List<Pair<String, Intent>>,
        onDialog: (AlertDialog) -> Unit,
    ): Pair<String, Intent>? = suspendCancellableCoroutine { continuation ->
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.omni_choose_workspace)
            .setItems(targets.map { it.first }.toTypedArray()) { _, index ->
                if (continuation.isActive) continuation.resume(targets[index])
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                if (continuation.isActive) continuation.resume(null)
            }
            .setOnCancelListener { if (continuation.isActive) continuation.resume(null) }
            .create()
        onDialog(dialog)
        continuation.invokeOnCancellation { activity.runOnUiThread { dialog.dismiss() } }
        dialog.show()
    }
}
