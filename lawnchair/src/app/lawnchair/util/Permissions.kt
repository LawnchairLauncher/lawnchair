package app.lawnchair.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

fun Context.openAppPermissionSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri: Uri = Uri.fromParts("package", packageName, null)
    intent.data = uri

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle application details settings intent")
    }
}

@RequiresApi(Build.VERSION_CODES.R)
fun Context.requestManageAllFilesAccessPermission() {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.fromParts("package", packageName, null)

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle application details settings intent")
    }
}
