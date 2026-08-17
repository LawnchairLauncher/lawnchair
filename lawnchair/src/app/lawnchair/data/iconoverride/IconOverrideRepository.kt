package app.lawnchair.data.iconoverride

import android.content.Context
import android.os.UserHandle
import android.util.Log
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
import kotlinx.coroutines.runBlocking

@LauncherAppSingleton
class IconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()

    @Volatile
    private var _overridesMap: Map<ComponentKey, IconPickerItem>? = null

    private val updatePackageQueue = ConcurrentLinkedQueue<ComponentKey>()

    /**
     * The overrides currently in effect.
     *
     * Loaded on first read rather than waiting for [IconOverrideDao.observeAll]. That flow only
     * delivers once the main thread gets a turn, while the icon pipeline reads this from the model
     * worker during the very first load after a cold start — so waiting for it hands out an empty
     * map, which both loads un-overridden icons and computes an override-free freshness id.
     * [com.android.launcher3.icons.cache.IconCacheUpdateHandler] then writes that pair into the
     * icon cache as though it were current, so the lost override survives every later start.
     *
     * The first read runs a database query and blocks. It usually falls to the model worker during
     * the loader, but a UI path can get there first — Customize loads a preview icon straight from
     * its click handler — so this is not safe to annotate as worker-thread only.
     */
    val overridesMap: Map<ComponentKey, IconPickerItem>
        get() = _overridesMap ?: loadOverridesBlocking()

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    publish(overrides.toOverridesMap())
                    while (updatePackageQueue.isNotEmpty()) {
                        val target = updatePackageQueue.poll() ?: continue
                        updatePackageIcons(target)
                    }
                }
        }
    }

    /**
     * Loads the overrides from the database on the calling thread and caches the result, unless a
     * newer map was published while the query was in flight. See [overridesMap] for why the first
     * read cannot wait for the observer.
     */
    private fun loadOverridesBlocking(): Map<ComponentKey, IconPickerItem> {
        val loaded = try {
            runBlocking { dao.getAll() }.toOverridesMap()
        } catch (t: Throwable) {
            // Deliberately not cached: an empty map is the state that corrupts the icon cache, so
            // let the next reader try again instead of settling on it.
            Log.e(TAG, "Unable to load icon overrides", t)
            return emptyMap()
        }
        // The query ran outside the lock, so anything published while it was in flight is newer
        // than this snapshot and wins.
        return synchronized(this) { _overridesMap ?: loaded.also { _overridesMap = it } }
    }

    /** Replaces the map wholesale. Safe before the first read, since it is a complete state. */
    @Synchronized
    private fun publish(overrides: Map<ComponentKey, IconPickerItem>) {
        _overridesMap = overrides
    }

    /**
     * Applies [transform] to an already-loaded map. A map that has not been read yet is left alone:
     * the row is already committed, so the first read picks it up.
     */
    @Synchronized
    private fun patch(
        transform: (Map<ComponentKey, IconPickerItem>) -> Map<ComponentKey, IconPickerItem>,
    ) {
        _overridesMap?.let { _overridesMap = transform(it) }
    }

    /** Rows keyed the way [overridesMap] serves them. */
    private fun List<IconOverride>.toOverridesMap() = associateBy(
        keySelector = { it.target },
        valueTransform = { it.iconPickerItem },
    )

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        dao.insert(IconOverride(target, item))
        // Keep the in-memory map in sync before any icon reload. The Room Flow update is
        // async and can race with onAppIconChanged / forceReload, leaving stale icons cached.
        patch { it + (target to item) }
        updatePackageQueue.offer(target)
    }

    suspend fun deleteOverride(target: ComponentKey) {
        dao.delete(target)
        patch { it - target }
        updatePackageQueue.offer(target)
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
        publish(emptyMap())
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
        private const val TAG = "IconOverrideRepository"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)
    }
}
