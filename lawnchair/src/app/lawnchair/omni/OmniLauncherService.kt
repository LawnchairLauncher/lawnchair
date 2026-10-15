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

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lawnchair.LawnchairLauncher
import app.lawnchair.animateToAllApps
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import com.omnilink.sdk.AccessController
import com.omnilink.sdk.AccessDecision
import com.omnilink.sdk.ActionError
import com.omnilink.sdk.ActionOutcome
import com.omnilink.sdk.ActionRequest
import com.omnilink.sdk.AuditLogger
import com.omnilink.sdk.CallerContext
import com.omnilink.sdk.CapabilityDescriptor
import com.omnilink.sdk.CapabilityRisk
import com.omnilink.sdk.CommunicationDirection
import com.omnilink.sdk.ExtensionService
import com.omnilink.sdk.IdempotencySemantics
import com.omnilink.sdk.OmniLinkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Optional, same-signer Workspace control. This service is bound only on explicit discovery. */
class OmniLauncherService : ExtensionService() {
    override val minSupportedVersion = 4
    override val maxSupportedVersion = OmniLinkConstants.CURRENT_PROTOCOL_VERSION
    override val maxInlineRequestBytes = 16 * 1024
    override val maxConcurrentAsyncActions = 2
    private val mutations = Mutex()

    override val accessController = object : AccessController {
        override fun decide(caller: CallerContext, request: ActionRequest): AccessDecision = if (caller.sameSignerAsHost && OmniLauncherAccessPolicy.allows(caller.callingPackage, request.name) &&
            (request.name == "launcher.health" || controlEnabled())
        ) {
            AccessDecision.ALLOW
        } else {
            AccessDecision.DENY
        }
    }

    override val auditLogger = object : AuditLogger {
        override fun log(caller: CallerContext, request: ActionRequest, result: ActionOutcome) {
            // Never log query text, labels, or payloads.
            Log.i("OmniLauncher", "caller=${caller.callingPackage} action=${request.name} result=${result::class.java.simpleName}")
        }
    }

