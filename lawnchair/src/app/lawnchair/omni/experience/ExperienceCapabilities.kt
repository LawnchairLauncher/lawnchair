package app.lawnchair.omni.experience

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.folder.service.FolderService
import com.omnilink.sdk.ActionError
import com.omnilink.sdk.ActionOutcome
import com.omnilink.sdk.ActionRequest
import com.omnilink.sdk.CapabilityDescriptor
import com.omnilink.sdk.CapabilityRisk
import com.omnilink.sdk.CommunicationDirection
import com.omnilink.sdk.IdempotencySemantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object ExperienceCapabilities {
    val readNames = ExperienceCapabilityNames.read
    val controlNames = ExperienceCapabilityNames.control
    val oemNames = ExperienceCapabilityNames.oem
    private val json = Json { ignoreUnknownKeys = false }
    private data class Spec(val name: String, val description: String, val fields: Map<String, String>, val required: List<String> = emptyList())
    private val specs = listOf(
        Spec("launcher.get_layout", "Read paged Home grid/occupied cells and revision. Only visible application icons are movable. Screen IDs and zero-based cells are model coordinates, not RTL presentation positions.", mapOf("offset" to "integer", "limit" to "integer")),
        Spec("launcher.move_items", "Atomically reposition 1-40 existing visible Home app icons on existing screens, including swaps. Read get_layout first; stale revisions and occupied cells are rejected. Widgets/folders/dock remain fixed. Verify with get_layout after reload.", mapOf("expectedRevision" to "string", "moves" to "array"), listOf("expectedRevision", "moves")),
        Spec("launcher.set_grid", "Change Home rows/columns via the standard grid migration (3-10, or up to 20 if locally enabled). Existing icons/widgets can reflow; requires get_layout revision. Verify get_layout after asynchronous reload.", mapOf("expectedRevision" to "string", "rows" to "integer", "columns" to "integer"), listOf("expectedRevision", "rows", "columns")),
        Spec("launcher.create_folder", "Create a drawer rule folder (not a Home icon folder) for a name/package/category rule. Does not move/delete installed apps.", mapOf("title" to "string", "nameContains" to "string", "packagePrefix" to "string", "category" to "integer"), listOf("title")),
        Spec("launcher.list_actions", "Find local launcher actions. Typing or listing never executes them.", mapOf("query" to "string")),
        Spec("launcher.open_action", "Open a known built-in action. Resumed Home required.", mapOf("id" to "string"), listOf("id")),
        Spec("launcher.list_scenes", "List saved Home scenes and their names.", emptyMap()),
        Spec("launcher.save_scene", "Capture the current Home/search appearance as a new named scene.", mapOf("name" to "string"), listOf("name")),
        Spec("launcher.apply_scene", "Apply one existing Home scene. The previous appearance is retained for local undo.", mapOf("id" to "string"), listOf("id")),
        Spec("launcher.list_folders", "List drawer folders and visible app counts.", emptyMap()),
        Spec("launcher.set_folder_rule", "Set rules on an existing drawer folder: nameContains, packagePrefix, category, include and exclude component keys. Hidden apps remain excluded.", mapOf("rule" to "object"), listOf("rule")),
        Spec("launcher.set_alias", "Set comma-separated multilingual aliases for one visible component key.", mapOf("componentKey" to "string", "aliases" to "string"), listOf("componentKey", "aliases")),
        Spec("launcher.shelf.list", "List up to ten Shelf card titles, sources and short text previews; no document URI grants.", emptyMap()),
        Spec("launcher.shelf.publish", "Publish a bounded text card to Shelf. Caller identity is recorded as the source. No remote URI access.", mapOf("id" to "string", "title" to "string", "text" to "string"), listOf("id", "title", "text")),
        Spec("launcher.widget_stacks.list", "List local widget stacks and the selected index.", emptyMap()),
        Spec("launcher.widget_stacks.select", "Select an already bound widget in one stack. Cannot bind or grant new widgets.", mapOf("outerId" to "integer", "index" to "integer"), listOf("outerId", "index")),
        Spec("launcher.oem.status", "Read actual system-app, enterprise-owner and Recents integration state.", emptyMap()),
        Spec("launcher.oem.suspend_app", "Suspend or resume one visible app through an already provisioned device/profile owner. Never grants roles, root or Shizuku.", mapOf("package" to "string", "suspended" to "boolean"), listOf("package", "suspended")),
    )
    val descriptors = specs.map { spec ->
        val mutation = spec.name !in readNames
        CapabilityDescriptor(
            name = spec.name, description = spec.description,
            requiresConfirmation = mutation, risk = if (mutation) CapabilityRisk.MEDIUM else CapabilityRisk.LOW,
            communicationDirection = CommunicationDirection.OMNI_TO_APP_ONLY,
            idempotency = if (spec.name in setOf("launcher.open_action", "launcher.save_scene", "launcher.create_folder")) IdempotencySemantics.NON_IDEMPOTENT else IdempotencySemantics.IDEMPOTENT,
            supportsDryRun = mutation, maxInlinePayloadBytes = 16 * 1024,
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    spec.fields.forEach { (key, type) ->
                        putJsonObject(key) {
                            put("type", type)
                            if (key == "rule") {
                                putJsonObject("properties") {
                                    putJsonObject("folderId") { put("type", "integer") }
                                    listOf("nameContains", "packagePrefix").forEach { field -> putJsonObject(field) { put("type", "string") } }
                                    putJsonObject("category") {
                                        put(
                                            "type",
                                            buildJsonArray {
                                                add(JsonPrimitive("integer"))
                                                add(JsonPrimitive("null"))
                                            },
                                        )
                                    }
                                    listOf("include", "exclude").forEach { field ->
                                        putJsonObject(field) {
                                            put("type", "array")
                                            put("maxItems", 100)
                                            putJsonObject("items") { put("type", "string") }
                                        }
                                    }
                                }
                                put("required", buildJsonArray { add(JsonPrimitive("folderId")) })
                                put("additionalProperties", false)
                            }
                            if (key in setOf("rows", "columns")) {
                                put("minimum", 3)
                                put("maximum", 20)
                            }
                            if (key == "moves") {
                                put("minItems", 1)
                                put("maxItems", 40)
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") { listOf("id", "screenId", "cellX", "cellY").forEach { field -> putJsonObject(field) { put("type", "integer") } } }
                                    put("required", buildJsonArray { listOf("id", "screenId", "cellX", "cellY").forEach { add(JsonPrimitive(it)) } })
                                    put("additionalProperties", false)
                                }
                            }
                        }
                    }
                }
                put("required", buildJsonArray { spec.required.forEach { add(JsonPrimitive(it)) } })
                put("additionalProperties", false)
            },
        )
    }

    suspend fun execute(context: Context, caller: String, request: ActionRequest, authorized: () -> Boolean): ActionOutcome {
        fun checkAccess() {
            check(authorized()) { "control_disabled" }
            check(request.deadlineEpochMs?.let { System.currentTimeMillis() <= it } != false) { "deadline_exceeded" }
        }
        try {
            checkAccess()
            val payload = request.payload as? JsonObject ?: return failure("invalid_payload")
            val spec = specs.firstOrNull { it.name == request.name } ?: return failure("unknown_capability")
            require(payload.keys.all { it in spec.fields } && spec.required.all { it in payload })
            fun text(key: String, max: Int = 160): String {
                val value = (payload[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("invalid_payload")
                require(value.length <= max)
                return value
            }
            fun integer(key: String) = (payload[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("invalid_payload")
            val store = ExperienceStore.get(context)
            val data = when (request.name) {
                "launcher.get_layout", "launcher.move_items", "launcher.set_grid" -> HomeLayoutController.execute(context, request.name, payload, request.dryRun, ::checkAccess)

                "launcher.create_folder" -> {
                    val title = text("title", 80).also { require(it.isNotBlank()) }
                    val name = if ("nameContains" in payload) text("nameContains", 80) else ""
                    val prefix = if ("packagePrefix" in payload) text("packagePrefix", 120) else ""
                    val category = if ("category" in payload) integer("category").also { require(it in -1..8) } else null
                    require(name.isNotBlank() || prefix.isNotBlank() || category != null)
                    val rule = FolderRule(0, name, prefix, category)
                    checkAccess()
                    val id = if (request.dryRun) -1 else FolderService.INSTANCE.get(context).createRuleFolder(title, rule, ::checkAccess)
                    buildJsonObject {
                        put("folderId", id)
                        put("title", title)
                    }
                }

                "launcher.list_actions" -> buildJsonObject {
                    val query = payload["query"]?.let { text("query", 200) }.orEmpty()
                    val normalized = app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer.normalize(query)
                    put(
                        "actions",
                        buildJsonArray {
                            ActionSearchProvider.actions.filter { normalized.isBlank() || app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer.normalize(context.getString(it.label) + " " + it.terms).contains(normalized) }.forEach {
                                add(
                                    buildJsonObject {
                                        put("id", it.id)
                                        put("title", context.getString(it.label))
                                    },
                                )
                            }
                        },
                    )
                }

                "launcher.open_action" -> {
                    val id = text("id")
                    require(ActionSearchProvider.actions.any { it.id == id })
                    withContext(Dispatchers.Main) {
                        checkAccess()
                        val home = LawnchairLauncher.instance
                        check(home?.hasBeenResumed() == true) { "foreground_required" }
                        if (!request.dryRun) home.startActivity(ActionSearchProvider.destination(context, id))
                    }
                    buildJsonObject { put("id", id) }
                }

                "launcher.list_scenes" -> buildJsonObject {
                    put(
                        "scenes",
                        buildJsonArray {
                            store.read().scenes.forEach {
                                add(
                                    buildJsonObject {
                                        put("id", it.id)
                                        put("name", it.name)
                                    },
                                )
                            }
                        },
                    )
                }

                "launcher.save_scene" -> {
                    val name = text("name", 80).also { require(it.isNotBlank()) }
                    val scene = SceneController.capture(context, name)
                    if (!request.dryRun) {
                        store.update {
                            checkAccess()
                            require(it.scenes.size < 20)
                            it.copy(scenes = it.scenes + scene)
                        }
                    }
                    buildJsonObject {
                        put("id", scene.id)
                        put("name", name)
                    }
                }

                "launcher.apply_scene" -> {
                    val id = text("id")
                    require(store.read().scenes.any { it.id == id })
                    checkAccess()
                    if (!request.dryRun) SceneController.apply(context, id, ::checkAccess)
                    buildJsonObject { put("id", id) }
                }

                "launcher.list_folders" -> {
                    val folders = FolderService.INSTANCE.get(context).getAllFolders()
                    buildJsonObject {
                        put(
                            "folders",
                            buildJsonArray {
                                folders.take(100).forEach {
                                    add(
                                        buildJsonObject {
                                            put("id", it.id)
                                            put("title", it.title.toString().take(80))
                                            put("count", it.getContents().size)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }

                "launcher.set_folder_rule" -> {
                    val rule = json.decodeFromJsonElement(FolderRule.serializer(), payload["rule"] ?: error("invalid_rule"))
                    require(rule.nameContains.length <= 80 && rule.packagePrefix.length <= 120 && rule.include.size <= 100 && rule.exclude.size <= 100)
                    require(FolderService.INSTANCE.get(context).getFolderInfo(rule.folderId, true) != null)
                    val visible = OmniAppInventory.get(context).visible().map { it.toComponentKey().toString() }.toSet()
                    require((rule.include + rule.exclude).all { it in visible })
                    if (!request.dryRun) {
                        store.update {
                            checkAccess()
                            it.copy(rules = it.rules.filterNot { r -> r.folderId == rule.folderId } + rule)
                        }
                    }
                    buildJsonObject { put("folderId", rule.folderId) }
                }

                "launcher.set_alias" -> {
                    val key = text("componentKey", 400)
                    val aliases = text("aliases", 200)
                    require(OmniAppInventory.get(context).visible().any { it.toComponentKey().toString() == key })
                    if (!request.dryRun) {
                        store.update {
                            checkAccess()
                            it.copy(aliases = if (aliases.isBlank()) it.aliases - key else it.aliases + (key to aliases))
                        }
                    }
                    buildJsonObject { put("componentKey", key) }
                }

                "launcher.shelf.list" -> buildJsonObject {
                    put(
                        "cards",
                        buildJsonArray {
                            store.read().cards.take(10).forEach {
                                add(
                                    buildJsonObject {
                                        put("id", it.id)
                                        put("title", it.title)
                                        put("source", it.source)
                                        put("preview", it.text.take(200))
                                    },
                                )
                            }
                        },
                    )
                }

                "launcher.shelf.publish" -> {
                    val id = text("id", 80).also { require(it.isNotBlank()) }
                    val card = ShelfCard("$caller:$id", text("title"), text("text", 4000), source = caller)
                    require(card.title.isNotBlank())
                    if (!request.dryRun) {
                        store.update {
                            checkAccess()
                            it.copy(cards = (listOf(card) + it.cards.filterNot { c -> c.id == card.id }).take(40))
                        }
                    }
                    buildJsonObject { put("id", card.id) }
                }

                "launcher.widget_stacks.list" -> buildJsonObject {
                    put(
                        "stacks",
                        buildJsonArray {
                            store.read().stacks.forEach {
                                add(
                                    buildJsonObject {
                                        put("outerId", it.outerId)
                                        put("count", it.widgetIds.size)
                                        put("index", it.selected)
                                    },
                                )
                            }
                        },
                    )
                }

                "launcher.widget_stacks.select" -> {
                    val id = integer("outerId")
                    val index = integer("index")
                    require(store.read().stacks.any { it.outerId == id && index in it.widgetIds.indices })
                    if (!request.dryRun) {
                        store.update { state ->
                            checkAccess()
                            state.copy(
                                stacks = state.stacks.map {
                                    if (it.outerId == id) {
                                        require(index in it.widgetIds.indices)
                                        it.copy(selected = index)
                                    } else {
                                        it
                                    }
                                },
                            )
                        }
                    }
                    buildJsonObject {
                        put("outerId", id)
                        put("index", index)
                    }
                }

                "launcher.oem.status" -> {
                    val state = OemIntegration.status(context)
                    buildJsonObject {
                        put("systemApp", state.systemApp)
                        put("deviceOwner", state.deviceOwner)
                        put("profileOwner", state.profileOwner)
                        put("quickstep", state.quickstep)
                    }
                }

                "launcher.oem.suspend_app" -> {
                    val pkg = text("package", 200)
                    val suspend = (payload["suspended"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("invalid_payload")
                    require(pkg != context.packageName && !pkg.startsWith("com.omnidev.workspace"))
                    require(OmniAppInventory.get(context).visible().any { it.targetPackage == pkg })
                    val status = OemIntegration.status(context)
                    check(status.deviceOwner || status.profileOwner) { "device_owner_required" }
                    checkAccess()
                    val applied = request.dryRun || OemIntegration.suspendApp(context, pkg, suspend)
                    check(applied) { "system_refused" }
                    buildJsonObject {
                        put("package", pkg)
                        put("suspended", suspend)
                    }
                }

                else -> return failure("unknown_capability")
            }
            return ActionOutcome.Success(
                buildJsonObject {
                    data.forEach { (key, value) -> put(key, value) }
                    put("dryRun", request.dryRun)
                },
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            return failure(
                if (error is IllegalArgumentException) {
                    "invalid_payload"
                } else {
                    when (error.message) {
                        "control_disabled", "deadline_exceeded", "foreground_required", "device_owner_required", "system_refused", "stale_layout", "cell_occupied", "item_unavailable", "home_unavailable", "model_loading", "storage_failed" -> error.message!!
                        else -> "operation_unavailable"
                    }
                },
            )
        }
    }
    private fun failure(code: String) = ActionOutcome.Failure(ActionError(code, code))
}
