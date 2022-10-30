package app.lawnchair.util

import android.content.pm.PackageManager
import com.android.launcher3.Utilities

fun PackageManager.isPackageInstalled(packageName: String) =
    try {
        getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

fun PackageManager.getPackageVersionCode(packageName: String) =
    try {
        val info = getPackageInfo(packageName, 0)
        when {
            Utilities.ATLEAST_P -> info.longVersionCode
            else -> info.versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        -1L
    }

fun PackageManager.isPackageInstalledAndEnabled(packageName: String) =
    try {
        getApplicationInfo(packageName, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
