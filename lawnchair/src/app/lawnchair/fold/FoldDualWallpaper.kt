package app.lawnchair.fold

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.Executors
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The two images of Lawnchair's dual wallpaper ([FoldDualWallpaperService]), for
 * [FoldWallpaper.EACH] on systems that have no wallpaper per screen (AOSP, Pixel).
 *
 * The images picked in the settings are copied, upright and scaled down, into the app's private
 * files, so they survive process death, a reboot and the original being deleted, and the
 * wallpaper needs no storage permission. Listeners (the wallpaper engines, in this process) are
 * told when an image changes.
 */
object FoldDualWallpaper {

    private const val TAG = "LC-FoldDualWallpaper"

    /** Longest side of a stored image. The engine decodes it at the screen's size anyway. */
    private const val MAX_STORED_SIDE = 3072

    enum class Screen(val fileName: String) {
        COVER("cover.jpg"),
        INNER("inner.jpg"),
        ;

        val other: Screen get() = if (this == COVER) INNER else COVER
    }

    private val listeners = CopyOnWriteArraySet<Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun file(context: Context, screen: Screen): File = File(File(context.filesDir, "fold_wallpaper"), screen.fileName)

    fun hasImage(context: Context, screen: Screen): Boolean = file(context, screen).isFile

    fun component(context: Context) = ComponentName(context, FoldDualWallpaperService::class.java)

    /** Whether the dual wallpaper is the current home wallpaper. */
    fun isActive(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)
    } catch (e: RuntimeException) {
        // What the wallpaper service throws: it is gone (DeadSystemException) or refuses the query
        Log.w(TAG, "can't read the current wallpaper", e)
        false
    }

    /**
     * Shows the dual wallpaper in the system's live wallpaper chooser on foldables only: the
     * service is disabled in the manifest, enabled here on a foldable and set back to the
     * manifest's state elsewhere (e.g. a phone restored from a foldable's backup). The wallpaper
     * in use is never disabled, since that would reset the system wallpaper. Runs off the main
     * thread.
     */
    fun updateServiceEnabled(context: Context, foldable: Boolean) {
        val appContext = context.applicationContext
        Executors.UI_HELPER_EXECUTOR.execute { applyServiceEnabled(appContext, foldable) }
    }

    private fun applyServiceEnabled(context: Context, foldable: Boolean) {
        val state = if (foldable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        try {
            val pm = context.packageManager
            if (pm.getComponentEnabledSetting(component(context)) == state) return
            if (!foldable && isActive(context)) return
            pm.setComponentEnabledSetting(component(context), state, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            Log.w(TAG, "can't update the dual wallpaper service", e)
        }
    }

    /** Opens the system's live wallpaper chooser on the dual wallpaper. */
    fun openChooser(context: Context): Boolean {
        // The settings can be opened before the launcher ever started
        applyServiceEnabled(context, InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in arrayOf(direct, chooser)) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "can't open ${intent.action}", e)
            }
        }
        return false
    }

    /** Copies the image at [uri] for [screen]. Blocking: call it off the main thread. */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setImage(context: Context, screen: Screen, uri: Uri): Boolean {
        val bitmap = try {
            decodeUpright(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "can't read $uri", e)
            null
        } ?: return false
        val target = file(context, screen)
        val temp = File(target.parentFile, "${screen.fileName}.tmp")
        return try {
            target.parentFile?.mkdirs()
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            temp.renameTo(target).also { if (it) notifyChanged() }
        } catch (e: Exception) {
            Log.w(TAG, "can't store the $screen image", e)
            false
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach(Runnable::run) }
    }

    /** Decodes [uri] the right way up (EXIF) and no larger than [MAX_STORED_SIDE]. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_STORED_SIDE) {
                val scale = MAX_STORED_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private const val JPEG_QUALITY = 92
}
