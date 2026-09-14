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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.TextView
import androidx.compose.ui.util.lerp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.luminance
import androidx.core.os.UserManagerCompat
import app.lawnchair.folder.Centered
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.BaseActivity
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.views.ActivityContext
import com.android.systemui.shared.system.QuickStepContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import org.json.JSONArray

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
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val componentName = intent!!.resolveActivity(pm)
    if (context.packageName != componentName.packageName) {
        intent = pm.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    restartLauncher(context, intent)
}

fun restartLauncher(context: Context, intent: Intent?) {
    context.startActivity(intent)

    // Create a pending intent so the application is restarted after System.exit(0) was called.
    // We use an AlarmManager to call this intent in 100ms
    val mPendingIntent =
        PendingIntent.getActivity(context, 0, intent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
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
    return when {
        prefs?.overrideWindowCornerRadius?.get() == true -> prefs.windowCornerRadius.get().toFloat()
        Utilities.ATLEAST_Q -> QuickStepContract.getWindowCornerRadius(context)
        else -> 0.0f
    }
}

fun supportsRoundedCornersOnWindows(context: Context): Boolean {
    val prefs = getPrefsIfUnlocked(context)

    return when {
        prefs?.overrideWindowCornerRadius?.get() == true -> true
        Utilities.ATLEAST_Q -> QuickStepContract.supportsRoundedCornersOnWindows(context.resources)
        else -> false
    }
}

/**
 * Sora: how far the shadow under a drawer label spreads, and how dark it goes.
 *
 * Diffused soft blur (4.5dp) centered with no vertical offset (dy = 0), so
 * labels in the drawer and inside drawer folders stand out with an isotropic,
 * soft halo without sharp downward edges.
 */
const val DRAWER_LABEL_SHADOW_BLUR_DP = 4.5f
const val DRAWER_LABEL_SHADOW_DY_DP = 0f
const val DRAWER_LABEL_SHADOW_COLOR = 0x70000000.toInt()

/**
 * Sora: the app drawer's labels -- one white, with a shadow to hold it up.
 *
 * The same white whatever the theme is doing. The themes carry a drawer text
 * colour each, white against near-black, and the drawer switched between them
 * over pixels that had not changed at all: a label read 255 in light and 198 in
 * dark on the identical frosted wallpaper.
 *
 * Contrast comes from the shadow instead, which is the job the wash used to do
 * without being asked. The drawer is blurred wallpaper rather than a flat
 * surface, so a label has no background colour to take its contrast from -- and
 * a wash laid down to give it one is paint the App Library does not use. A thin
 * shadow costs nothing, holds white apart from a pale wallpaper, and leaves the
 * glass alone.
 */
fun overrideAllAppsTextColor(textView: TextView) {
    textView.setTextColor(Color.WHITE)
    val density = textView.resources.displayMetrics.density
    textView.setShadowLayer(
        DRAWER_LABEL_SHADOW_BLUR_DP * density,
        0f,
        DRAWER_LABEL_SHADOW_DY_DP * density,
        DRAWER_LABEL_SHADOW_COLOR,
    )
}

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.toArrayList(): ArrayList<T> {
    val arrayList = ArrayList<T>()
    for (i in (0 until length())) {
        arrayList.add(get(i) as T)
    }
    return arrayList
}

val kotlinxJson = Json {
    ignoreUnknownKeys = true
}

@SuppressLint("DiscouragedApi")
private val pendingIntentTagId =
    Resources.getSystem().getIdentifier("pending_intent_tag", "id", "android")

val View?.pendingIntent get() = this?.getTag(pendingIntentTagId) as? PendingIntent

fun getFolderPreviewAlpha(context: Context): Int {
    val prefs2 = PreferenceManager2.getInstance(context)
    if (prefs2.folderOpenMode.firstCached() is Centered) {
        return 255
    }
    return (prefs2.folderPreviewBackgroundOpacity.firstCached() * 255).toInt()
}

fun getFolderBackgroundAlpha(context: Context): Int {
    val prefs2 = PreferenceManager2.getInstance(context)
    if (prefs2.folderOpenMode.firstCached() is Centered) {
        return 255
    }
    return (prefs2.folderBackgroundOpacity.firstCached() * 255).toInt()
}

/**
 * Custom folder color from preferences, or `0` when the theme default should be used.
 *
 * Note: pure black (`#FF000000`) is a valid custom color and is not treated as default.
 */
fun getCustomFolderColor(context: Context): Int {
    val prefs2 = PreferenceManager2.getInstance(context)
    if (prefs2.folderOpenMode.firstCached() is Centered) {
        return 0
    }
    return prefs2.folderColor.firstCached().colorPreferenceEntry.lightColor(context)
}

/** Closed-folder preview circle color (includes preview opacity). */
fun resolveFolderPreviewColor(context: Context): Int {
    val custom = getCustomFolderColor(context)
    val base = if (custom != 0) {
        custom
    } else {
        ColorTokens.FolderPreviewColor.resolveColor(context)
    }
    return ColorUtils.setAlphaComponent(base, getFolderPreviewAlpha(context))
}

/**
 * Open-folder background fill color.
 * Opacity is applied separately via [getFolderBackgroundAlpha] on the drawable.
 */
fun resolveFolderBackgroundColor(context: Context): Int {
    val custom = getCustomFolderColor(context)
    return if (custom != 0) {
        custom
    } else {
        ColorTokens.FolderBackgroundColor.resolveColor(context)
    }
}

/** Apply Lawnchair custom allapps colour to the provided colour */
private fun getAllAppsBaseColor(context: Context, defaultColor: Int): Int {
    val prefs2 = PreferenceManager2.getInstance(context)
    val colorOptions: ColorOption = prefs2.appDrawerBackgroundColor.firstCached()
    val color = colorOptions.colorPreferenceEntry.lightColor.invoke(context)
    val baseColor = if (color != 0) color else defaultColor
    return ColorUtils.setAlphaComponent(baseColor, 255)
}

/**
 * Sora: how much flat colour is laid over the drawer's frosted backdrop.
 *
 * None. The drawer is blurred wallpaper and nothing else, which is what iOS
 * does and what every surface floating on it -- folder tile, search pill --
 * is already made of. A wash was tried at 0.5 and it is the thing that made
 * the drawer read as grey: light mode went pale grey, dark mode went charcoal,
 * and the two were nothing like each other. Zero is not a disabled feature
 * here, it is the design; the branches that read this value already have a
 * no-wash path because the text and status-bar contrast have to come from the
 * wallpaper's own luminance once there is no flat colour to read instead.
 */
const val DRAWER_TINT_ALPHA = 0f

/** Apply Lawnchair custom allapps opacity and colour to the provided colour */
fun getAllAppsBackgroundColor(context: Context, defaultColor: Int): Int {
    return ColorUtils.setAlphaComponent(
        getAllAppsBaseColor(context, defaultColor),
        (DRAWER_TINT_ALPHA * 255).roundToInt(),
    )
}

/**
 * Sora: the flat colour the app drawer lays over its frosted backdrop.
 *
 * Fully transparent as things stand -- see [DRAWER_TINT_ALPHA] -- but the value
 * is not the point, having one source of it is. A pane set into the drawer has
 * to wear exactly what the drawer wears, or it reads as a patch cut into the
 * background rather than as part of it; the folder tiles wearing a wash of their
 * own is what made them visibly brighter than the frosting around them. Asking
 * in one place means the two cannot drift apart again.
 */
/**
 * Sora: the wash every pane of glass inside the app drawer wears.
 *
 * The folder tiles, a folder opened out of one, and the search pill all read
 * this, so that one value moves all of them together and none of them can drift
 * apart from the others.
 *
 * A tenth of white: the App Library has the faintest grey-towards-white over its
 * tiles rather than the heavy scrim first tried here. At this weight it is a
 * shade and nothing more -- unlike a ~40% wash, which also flattens the pane's
 * own contrast, so the tiles keep the stretch that vibrancy and the lens put on
 * the scene and stand a little prouder over a bright wallpaper.
 *
 * Whatever it is set to, it must not come from anything that follows the
 * light/dark theme. An earlier version sampled the scrim at the moment a tile
 * attached -- which is the *workspace* scrim, and does follow the theme -- and
 * the tiles changed shade when the theme was switched.
 */
const val DRAWER_GLASS_WASH: Int = 0x1AFFFFFF

fun drawerWash(launcher: Launcher): Int {
    val scrim = launcher.scrimView?.backgroundColor ?: Color.TRANSPARENT
    val sheet = if (launcher.deviceProfile.shouldShowAllAppsOnSheet() && launcher.appsView != null) {
        launcher.appsView.bottomSheetBackgroundColor
    } else {
        Color.TRANSPARENT
    }
    // The sheet is drawn over the scrim, so it composites in that order.
    return ColorUtils.compositeColors(sheet, scrim)
}

fun Context.checkPackagePermission(packageName: String, permissionName: String): Boolean {
    try {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.forEachIndexed { index, s ->
            if (s == permissionName) {
                return info.requestedPermissionsFlags?.get(index)?.hasFlag(REQUESTED_PERMISSION_GRANTED)!!
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

fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

fun Context.isDefaultLauncher(): Boolean = getDefaultLauncherPackageName() == packageName

fun Context.getDefaultLauncherPackageName(): String? = runCatching { getDefaultResolveInfo()?.activityInfo?.packageName }.getOrNull()

fun Context.getDefaultResolveInfo(): ResolveInfo? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
}

/**
 * Parses a version code into [Major, Minor, Stage, Release, Patch].
 * Handles both 8-digit (AA_BB_CC_DD) and 10-digit (AA_BB_CC_DD_EE) formats.
 *
 * Lawnchair format has: [Major, Minor, Stage, Release]
 *
 * pE format has: [Major, Minor, Stage, Release, Patch]
 */
private fun versionParser(version: Long): List<Int> {
    var ver = version

    // If version is less than 1 Billion (1,000,000,000), it is likely the 8-digit format
    // (AA_BB_CC_DD). Multiply by 100 to shift it to 10-digit format equivalent
    // (AA_BB_CC_DD_00), so the math below works for both.
    if (ver < 1_000_000_000L) {
        ver *= 100
    }

    val patch = (ver % 100).toInt() // EE
    val release = ((ver / 100) % 100).toInt() // DD
    val stage = ((ver / 10000) % 100).toInt() // CC
    val minor = ((ver / 1000000) % 100).toInt() // BB
    val major = ((ver / 100000000)).toInt() // AA

    return listOf(major, minor, stage, release, patch)
}

// pE-TODO: Make this actually sensible because the writing is really non-sense after re-reading for fourth time

/**
 * Get both current and APK version for the purpose of comparing them.
 * Returns a [Pair] of (current build version, apk build version) or null if parsing fails.
 */
fun Context.getApkVersionComparison(apkFile: File): Pair<List<Int>, List<Int>>? {
    val pm = packageManager

    val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        ?: return null

    val apkVersionCode = if (Utilities.ATLEAST_P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    val currentVersionCode = if (Utilities.ATLEAST_P) {
        pm.getPackageInfo(packageName, 0).longVersionCode
    } else {
        BuildConfig.VERSION_CODE.toLong()
    }

    val apkParsed = versionParser(apkVersionCode)
    val currentParsed = versionParser(currentVersionCode)

    Log.d("UpdateCheck", "Current: $currentParsed, APK: $apkParsed")

    return Pair(currentParsed, apkParsed)
}

// pE-TODO: Make this actually sensible because the writing is really non-sense after re-reading for fourth time

/**
 * Get current version for the purpose of comparing them.
 * Returns a [Pair] of (current build version, apk build version else null)
 */
fun Context.getApkVersionComparison(): Pair<List<Int>, Nothing?> {
    val pm = packageManager

    val currentVersionCode = if (Utilities.ATLEAST_P) {
        pm.getPackageInfo(packageName, 0).longVersionCode
    } else {
        BuildConfig.VERSION_CODE.toLong()
    }

    val currentParsed = versionParser(currentVersionCode)

    Log.d("UpdateCheck", "Current: $currentParsed")

    return Pair(currentParsed, null)
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }

    val bitmap =
        createBitmap(intrinsicWidth.takeIf { it > 0 } ?: 1, intrinsicHeight.takeIf { it > 0 } ?: 1)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}

fun createRoundedBitmap(color: Int, cornerRadius: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
    }
    val rect = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    return bitmap
}

fun getSignatureHash(context: Context, packageName: String): Long? {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        signatures?.firstOrNull()?.hashCode()?.toLong()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

inline fun <T> listWhileNotNull(generator: () -> T?): List<T> = mutableListOf<T>().apply {
    while (true) {
        add(generator() ?: break)
    }
}

fun String.toTitleCase(): String = splitToSequence(" ")
    .map { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
    .joinToString(" ")

/**
 * Calculate an `inSampleSize` as a power of 2 so that a decoded bitmap stays as small as possible
 * while both dimensions remain >= [reqWidth] / [reqHeight].
 */
fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (rawHeight > reqHeight || rawWidth > reqWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * Decode a bitmap from [path] downsampled to roughly [reqWidth] x [reqHeight] to avoid loading
 * full-resolution images into small views. Returns null if the file cannot be decoded.
 */
fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
    }
    return BitmapFactory.decodeFile(path, options)
}