    override val capabilities = listOf(
        capability("launcher.health", "Connection status and whether local control consent is enabled"),
        capability("launcher.get_settings", "Read configured search provider and Omni suggestion options"),
        capability(
            "launcher.list_apps",
            "List visible current-profile launchable apps; offset/limit paging, no hidden apps",
            schema = schema("offset" to "integer", "limit" to "integer"),
        ),
        capability(
            "launcher.set_preference",
            "Set one allowlisted preference: search_provider (provider ID), omni_suggestions or omni_always_suggest (boolean). Consent cannot be changed remotely",
            mutation = true,
            dryRun = true,
            schema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "key",
                            buildJsonObject {
                                put("type", "string")
                                put(
                                    "enum",
                                    buildJsonArray {
                                        add(JsonPrimitive("search_provider"))
                                        add(JsonPrimitive("omni_suggestions"))
                                        add(JsonPrimitive("omni_always_suggest"))
                                    },
                                )
                            },
                        )
                        put(
                            "value",
                            buildJsonObject {
                                put(
                                    "oneOf",
                                    buildJsonArray {
                                        add(buildJsonObject { put("type", "string") })
                                        add(buildJsonObject { put("type", "boolean") })
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("key"))
                        add(JsonPrimitive("value"))
                    },
                )
                put("additionalProperties", false)
            },
        ),
        capability(
            "launcher.open_app",
            "Open an exact visible component from list_apps; Home must be resumed",
            mutation = true,
            dryRun = true,
            schema = schema("component" to "string"),
        ),
        capability("launcher.open_drawer", "Open app drawer only while Home is resumed", mutation = true, dryRun = true),
    )

    override suspend fun onAction(caller: CallerContext, request: ActionRequest): ActionOutcome = withContext(Dispatchers.IO) {
        // Recheck after dispatch: queued calls must not outlive revoked local consent.
        if (accessController.decide(caller, request) != AccessDecision.ALLOW) return@withContext failure("control_disabled")
        val payload = request.payload as? JsonObject ?: return@withContext failure("invalid_payload")
        if (request.name in setOf("launcher.health", "launcher.get_settings", "launcher.open_drawer") && payload.isNotEmpty()) {
            return@withContext failure("invalid_payload")
        }
        if (expired(request)) return@withContext failure("deadline_exceeded")
        when (request.name) {
            "launcher.health" -> success(
                buildJsonObject {
                    put("ready", controlEnabled())
                    put("packageName", packageName)
                    put("protocolVersion", maxSupportedVersion)
                    put("requiresSameSigner", true)
                },
            )

            "launcher.get_settings" -> {
                val store = PreferenceManager2.omniPreferencesDataStore(applicationContext).data.first()
                val prefs = Utilities.getPrefs(this@OmniLauncherService)
                success(
                    buildJsonObject {
                        put("configuredSearchProvider", store[stringPreferencesKey("dock_search_bar_provider")] ?: "default")
                        put("omniSuggestions", prefs.getBoolean("omni_search_suggestions", true))
                        put("omniAlwaysSuggest", prefs.getBoolean("omni_always_suggest", false))
                        put("remoteControl", controlEnabled())
                        put(
                            "searchProviders",
                            buildJsonArray {
                                QsbSearchProvider.values().forEach { add(JsonPrimitive(it.id)) }
                            },
                        )
                    },
                )
            }

            "launcher.list_apps" -> listApps(payload)

            "launcher.set_preference" -> mutations.withLock { setPreference(request, payload) }

            "launcher.open_app" -> openApp(request, payload)

            "launcher.open_drawer" -> withContext(Dispatchers.Main) {
                val launcher = LawnchairLauncher.instance
                when {
                    !controlEnabled() -> failure("control_disabled")

                    expired(request) -> failure("deadline_exceeded")

                    launcher == null || !launcher.hasBeenResumed() -> failure("foreground_required")

                    request.dryRun -> success(buildJsonObject { put("dryRun", true) })

                    else -> {
                        launcher.animateToAllApps()
                        success(buildJsonObject { put("opened", true) })
                    }
                }
            }

            else -> failure("unknown_capability")
        }
    }

    private fun controlEnabled() = Utilities.getPrefs(this).getBoolean("omni_remote_control", false)

    private suspend fun visibleApps(): List<android.content.pm.LauncherActivityInfo>? {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return null
        val hidden = PreferenceManager2.omniPreferencesDataStore(applicationContext).data.first()[
            stringSetPreferencesKey("hidden_apps"),
        ].orEmpty()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { ComponentKey(it.componentName, it.user).toString() !in hidden }
            .sortedBy { it.componentName.flattenToString() }
    }

    private suspend fun listApps(payload: JsonObject): ActionOutcome {
        if (payload.keys.any { it !in setOf("offset", "limit") }) return failure("invalid_payload")
        val offset = if ("offset" in payload) (payload["offset"] as? JsonPrimitive)?.intOrNull ?: return failure("invalid_offset") else 0
        val limit = if ("limit" in payload) (payload["limit"] as? JsonPrimitive)?.intOrNull ?: return failure("invalid_limit") else 20
        if (offset < 0 || limit !in 1..40) return failure("invalid_page")
        val apps = visibleApps() ?: return failure("launcher_service_unavailable")
        if (!controlEnabled()) return failure("control_disabled")
        val page = apps.drop(offset).take(limit)
        return success(
            buildJsonObject {
                put(
                    "apps",
                    buildJsonArray {
                        page.forEach { app ->
                            add(
                                buildJsonObject {
                                    put("component", app.componentName.flattenToString())
                                    put("label", app.label.toString().take(200))
                                },
                            )
                        }
                    },
                )
                put("total", apps.size)
                put("nextOffset", if (offset < apps.size - page.size) offset + page.size else -1)
            },
        )
    }

    private suspend fun setPreference(request: ActionRequest, payload: JsonObject): ActionOutcome {
        if (payload.keys != setOf("key", "value")) return failure("invalid_payload")
        val key = (payload["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return failure("invalid_key")
        val value = payload["value"] as? JsonPrimitive ?: return failure("invalid_value")
        val provider = if (key == "search_provider") {
            if (!value.isString) return failure("invalid_value")
            QsbSearchProvider.values().firstOrNull { it.id == value.content } ?: return failure("unknown_provider")
        } else {
            null
        }
        val boolean = if (key in setOf("omni_suggestions", "omni_always_suggest")) {
            if (value.isString) return failure("invalid_value")
            value.booleanOrNull ?: return failure("invalid_value")
        } else {
            null
        }
        if (provider == null && boolean == null) return failure("preference_not_allowed")
        if (!controlEnabled()) return failure("control_disabled")
        if (expired(request)) return failure("deadline_exceeded")
        if (!request.dryRun) {
            if (provider != null) {
                PreferenceManager2.omniPreferencesDataStore(applicationContext).edit {
                    check(controlEnabled() && !expired(request)) { "Control consent revoked or deadline elapsed" }
                    it[stringPreferencesKey("dock_search_bar_provider")] = provider.id
                }
                withContext(Dispatchers.Main) { LawnchairLauncher.instance?.recreateIfNotScheduled() }
            } else {
                val prefKey = if (key == "omni_suggestions") "omni_search_suggestions" else "omni_always_suggest"
                Utilities.getPrefs(this).edit().putBoolean(prefKey, boolean!!).apply()
            }
        }
        return success(
            buildJsonObject {
                put("key", key)
                put("value", value)
                put("dryRun", request.dryRun)
            },
        )
    }

    private suspend fun openApp(request: ActionRequest, payload: JsonObject): ActionOutcome {
        if (payload.keys != setOf("component")) return failure("invalid_payload")
        val componentText = (payload["component"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return failure("invalid_component")
        val component = ComponentName.unflattenFromString(componentText) ?: return failure("invalid_component")
        val apps = visibleApps() ?: return failure("launcher_service_unavailable")
        if (apps.none { it.componentName == component }) return failure("app_not_visible")
        return withContext(Dispatchers.Main) {
            val launcher = LawnchairLauncher.instance
            when {
                !controlEnabled() -> failure("control_disabled")

                expired(request) -> failure("deadline_exceeded")

                launcher == null || !launcher.hasBeenResumed() -> failure("foreground_required")

                request.dryRun -> success(buildJsonObject { put("dryRun", true) })

                else -> {
                    // LauncherApps is called on IO, while holding a reference to the resumed Home.
                    withContext(Dispatchers.IO) launchOnIo@{
                        if (!controlEnabled()) return@launchOnIo failure("control_disabled")
                        if (expired(request)) return@launchOnIo failure("deadline_exceeded")
                        if (!launcher.hasBeenResumed()) return@launchOnIo failure("foreground_required")
                        val launcherApps = getSystemService(LauncherApps::class.java) ?: return@launchOnIo failure("launcher_service_unavailable")
                        launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
                        success(buildJsonObject { put("opened", component.flattenToString()) })
                    }
                }
            }
        }
    }

    private fun expired(request: ActionRequest) = request.deadlineEpochMs?.let { System.currentTimeMillis() > it } == true

    private fun failure(code: String) = ActionOutcome.Failure(ActionError(code, code))
    private fun success(data: JsonObject) = ActionOutcome.Success(data)

    private fun capability(name: String, description: String, mutation: Boolean = false, dryRun: Boolean = false, schema: JsonObject = schema()) = CapabilityDescriptor(
        name = name, description = description, requiresConfirmation = mutation,
        communicationDirection = CommunicationDirection.OMNI_TO_APP_ONLY,
        risk = if (mutation) CapabilityRisk.MEDIUM else CapabilityRisk.LOW,
        idempotency = if (name.startsWith("launcher.open_")) IdempotencySemantics.NON_IDEMPOTENT else IdempotencySemantics.IDEMPOTENT,
        supportsDryRun = dryRun, maxInlinePayloadBytes = maxInlineRequestBytes,
        inputSchema = schema,
    )

    private fun schema(vararg properties: Pair<String, String>) = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                properties.forEach { (key, type) -> put(key, buildJsonObject { put("type", type) }) }
            },
        )
        if (properties.any { it.first == "component" }) {
            put("required", buildJsonArray { add(JsonPrimitive("component")) })
        }
        put("additionalProperties", false)
    }
}
