package app.lawnchair.qsb.providers

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.patrykmichalik.opto.core.first
import java.text.Collator

data object GlobalSearchApp : QsbSearchProvider(
    id = "global_search_app",
    name = R.string.search_provider_other_app,
    icon = R.drawable.ic_qsb_search,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "",
    type = QsbSearchProviderType.LOCAL,
) {

    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        val selectedPackage = PreferenceManager2.getInstance(launcher)
            .hotseatQsbGlobalSearchPackage
            .first()
        val selectedIntent = resolveSearchIntent(launcher, selectedPackage)
        val systemIntent = resolveSystemSearchIntent(launcher)

        listOfNotNull(selectedIntent, systemIntent)
            .distinctBy { it.component }
            .forEach { intent ->
                try {
                    launcher.startActivity(intent)
                    return
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Global search activity is no longer available", e)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Global search activity cannot be launched", e)
                }
            }

        AppSearch.launch(launcher, forceWebsite = false)
    }

    fun queryInstalledApps(
        context: Context,
        excludedPackages: Set<String> = emptySet(),
    ): List<GlobalSearchAppInfo> {
        val packageManager = context.packageManager
        val resolveInfos = packageManager.queryIntentActivities(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val collator = Collator.getInstance()

        return resolveInfos
            .asSequence()
            .filter(::isLaunchable)
            .distinctBy { it.activityInfo.packageName }
            .filterNot { it.activityInfo.packageName in excludedPackages }
            .map { resolveInfo ->
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                GlobalSearchAppInfo(
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                    packageName = applicationInfo.packageName,
                    icon = packageManager.getApplicationIcon(applicationInfo),
                )
            }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
            .toList()
    }

    fun getApplicationLabel(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null

        val resolveInfo = querySearchActivities(context, packageName).firstOrNull() ?: return null
        return context.packageManager
            .getApplicationLabel(resolveInfo.activityInfo.applicationInfo)
            .toString()
    }

    private fun resolveSearchIntent(context: Context, packageName: String): Intent? {
        if (packageName.isBlank()) return null

        val packageManager = context.packageManager
        val packageIntent = Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            .setPackage(packageName)
        val resolvedActivity = packageManager.resolveActivity(
            packageIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.takeIf {
            isLaunchable(it) && it.activityInfo.packageName == packageName
        } ?: querySearchActivities(context, packageName).firstOrNull()

        return resolvedActivity?.activityInfo?.let {
            createLaunchIntent(context, ComponentName(it.packageName, it.name))
        }
    }

    private fun resolveSystemSearchIntent(context: Context): Intent? {
        val searchManager = context.getSystemService(SearchManager::class.java)
        val component = searchManager.globalSearchActivity ?: return null
        return createLaunchIntent(context, component)
            .takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    private fun querySearchActivities(context: Context, packageName: String): List<ResolveInfo> {
        return context.packageManager.queryIntentActivities(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(packageName),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
            .asSequence()
            .filter(::isLaunchable)
            .sortedWith(
                compareByDescending<ResolveInfo> { it.priority }
                    .thenByDescending { it.preferredOrder }
                    .thenByDescending { it.match }
                    .thenBy { it.activityInfo.name },
            )
            .toList()
    }

    private fun createLaunchIntent(context: Context, component: ComponentName): Intent {
        return Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            .setComponent(component)
            .addFlags(INTENT_FLAGS)
            .putExtra(
                SearchManager.APP_DATA,
                Bundle().apply { putString("source", context.packageName) },
            )
    }

    private fun isLaunchable(resolveInfo: ResolveInfo): Boolean {
        return resolveInfo.activityInfo.enabled && resolveInfo.activityInfo.exported
    }

    private const val TAG = "GlobalSearchApp"
}

data class GlobalSearchAppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)
