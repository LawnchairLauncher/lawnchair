package app.lawnchair.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log

class AppInfoHelper(private val context: Context) {

    fun getDefaultPhoneAppInfo(): AppInfo? {
        val phoneIntent = Intent(Intent.ACTION_DIAL)
        return getDefaultAppInfo(phoneIntent)
    }

    fun getDefaultMessageAppInfo(): AppInfo? {
        val messageIntent = Intent(Intent.ACTION_SENDTO)
        messageIntent.data = android.net.Uri.parse("smsto:")
        return getDefaultAppInfo(messageIntent)
    }

    private fun getDefaultAppInfo(intent: Intent): AppInfo? {
        val packageManager: PackageManager = context.packageManager
        val resolveInfo: ResolveInfo? = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return if (resolveInfo != null) {
            val appName: String = resolveInfo.loadLabel(packageManager).toString()
            val appIcon: Drawable = resolveInfo.loadIcon(packageManager)
            val packageName: String = resolveInfo.activityInfo.packageName
            AppInfo(appName, appIcon, packageName)
        } else {
            Log.e("AppInfoHelper", "No default app found for the given intent.")
            null
        }
    }
}

data class AppInfo(val appName: String, val appIcon: Drawable, val packageName: String)
