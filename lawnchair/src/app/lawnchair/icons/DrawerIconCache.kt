package app.lawnchair.icons

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairActivityCachingLogic
import app.lawnchair.icons.iconpack.IconPack
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Memory-only store of app-drawer icons baked from a *separate* icon pack
 * ([PreferenceManager.drawerIconPackPackage]), used only while
 * [PreferenceManager.useSeparateDrawerIcons] is enabled.
 *
 * The home/global icon cache ([com.android.launcher3.icons.IconCache], keyed by component with no
 * surface dimension) is deliberately left untouched: home, hotseat, taskbar, folders and search
 * keep the global pack. Drawer surfaces consult this cache at render time and only fall back here
 * when the toggle is on.
 *
 * Icons are baked lazily, one component at a time, off the UI thread, reusing the exact same recipe
 * as the normal cache ([LawnchairActivityCachingLogic.loadIconForPack]) so badging, adaptive
 * shaping, archived and themed layers all match the rest of the launcher. A component that can't be
 * baked (e.g. no longer a launchable activity) is remembered in [negative] so it isn't retried on
 * every rebind. Baking only what is actually rendered keeps memory bounded and lets newly installed
 * apps pick up a drawer icon on their first appearance without any explicit invalidation.
 *
 * Known v1 limitation: a dynamic calendar/clock icon from the drawer pack is baked once and won't
 * roll over at midnight until the drawer pack/toggle changes (the global cache does refresh). This
 * niche combination is intentionally not handled here to avoid a second broadcast receiver.
 */
@LauncherAppSingleton
class DrawerIconCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val prefs = PreferenceManager.getInstance(context)
    private val iconPackProvider = IconPackProvider.INSTANCE.get(context)

    private val memo = ConcurrentHashMap<ComponentKey, BitmapInfo>()
    private val negative = ConcurrentHashMap.newKeySet<ComponentKey>()
    private val pending = ConcurrentHashMap.newKeySet<ComponentKey>()
    private val bakeScheduled = AtomicBoolean(false)

    private val enabled get() = prefs.useSeparateDrawerIcons.get()

    private val drawerPack: IconPack?
        get() = iconPackProvider.getIconPack(prefs.drawerIconPackPackage.get())?.apply { loadBlocking() }

    // Drop the baked drawer icons under memory pressure; they lazily re-bake on next render.
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) clear()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() = clear()
    }

    init {
        context.registerComponentCallbacks(memoryCallbacks)
    }

    /**
     * The drawer-pack [BitmapInfo] for [info], or null when the feature is off, no drawer pack is
     * selected, the item has no launchable component, or it hasn't been baked yet. On a not-yet-baked
     * miss the component is queued for a background bake; the caller should fall back to the item's
     * normal bitmap until the drawer rebinds.
     */
    fun getBitmapInfoOrNull(info: ItemInfoWithIcon): BitmapInfo? {
        if (!enabled || prefs.drawerIconPackPackage.get().isEmpty()) return null
        val component = info.targetComponent ?: return null
        val key = ComponentKey(component, info.user)
        memo[key]?.let { return it }
        if (!negative.contains(key) && pending.add(key)) {
            scheduleBake()
        }
        return null
    }

    private fun scheduleBake() {
        if (!bakeScheduled.compareAndSet(false, true)) return
        Executors.MODEL_EXECUTOR.execute {
            // Allow the next miss to reschedule while this batch runs.
            bakeScheduled.set(false)
            try {
                if (bakePending()) {
                    // Rebind existing callbacks so drawer views re-render from the freshly baked
                    // icons. This re-binds all surfaces from the already-loaded model (no DB
                    // reload); home/workspace icons are unchanged and just repaint identically.
                    LauncherAppState.getInstance(context).model.rebindCallbacks()
                }
            } catch (t: Throwable) {
                // Never let a drawer-icon bake or rebind bring down the launcher process.
                Log.e(TAG, "Failed to bake drawer icons", t)
            }
        }
    }

    /** Bakes the queued components. Returns true if at least one new icon was produced. */
    private fun bakePending(): Boolean {
        val pack = drawerPack ?: run {
            // The selected drawer pack can't be resolved (e.g. it was uninstalled). Blacklist the
            // queued components so we don't reschedule a bake on every rebind; selecting another
            // pack clears the negative set via clear().
            val keys = pending.toList()
            pending.removeAll(keys.toSet())
            negative.addAll(keys)
            return false
        }
        val launcherApps = context.getSystemService<LauncherApps>() ?: return false
        val cache = LauncherAppState.getInstance(context).iconCache
        val cachingLogic = LawnchairActivityCachingLogic.INSTANCE.get(context)
        val keys = pending.toList()
        pending.removeAll(keys.toSet())
        var baked = false
        for (key in keys) {
            if (memo.containsKey(key)) continue
            val activityInfo = resolveActivity(launcherApps, key)
            if (activityInfo == null) {
                negative.add(key)
                continue
            }
            try {
                memo[key] = cachingLogic.loadIconForPack(context, cache, activityInfo, pack)
                baked = true
            } catch (_: Throwable) {
                // This component simply falls back to the normal icon.
                negative.add(key)
            }
        }
        return baked
    }

    private fun resolveActivity(launcherApps: LauncherApps, key: ComponentKey): LauncherActivityInfo? {
        return try {
            launcherApps.getActivityList(key.componentName.packageName, key.user)
                .firstOrNull { it.componentName == key.componentName }
        } catch (_: Exception) {
            null
        }
    }

    /** Drops all baked drawer icons; called when the drawer pack or toggle changes. */
    fun clear() {
        memo.clear()
        negative.clear()
        pending.clear()
    }

    override fun close() {
        context.unregisterComponentCallbacks(memoryCallbacks)
        clear()
    }

    companion object {
        private const val TAG = "DrawerIconCache"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDrawerIconCache)
    }
}
