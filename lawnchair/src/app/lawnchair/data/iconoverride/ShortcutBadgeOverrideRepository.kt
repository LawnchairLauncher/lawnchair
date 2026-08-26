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
import kotlinx.coroutines.cancel
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
                .collect { overrides ->
                    val newHidden = overrides.map { it.target }.toSet()
                    val oldHidden = _hiddenBadges
                    _hiddenBadges = newHidden
                    // updateQueue only ever gets entries this process itself wrote via
                    // setHidden - on a fresh start, badges hidden in a previous session arrive
                    // here first, with nothing queued for them, and the icon cache can query
                    // (and cache the badged icon) before this first emission lands at all.
                    // Diffing against the previous snapshot catches both that startup race and
                    // this emission's own writes.
                    ((oldHidden - newHidden) + (newHidden - oldHidden)).forEach { updateQueue.offer(it) }
                    drainUpdateQueue()
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
        // Don't wait for the Flow above to notice this write - it can race with the offer just
        // above and only pick the target up on some later, unrelated emission.
        drainUpdateQueue()
    }

    private fun drainUpdateQueue() {
        while (updateQueue.isNotEmpty()) {
            val target = updateQueue.poll() ?: continue
            // onAppIconChanged is @WorkerThread (blocking ShortcutManager query) - this whole
            // repository otherwise runs on MainScope, so this needs its own IO dispatch.
            scope.launch(Dispatchers.IO) { refreshShortcutIcon(target) }
        }
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
        scope.cancel()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getShortcutBadgeOverrideRepository)
    }
}
