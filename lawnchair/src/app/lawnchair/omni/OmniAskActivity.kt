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

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.android.launcher3.R
import com.omnilink.sdk.OmniJson
import com.omnilink.sdk.OmniLinkConstants
import com.omnilink.sdk.PublicOmniRequest
import com.omnilink.sdk.PublicRequestKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString

/** User-visible, on-demand handoff. No long-lived connection or work on the Home startup path. */
class OmniAskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent.getStringExtra(EXTRA_QUERY).orEmpty().trim()
        if (query.length > OmniSearchPolicy.MAX_QUERY_CHARS) {
            finish()
            return
        }
        val loading = AlertDialog.Builder(this)
            .setTitle(R.string.omni_search_provider)
            .setMessage(R.string.omni_opening_workspace)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
        lifecycleScope.launch {
            // Keep discovery outside the timeout scope: a slow system Binder call must
            // not prevent the UI deadline or the Cancel button from returning to Home.
            val discovery = lifecycleScope.async(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    Intent(OmniLinkConstants.ACTION_PUBLIC_OMNI_REQUEST),
                    PackageManager.MATCH_DEFAULT_ONLY,
                ).mapNotNull { resolved ->
                    val info = resolved.activityInfo ?: return@mapNotNull null
                    if (!info.exported || !info.enabled || !info.applicationInfo.enabled ||
                        info.packageName !in WORKSPACE_PACKAGES
                    ) {
                        return@mapNotNull null
                    }
                    info.loadLabel(packageManager).toString() to
                        Intent(OmniLinkConstants.ACTION_PUBLIC_OMNI_REQUEST)
                            .setClassName(info.packageName, info.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }.distinctBy { it.second.component }.sortedBy { it.second.component?.packageName }
            }
            val targets = try {
                withTimeoutOrNull(3_000L) { discovery.await() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("OmniAskActivity", "Workspace discovery failed", error)
                null
            } finally {
                discovery.cancel()
                loading.dismiss()
            }
            if (isFinishing || isDestroyed) return@launch
            if (targets == null) {
                Toast.makeText(this@OmniAskActivity, R.string.omni_workspace_unavailable, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val request = PublicOmniRequest(
                kind = if (query.isEmpty()) PublicRequestKind.OPEN_OMNI else PublicRequestKind.ASK_OMNI,
                text = query.takeIf(String::isNotEmpty),
            )
            val json = OmniJson.instance.encodeToString(request)
            fun open(target: Intent) {
                try {
                    startActivity(target.putExtra(OmniLinkConstants.EXTRA_PUBLIC_REQUEST_JSON, json))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@OmniAskActivity, R.string.omni_workspace_required, Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(this@OmniAskActivity, R.string.omni_workspace_required, Toast.LENGTH_LONG).show()
                }
                finish()
            }
            when (targets.size) {
                0 -> AlertDialog.Builder(this@OmniAskActivity)
                    .setTitle(R.string.omni_search_provider)
                    .setMessage(R.string.omni_workspace_required)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()

                1 -> open(targets.single().second)

                else -> AlertDialog.Builder(this@OmniAskActivity)
                    .setTitle(R.string.omni_choose_workspace)
                    .setItems(targets.map { it.first }.toTypedArray()) { _, index -> open(targets[index].second) }
                    .setOnCancelListener { finish() }
                    .show()
            }
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

        fun createIntent(context: Context, query: String = ""): Intent = Intent(context, OmniAskActivity::class.java).putExtra(EXTRA_QUERY, query)
    }
}
