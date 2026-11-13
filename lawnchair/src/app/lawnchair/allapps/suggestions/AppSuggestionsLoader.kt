package app.lawnchair.allapps.suggestions

import android.content.Context
import android.os.Process
import androidx.annotation.WorkerThread
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE

/** Apps installed or updated within this long count as new. */
private const val NEW_APP_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

/**
 * The apps of the drawer's suggestion tabs, in tab order, at most [limit] each:
 *  - Frequent: most launched from Lawnchair, recent launches weighing more ([AppLaunchStore]).
 *  - Recent: last launched from Lawnchair.
 *  - New/Updated: installed or updated in the last 30 days, newest first.
 *
 * Only apps of [apps] (the drawer's app list) that belong to the current user, aren't hidden or
 * still installing, and aren't Lawnchair itself. Reads files and the package manager.
 */
@WorkerThread
fun loadAppSuggestions(context: Context, apps: Array<AppInfo>, limit: Int): List<List<AppInfo>> {
    val hiddenApps = PreferenceManager2.getInstance(context).hiddenApps.firstCached()
    val byKey = LinkedHashMap<String, AppInfo>()
    for (app in apps) {
        val key = app.toComponentKey().toString()
        val installing = (app.runtimeStatusFlags and FLAG_INSTALL_SESSION_ACTIVE) != 0
        if (app.user == Process.myUserHandle() && app.targetPackage != context.packageName && !installing && key !in hiddenApps) {
            byKey.putIfAbsent(key, app)
        }
    }

    val nowMs = System.currentTimeMillis()
    val launches = AppLaunchStore.getInstance(context).getLaunches().filterKeys { it in byKey }.entries
    val frequent = launches.sortedWith(
        compareByDescending<Map.Entry<String, AppLaunchStore.Launches>> { it.value.scoreAt(nowMs) }
            .thenByDescending { it.value.lastLaunched },
    )
    val recent = launches.sortedByDescending { it.value.lastLaunched }

    val changedTimes = try {
        context.packageManager.getInstalledPackages(0)
            .associate { it.packageName to maxOf(it.firstInstallTime, it.lastUpdateTime) }
    } catch (_: Exception) {
        emptyMap()
    }
    // One entry per package, even if it has several launcher activities.
    val newApps = byKey.values.distinctBy { it.targetPackage }
        .mapNotNull { app -> changedTimes[app.targetPackage]?.takeIf { nowMs - it <= NEW_APP_MAX_AGE_MS }?.let { app to it } }
        .sortedByDescending { it.second }
        .map { it.first }

    return listOf(frequent.map { byKey.getValue(it.key) }, recent.map { byKey.getValue(it.key) }, newApps)
        .map { it.take(limit) }
}
