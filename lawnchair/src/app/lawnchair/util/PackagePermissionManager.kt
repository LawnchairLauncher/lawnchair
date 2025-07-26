package app.lawnchair.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.lawnchair.preferences.PreferenceManager

fun checkAndRequestFilesPermission(context: Context, prefs: PreferenceManager): Boolean {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            if (!Environment.isExternalStorageManager() || !hasReadMediaImagesPermission(context)) {
                if (!Environment.isExternalStorageManager()) {
                    requestManageAllFilesAccessPermission(context)
                }
                if (!hasReadMediaImagesPermission(context)) {
                    requestReadMediaImagesPermission(context)
                }
                prefs.searchResultFiles.set(false)
                return false
            }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            if (!Environment.isExternalStorageManager()) {
                requestManageAllFilesAccessPermission(context)
                prefs.searchResultFiles.set(false)
                return false
            }
        }
        else -> {
            if (!hasReadExternalStoragePermission(context)) {
                requestReadExternalStoragePermission(context)
                return false
            }
        }
    }
    return true
}

fun filesAndStorageGranted(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            Environment.isExternalStorageManager() && hasReadMediaImagesPermission(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> hasReadExternalStoragePermission(context)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun requestManageAllFilesAccessPermission(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.fromParts("package", context.packageName, null)
    context.startActivity(intent)
}

private fun hasReadExternalStoragePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requestReadExternalStoragePermission(context: Context) {
    ActivityCompat.requestPermissions(
        context as Activity,
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
        123,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun hasReadMediaImagesPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_IMAGES,
    ) == PackageManager.PERMISSION_GRANTED
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestReadMediaImagesPermission(context: Context) {
    ActivityCompat.requestPermissions(
        context as Activity,
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
        124,
    )
}
