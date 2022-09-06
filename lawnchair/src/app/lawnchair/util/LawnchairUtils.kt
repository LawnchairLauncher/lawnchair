/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.os.UserManagerCompat
import androidx.core.view.children
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorTokens
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Themes
import com.android.systemui.shared.system.QuickStepContract
import com.patrykmichalik.opto.core.firstBlocking
import org.json.JSONArray
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess

fun <T, A> ensureOnMainThread(creator: (A) -> T): (A) -> T = { it ->
    if (Looper.myLooper() == Looper.getMainLooper()) {
        creator(it)
    } else {
        try {
            MAIN_EXECUTOR.submit(Callable { creator(it) }).get()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }
    }
}

fun <T> useApplicationContext(creator: (Context) -> T): (Context) -> T = { it ->
    creator(it.applicationContext)
}

fun restartLauncher(context: Context) {
    val pm = context.packageManager
    var intent: Intent? = Intent(Intent.ACTION_MAIN)
    intent!!.addCategory(Intent.CATEGORY_HOME)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val componentName = intent.resolveActivity(pm)
    if (context.packageName != componentName.packageName) {
        intent = pm.getLaunchIntentForPackage(context.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    restartLauncher(context, intent)
}

fun restartLauncher(context: Context, intent: Intent?) {
    context.startActivity(intent)

    // Create a pending intent so the application is restarted after System.exit(0) was called.
    // We use an AlarmManager to call this intent in 100ms
    val mPendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
    val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent

    // Kill the application
    killLauncher()
}

fun killLauncher() {
    exitProcess(0)
}

fun getPrefsIfUnlocked(context: Context): PreferenceManager? {
    return if (UserManagerCompat.isUserUnlocked(context)) {
        PreferenceManager.getInstance(context)
    } else {
        null
    }
}

fun getWindowCornerRadius(context: Context): Float {
    val prefs = getPrefsIfUnlocked(context)
    if (prefs != null && prefs.overrideWindowCornerRadius.get()) {
        return prefs.windowCornerRadius.get().toFloat()
    }
    return QuickStepContract.getWindowCornerRadius(context)
}

fun supportsRoundedCornersOnWindows(context: Context): Boolean {
    if (getPrefsIfUnlocked(context)?.overrideWindowCornerRadius?.get() == true) {
        return true
    }
    return QuickStepContract.supportsRoundedCornersOnWindows(context.resources)
}

fun overrideAllAppsTextColor(textView: TextView) {
    val context = textView.context
    val opacity = PreferenceManager.getInstance(context).drawerOpacity.get()
    if (opacity <= 0.3f) {
        textView.setTextColor(Themes.getAttrColor(context, R.attr.allAppsAlternateTextColor))
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.toArrayList(): ArrayList<T> {
    val arrayList = ArrayList<T>()
    for (i in (0 until length())) {
        arrayList.add(get(i) as T)
    }
    return arrayList
}

val ViewGroup.recursiveChildren: Sequence<View> get() = children.flatMap {
    if (it is ViewGroup) {
        it.recursiveChildren + sequenceOf(it)
    } else sequenceOf(it)
}

private val pendingIntentTagId = Resources.getSystem().getIdentifier("pending_intent_tag", "id", "android")

val View?.pendingIntent get() = this?.getTag(pendingIntentTagId) as? PendingIntent

fun getFolderPreviewAlpha(context: Context): Int {
    val prefs2 = PreferenceManager2.getInstance(context)
    return (prefs2.folderPreviewBackgroundOpacity.firstBlocking() * 255).toInt()
}

fun getAllAppsScrimColor(context: Context): Int {
    val opacity = PreferenceManager.getInstance(context).drawerOpacity.get()
    val scrimColor = ColorTokens.AllAppsScrimColor.resolveColor(context)
    val alpha = (opacity * 255).roundToInt()
    return ColorUtils.setAlphaComponent(scrimColor, alpha)
}

fun Context.checkPackagePermission(packageName: String, permissionName: String): Boolean {
    try {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions.forEachIndexed { index, s ->
            if (s == permissionName) {
                return info.requestedPermissionsFlags[index].hasFlag(REQUESTED_PERMISSION_GRANTED)
            }
        }
    } catch (_: PackageManager.NameNotFoundException) {
    }
    return false
}

fun ContentResolver.getDisplayName(uri: Uri): String? {
    query(uri, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex < 0) return null
        return cursor.getString(columnIndex)
    }
    return null
}

fun Bitmap.scaleDownToDisplaySize(context: Context, keepOriginal: Boolean = false): Bitmap {
    val originalSize = Size(width, height)
    return scaleDownTo(originalSize.scaleDownToDisplaySize(context), keepOriginal)
}

fun Bitmap.scaleDownTo(maxSize: Int, keepOriginal: Boolean = false): Bitmap {
    val originalSize = Size(width, height)
    return scaleDownTo(originalSize.scaleDownTo(maxSize), keepOriginal)
}

fun Bitmap.scaleDownTo(size: Size, keepOriginal: Boolean = false): Bitmap {
    if (size.width > width || size.height > height) return this

    val newBitmap = Bitmap.createScaledBitmap(this, size.width, size.height, true)
    if (newBitmap != this && !keepOriginal) {
        recycle()
    }
    return newBitmap
}

fun Size.scaleDownToDisplaySize(context: Context): Size {
    val metrics = context.resources.displayMetrics
    return scaleDownTo(max(metrics.widthPixels, metrics.heightPixels))
}

fun Size.scaleDownTo(maxSize: Int): Size {
    val width = width
    val height = height

    return when {
        width > height && width > maxSize -> {
            val newHeight = (height * maxSize.toFloat() / width).toInt()
            Size(maxSize, newHeight)
        }
        height > maxSize -> {
            val newWidth = (width * maxSize.toFloat() / height).toInt()
            Size(newWidth, maxSize)
        }
        else -> this
    }
}
