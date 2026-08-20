package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.picker.IconPickerItem
import com.android.launcher3.LauncherAppState
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Per-shortcut icon override repository — see [ShortcutIconOverride] for why
 * this is a separate table/repo from [IconOverrideRepository].
 *
 * Refreshing uses [com.android.launcher3.LauncherModel.onAppIconChanged],
 * NOT `onPackageIconsUpdated` — the latter (`CacheDataUpdatedTask`) only
 * refreshes `ITEM_TYPE_APPLICATION` items and never touches shortcut icons.
 */
@LauncherAppSingleton
class ShortcutIconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("ShortcutIconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).shortcutIconOverrideDao()

    @Volatile
    private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    private val updateQueue = ConcurrentLinkedQueue<ComponentKey>()

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem },
                    )
                    while (updateQueue.isNotEmpty()) {
                        val target = updateQueue.poll() ?: continue
                        refreshShortcutIcon(target)
                    }
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        dao.insert(ShortcutIconOverride(target, item))
        // Keep the in-memory map in sync before any icon reload — the Room Flow update is
        // async and can race with the refresh below, leaving a stale icon cached.
        _overridesMap = _overridesMap + (target to item)
        updateQueue.offer(target)
    }

    suspend fun deleteOverride(target: ComponentKey) {
        dao.delete(target)
        _overridesMap = _overridesMap - target
        updateQueue.offer(target)
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun observeCount() = dao.observeCount()

    /**
     * Returns a persistable fingerprint of this shortcut's icon override, for
     * folding into the icon cache's freshness identifier so clearing an
     * override actually invalidates the cached entry.
     */
    fun getShortcutOverrideState(target: ComponentKey): String {
        val item = overridesMap[target] ?: return ""
        return "${item.packPackageName}/${item.drawableName}/${item.type}"
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        _overridesMap = emptyMap()
        LauncherAppState.getInstance(context).model.reloadIfActive()
    }

    private fun refreshShortcutIcon(target: ComponentKey) {
        val model = LauncherAppState.INSTANCE.get(context).model
        model.onAppIconChanged(target.componentName.packageName, target.user)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getShortcutIconOverrideRepository)
    }
}
