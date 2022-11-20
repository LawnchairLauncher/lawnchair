package app.lawnchair.util

import android.content.pm.PackageManager
import com.android.launcher3.Utilities
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.ComponentName
import android.content.Context
import com.android.launcher3.icons.R

fun PackageManager.isPackageInstalled(packageName: String): Boolean =
    try {
        getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

fun PackageManager.getPackageVersionCode(packageName: String): Long =
    try {
        val info = getPackageInfo(packageName, 0)
        when {
            Utilities.ATLEAST_P -> info.longVersionCode
            else -> info.versionCode.toLong()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        -1L
    }

fun PackageManager.isPackageInstalledAndEnabled(packageName: String) = try {
    getApplicationInfo(packageName, 0).enabled
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun PackageManager.getThemedIconPacksInstalled(context: Context): List<String> =
    try {
        queryIntentActivityOptions(
            ComponentName(context.applicationInfo.packageName, context.applicationInfo.className),
            null,
            Intent(context.resources.getString(R.string.icon_packs_intent_name)),
            PackageManager.GET_RESOLVED_FILTER
        ).map { it.activityInfo.packageName }
    } catch (_: PackageManager.NameNotFoundException) {
        emptyList()
    }
