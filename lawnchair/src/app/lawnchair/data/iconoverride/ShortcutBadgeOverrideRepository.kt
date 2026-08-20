package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Tracks which pinned shortcuts have their work/clone-profile badge hidden — see [ShortcutBadgeOverride]. */
@LauncherAppSingleton
class ShortcutBadgeOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("ShortcutBadgeOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).shortcutBadgeOverrideDao()

    @Volatile
    private var _hiddenBadges = setOf<ComponentKey>()
    val hiddenBadges get() = _hiddenBadges

    private val updateQueue = ConcurrentLinkedQueue<ComponentKey>()

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    _hiddenBadges = overrides.map { it.target }.toSet()
                    while (updateQueue.isNotEmpty()) {
                        val target = updateQueue.poll() ?: continue
                        refreshShortcutIcon(target)
                    }
                }
        }
    }

    suspend fun setHidden(target: ComponentKey, hidden: Boolean) {
        if (hidden) {
            dao.insert(ShortcutBadgeOverride(target))
        } else {
            dao.delete(target)
        }
        // Keep the in-memory set in sync before any icon reload — the Room Flow update is async
        // and can race with the refresh below, leaving the badge unchanged for this toggle.
        _hiddenBadges = if (hidden) _hiddenBadges + target else _hiddenBadges - target
        updateQueue.offer(target)
    }

    fun isHidden(target: ComponentKey) = _hiddenBadges.contains(target)

    fun observeHidden(target: ComponentKey) = dao.observeTarget(target).map { it != null }

    suspend fun deleteAll() {
        dao.deleteAll()
        _hiddenBadges = emptySet()
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
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getShortcutBadgeOverrideRepository)
    }
}
