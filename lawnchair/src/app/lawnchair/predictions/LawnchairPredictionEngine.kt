package app.lawnchair.predictions

import android.app.AppOpsManager
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.pm.UserCache

/**
 * Compiles ranked store keys into [AppTarget] lists for all-apps and widget predictions. Holds
 * merging, fallback, and target-building logic, but does **not** hold any system state or stores.
 */
class LawnchairPredictionEngine(
    private val context: Context,
    private val usageStatsRanker: UsageStatsRanker,
) {
    private val userCache = UserCache.INSTANCE.get(context)
    private val prefs2: PreferenceManager2 by lazy { PreferenceManager2.getInstance(context) }

    /**
     * Compiles a ranked list of store keys into resolved [AppTarget] entries, filtering out
     * excluded keys and unresolvable activities.
     *
     * @param ranked Store keys sorted by highest priority.
     * @param count Maximum number of targets to return.
     * @param excludedKeys Store keys that should be skipped (e.g. occupied hotseat slots).
     */
    fun compileAppTargets(
        ranked: List<String>,
        count: Int,
        excludedKeys: Set<String> = emptySet(),
    ): List<AppTarget> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        return buildList(count) {
            ranked.forEach { key ->
                if (size == count) return@buildList

                val parsedKey = parseStoreKey(key) ?: return@forEach
                val componentName = ComponentName(parsedKey.packageName, parsedKey.className)
                val storeKey = toStoreKey(componentName, parsedKey.user)
                if (storeKey in excludedKeys) return@forEach

                try {
                    launcherApps?.resolveActivity(
                        android.content.Intent().setComponent(componentName),
                        parsedKey.user,
                    ) ?: return@forEach
                } catch (_: Exception) {
                    return@forEach
                }

                add(
                    createAppTarget(
                        prefix = "app",
                        componentName = componentName,
                        packageName = parsedKey.packageName,
                        user = parsedKey.user,
                    ),
                )
            }
        }
    }

    /**
     * Compiles a ranked list of store keys into widget [AppTarget] entries by matching packages to
     * available widget providers.
     */
    fun compileWidgetTargets(
        ranked: List<String>,
        dataModel: BgDataModel,
    ): List<AppTarget> {
        val widgetsByPackageAndUser = LinkedHashMap<String, MutableList<WidgetItem>>()
        dataModel.widgetsModel.widgetsByComponentKeyForPicker.values.forEach { widgetItem ->
            if (widgetItem.widgetInfo == null) return@forEach
            val key = packageUserKey(widgetItem.componentName.packageName, widgetItem.user)
            widgetsByPackageAndUser.getOrPut(key) { ArrayList() }.add(widgetItem)
        }

        val targets = ArrayList<AppTarget>(NUM_WIDGET_SUGGESTIONS)
        val addedComponents = HashSet<String>()
        for (rankedKey in ranked) {
            val parsedKey = parseStoreKey(rankedKey) ?: continue
            val packageName = parsedKey.packageName
            val userToken = userToken(parsedKey.user)
            val widget =
                widgetsByPackageAndUser["$packageName/$userToken"]?.firstOrNull() ?: continue
            val componentKey = toStoreKey(widget.componentName, widget.user)
            if (!addedComponents.add(componentKey)) continue

            targets.add(
                createAppTarget(
                    prefix = "widget",
                    componentName = widget.componentName,
                    packageName = widget.componentName.packageName,
                    user = widget.user,
                ),
            )
            if (targets.size == NUM_WIDGET_SUGGESTIONS) break
        }
        return targets
    }

    /**
     * Returns a fallback ranking by merging weighted usage stats (if enabled and permitted) with a
     * randomised activity list.
     */
    fun getFallbackRanked(): List<String> {
        val usageStatsRanked =
            if (shouldUseWeightedUsageStats()) getUsageStatsRanked() else emptyList()
        val randomRanked = getRandomRanked()
        return mergeRanked(usageStatsRanked, randomRanked)
    }

    /**
     * Merges multiple ranked lists into one, preserving order and removing duplicates.
     */
    fun mergeRanked(vararg rankedLists: List<String>): List<String> = buildList {
        val seen = LinkedHashSet<String>()
        rankedLists.forEach { ranked ->
            ranked.forEach { key ->
                if (key !in seen) {
                    seen.add(key)
                    add(key)
                }
            }
        }
    }

    /**
     * Creates a store key from a [ComponentName] and [UserHandle].
     */
    fun toStoreKey(componentName: ComponentName, user: UserHandle): String = PredictionAppKey.create(componentName, userToken(user))

    /**
     * Returns the set of store keys for items currently occupying the hotseat.
     */
    fun getOccupiedHotseatItems(dataModel: BgDataModel): Set<String> {
        val itemsSnapshot = synchronized(dataModel) { dataModel.itemsIdMap.copy() }
        return itemsSnapshot
            .filter { it.container == CONTAINER_HOTSEAT }
            .mapNotNull { item ->
                item.targetComponent?.let { component ->
                    toStoreKey(component, item.user)
                }
            }
            .toSet()
    }

    private fun shouldUseWeightedUsageStats(): Boolean {
        if (!prefs2.lawnchairPredictorUseWeightedUsageStats.firstCached()) return false
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun getUsageStatsRanked(): List<String> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val appFilter = AppFilter(context)
        return usageStatsRanker.getUsageStatsRanked(
            launcherApps = launcherApps,
            appFilter = appFilter,
            resolveStoreKey = ::resolvePackageToStoreKey,
        )
    }

    private fun getRandomRanked(): List<String> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val appFilter = AppFilter(context)
        return userProfilesInPredictionOrder()
            .asSequence()
            .flatMap { user -> launcherApps.getActivityList(null, user).asSequence() }
            .filter { activityInfo -> appFilter.shouldShowApp(activityInfo.componentName) }
            .map { activityInfo -> toStoreKey(activityInfo.componentName, activityInfo.user) }
            .distinct()
            .toList()
            .shuffled()
    }

    private fun resolvePackageToStoreKey(
        packageName: String,
        launcherApps: LauncherApps,
        appFilter: AppFilter,
    ): String? {
        userProfilesInPredictionOrder().forEach { user ->
            val activityInfo = launcherApps.getActivityList(packageName, user)
                .firstOrNull { info -> appFilter.shouldShowApp(info.componentName) }
                ?: return@forEach
            return toStoreKey(activityInfo.componentName, activityInfo.user)
        }
        return null
    }

    private fun userProfilesInPredictionOrder(): List<UserHandle> {
        val currentUser = Process.myUserHandle()
        return buildList {
            if (currentUser in userCache.userProfiles) add(currentUser)
            userCache.userProfiles.forEach { user ->
                if (user !in this) add(user)
            }
        }
    }

    private fun userToken(user: UserHandle): String = userCache.getSerialNumberForUser(user).toString()

    private fun packageUserKey(packageName: String, user: UserHandle): String = "$packageName/${userToken(user)}"

    private fun parseStoreKey(key: String): ParsedStoreKey? {
        val parts = PredictionAppKey.parse(key) ?: return null
        val user = resolveUserToken(parts.userToken) ?: return null
        return ParsedStoreKey(parts.packageName, parts.className, user)
    }

    private fun resolveUserToken(token: String): UserHandle? {
        token.toLongOrNull()?.let { serialNumber ->
            val user = userCache.getUserForSerialNumber(serialNumber)
            if (user != null && userToken(user) == token) return user
        }

        return userCache.userProfiles.firstOrNull { profile ->
            profile.hashCode().toString() == token
        }
    }

    private fun createAppTarget(
        prefix: String,
        componentName: ComponentName,
        packageName: String,
        user: UserHandle,
    ): AppTarget {
        val storeKey = toStoreKey(componentName, user)
        return AppTarget.Builder(
            AppTargetId("$prefix:$storeKey"),
            packageName,
            user,
        ).setClassName(componentName.className).build()
    }

    private data class ParsedStoreKey(
        val packageName: String,
        val className: String,
        val user: UserHandle,
    )

    companion object {
        private const val NUM_WIDGET_SUGGESTIONS = 20
    }
}
