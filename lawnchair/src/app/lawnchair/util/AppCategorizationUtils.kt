package app.lawnchair.util

import android.content.Context
import app.lawnchair.flowerpot.Flowerpot
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.PackageManagerHelper

/**
 * Categorizes apps into System Apps, Google Apps, and Flowerpot categories.
 *
 * @param apps List of apps to categorize
 * @param context Context for checking system apps and accessing Flowerpot
 * @return Map of category names to lists of apps in that category
 */
fun categorizeAppsWithSystemAndGoogle(
    apps: List<AppInfo>,
    context: Context,
): Map<String, List<AppInfo>> {
    val systemApps = mutableListOf<AppInfo>()
    val googleApps = mutableListOf<AppInfo>()
    val otherApps = mutableListOf<AppInfo>()

    apps.forEach { app ->
        val packageName = app.targetPackage ?: return@forEach
        val intent = app.intent

        // Check if it's a Google app first (Google apps can also be system apps)
        when {
            packageName.startsWith("com.google.") -> googleApps.add(app)
            intent != null && ApplicationInfoWrapper(context, intent).isSystem() -> systemApps.add(app)
            else -> otherApps.add(app)
        }
    }

    // Use flowerpot to categorize other apps (non-system, non-Google)
    val potsManager = Flowerpot.Manager.getInstance(context)
    val categorizedApps = potsManager.categorizeApps(otherApps)

    // Build final categorized apps map
    val finalCategorizedApps = mutableMapOf<String, List<AppInfo>>()

    if (systemApps.isNotEmpty()) {
        finalCategorizedApps["System Apps"] = systemApps
    }

    if (googleApps.isNotEmpty()) {
        finalCategorizedApps["Google Apps"] = googleApps
    }

    // Add flowerpot categorized apps
    finalCategorizedApps.putAll(categorizedApps)

    return finalCategorizedApps
}
