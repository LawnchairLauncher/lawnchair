package app.lawnchair.data.iconoverride

import android.content.Context
import android.os.UserHandle
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.iconpack.LocalIconPack
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

@LauncherAppSingleton
class IconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()

    @Volatile
    private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    private val updatePackageQueue = ConcurrentLinkedQueue<ComponentKey>()

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem },
                    )
                    while (updatePackageQueue.isNotEmpty()) {
                        val target = updatePackageQueue.poll() ?: continue
                        updatePackageIcons(target)
                    }
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        val existing = _overridesMap[target]
        dao.insert(IconOverride(target, item))
        // Keep the in-memory map in sync before any icon reload. The Room Flow update is
        // async and can race with onAppIconChanged / forceReload, leaving stale icons cached.
        _overridesMap = _overridesMap + (target to item)
        updatePackageQueue.offer(target)
        if (existing?.packPackageName == LocalIconPack.PACKAGE_NAME && existing.drawableName != item.drawableName) {
            val oldFile = java.io.File(context.filesDir, "${LocalIconPack.CUSTOM_ICONS_DIR}/${existing.drawableName}")
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }

    suspend fun deleteOverride(target: ComponentKey) {
        val existing = _overridesMap[target]
        dao.delete(target)
        _overridesMap = _overridesMap - target
        updatePackageQueue.offer(target)
        if (existing?.packPackageName == LocalIconPack.PACKAGE_NAME) {
            val oldFile = java.io.File(context.filesDir, "${LocalIconPack.CUSTOM_ICONS_DIR}/${existing.drawableName}")
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun observeCount() = dao.observeCount()

    /**
     * Returns a persistable fingerprint of per-app icon overrides for [packageName]/[user].
     * Used as part of icon-cache freshness so clearing an override invalidates the cache entry.
     */
    fun getPackageOverrideState(packageName: String, user: UserHandle): String {
        return overridesMap.asSequence()
            .filter {
                it.key.componentName.packageName == packageName && it.key.user == user
            }
            .sortedBy { it.key.componentName.className }
            .joinToString(";") { (key, item) ->
                "${key.componentName.className}:${item.packPackageName}/${item.drawableName}/${item.type}"
            }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        val customIconsDir = java.io.File(context.filesDir, LocalIconPack.CUSTOM_ICONS_DIR)
        customIconsDir.deleteRecursively()
        _overridesMap = emptyMap()
        LauncherAppState.getInstance(context).model.reloadIfActive()
    }

    private fun updatePackageIcons(target: ComponentKey) {
        val model = LauncherAppState.INSTANCE.get(context).model

        model.onPackageIconsUpdated(hashSetOf(target.componentName.packageName), target.user)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)
    }
}
