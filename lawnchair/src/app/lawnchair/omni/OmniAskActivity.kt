/*
 * Copyright 2026 Abdelrahman Hussein (عبدالرحمن حسين)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.omni

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.omnilink.sdk.OmniLinkConstants
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException

/** User-visible, on-demand handoff. No long-lived connection or work on the Home startup path. */
class OmniAskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmniSearchHandoff.launch(this, queryFrom(intent), "compatibility") {
            if (!isDestroyed) finish()
        }
    }

    companion object {
        private const val EXTRA_QUERY = "omni_query"
        val WORKSPACE_PACKAGES = setOf(
            "com.omnidev.workspace",
            "com.omnidev.workspace.norm",
            "com.omnidev.workspace.pro",
            "com.omnidev.workspace.oem",
            "com.omnidev.workspace.admin",
        )

        /** Exact allowlist and exported/enabled checks are identical for selection and launch. */
        private val discoveryCalls by lazy { BoundedSystemCalls(1, 2, "OmniHandoffIO") }
        suspend fun workspaceTargets(context: Context): List<Pair<String, Intent>> {
            val appContext = context.applicationContext
            return try {
                discoveryCalls.call(3_000L) { discoverWorkspaceTargets(appContext) }
            } catch (timeout: TimeoutCancellationException) {
                throw IOException("Workspace discovery timed out", timeout)
            }
        }

        private fun discoverWorkspaceTargets(context: Context): List<Pair<String, Intent>> {
            @Suppress("DEPRECATION")
            return context.packageManager.queryIntentActivities(
                Intent(OmniLinkConstants.ACTION_PUBLIC_OMNI_REQUEST),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).mapNotNull { resolved ->
                val info = resolved.activityInfo ?: return@mapNotNull null
                if (!info.exported || !info.enabled || !info.applicationInfo.enabled ||
                    info.packageName !in WORKSPACE_PACKAGES
                ) {
                    return@mapNotNull null
                }
                info.loadLabel(context.packageManager).toString() to
                    Intent(OmniLinkConstants.ACTION_PUBLIC_OMNI_REQUEST)
                        .setClassName(info.packageName, info.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }.distinctBy { it.second.component }.sortedBy { it.second.component?.packageName }
        }

        fun queryFrom(intent: Intent): String = intent.getStringExtra(EXTRA_QUERY).orEmpty()

        fun createIntent(context: Context, query: String = ""): Intent = Intent(context, OmniAskActivity::class.java).putExtra(EXTRA_QUERY, query)
    }
}
