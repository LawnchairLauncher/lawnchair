package app.lawnchair.omni.experience

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.provider.MediaStore
import android.provider.Settings
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ActionSearchProvider {
    data class Action(val id: String, val label: Int, val terms: String)
    val actions = listOf(
        Action("shelf", R.string.omni_shelf, "omni shelf رف اومني"),
        Action("new_note", R.string.omni_new_note, "new note create note ملاحظة جديده ملاحظه جديدة"),
        Action("scenes", R.string.omni_scenes, "home scenes مشاهد اوضاع"),
        Action("wifi", R.string.omni_wifi, "wifi wireless واي فاي وايفاي"),
        Action("bluetooth", R.string.omni_bluetooth, "bluetooth بلوتوث"),
        Action("camera", R.string.omni_camera, "take photo camera صورة كاميرا تصوير"),
    )

    @Volatile private var shortcutsVersion = -1L

    @Volatile private var shortcuts = emptyList<android.content.pm.ShortcutInfo>()

    suspend fun search(context: Context, query: String): List<SearchTargetCompat> = withContext(Dispatchers.IO) {
        val normalized = SearchTextNormalizer.normalize(query)
        if (normalized.length < 2) return@withContext emptyList()
        val factory = SearchTargetFactory(context)
        val result = mutableListOf<SearchTargetCompat>()
        actions.filter { action ->
            val terms = SearchTextNormalizer.normalize(context.getString(action.label) + " " + action.terms)
            normalized.split(' ').all { terms.contains(it) }
        }.forEach { action ->
            result.add(factory.createLauncherActionTarget(action.id, context.getString(action.label), OmniActionActivity.intent(context, action.id)))
        }
        val state = ExperienceStore.get(context).read()
        state.scenes.filter { SearchTextNormalizer.normalize(it.name).contains(normalized) }.forEach {
            result.add(factory.createLauncherActionTarget("scene:${it.id}", it.name, OmniActionActivity.intent(context, "scene:${it.id}")))
        }
        val service = context.getSystemService(LauncherApps::class.java)
        if (service?.hasShortcutHostPermission() == true) {
            val inventory = OmniAppInventory.get(context)
            val visibleApps = inventory.visible()
            val visibleKeys = visibleApps.map { it.toComponentKey().toString() }.toSet()
            val visibleProfiles = visibleApps.map { "${it.targetPackage}|${it.user}" }.toSet()
            val revision = inventory.revision.value
            if (shortcutsVersion != revision) {
                val visible = visibleApps.mapNotNull { it.targetPackage }.toSet()
                shortcuts = service.profiles.flatMap { user ->
                    runCatching {
                        service.getShortcuts(
                            LauncherApps.ShortcutQuery().setQueryFlags(
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                            ),
                            user,
                        ).orEmpty()
                    }.getOrDefault(emptyList())
                }.filter { it.`package` in visible && it.isEnabled }.take(300)
                shortcutsVersion = revision
            }
            shortcuts.filter { shortcut ->
                val key = shortcut.activity?.let { com.android.launcher3.util.ComponentKey(it, shortcut.userHandle).toString() }
                (if (key == null) "${shortcut.`package`}|${shortcut.userHandle}" in visibleProfiles else key in visibleKeys) && SearchTextNormalizer.normalize("${shortcut.shortLabel} ${shortcut.longLabel?.toString().orEmpty()}").contains(normalized)
            }.take(5).forEach { result.add(factory.createShortcutTarget(it)) }
        }
        result.take(10)
    }

    fun destination(context: Context, id: String): Intent = when (id) {
        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        "shelf", "new_note", "scenes" -> OmniHubActivity.intent(context, if (id == "scenes") "scenes" else "shelf").putExtra("new_note", id == "new_note")
        else -> throw IllegalArgumentException("Unknown action")
    }
}
