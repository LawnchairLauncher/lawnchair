package app.lawnchair.util

import android.content.pm.PackageManager
import com.android.launcher3.Utilities
import app.lawnchair.util.Constants.LAWNCHAIR_INTENT
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ApplicationInfo
import app.lawnchair.util.Constants
import android.content.ComponentName

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
fun PackageManager.getThemedIconPackInstalled(applicationIfo: ApplicationInfo) : List<String> = try {
    val launchables: List<ResolveInfo> = queryIntentActivityOptions(
        ComponentName(
            applicationIfo.packageName,
            applicationIfo.className
        ),
        null,
        Intent(LAWNCHAIR_INTENT),
        PackageManager.GET_RESOLVED_FILTER
    )
      launchables.map { it.activityInfo.packageName }
} catch (_: PackageManager.NameNotFoundException) {
    emptyList()
}
