package app.lawnchair.ui.popup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.LawnchairLauncher
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconType
import app.lawnchair.override.CustomizeFolderDialog
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.views.ActivityContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val iconCache = ConcurrentHashMap<Int, Drawable>()

fun showDrawerFolderPopup(folderIcon: FolderIcon) {
    val launcher = try {
        ActivityContext.lookupContext<LawnchairLauncher>(folderIcon.context)
    } catch (e: Exception) {
        return
    }
    val info = folderIcon.mInfo ?: return

    val title = info.title?.toString() ?: ""
    val folderId = info.id
    val icon = loadFolderOverrideIcon(folderIcon.context, folderId)
        ?: captureFolderIconDrawable(folderIcon)
        ?: ColorDrawable(Color.TRANSPARENT)

    AbstractFloatingView.closeAllOpenViews(launcher)
    ComposeBottomSheet.show(
        context = launcher,
        contentPaddings = PaddingValues(bottom = 64.dp),
    ) {
        CustomizeFolderDialog(
            icon = icon,
            defaultTitle = title,
            folderId = folderId,
        ) { close(true) }
    }
}

private fun captureFolderIconDrawable(folderIcon: FolderIcon): Drawable? {
    return try {
        val bounds = android.graphics.Rect()
        folderIcon.getPreviewBounds(bounds)
        if (bounds.isEmpty) return null
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        folderIcon.draw(canvas)
        BitmapDrawable(folderIcon.resources, bitmap)
    } catch (e: Exception) {
        null
    }
}

/**
 * Loads the custom override icon for a drawer folder.
 * Uses an in-memory cache to avoid repeated I/O on the main thread.
 * Called from FolderIcon.java (inflateIcon and onWindowFocusChanged).
 */
fun loadFolderOverrideIcon(context: Context, folderId: Int): Drawable? {
    iconCache[folderId]?.let { return it }

    val prefs = PreferenceManager.getInstance(context)
    val serialized = prefs.folderCustomIcon[folderId] ?: return null

    val drawable = try {
        if (serialized.startsWith("file:")) {
            val file = File(serialized.removePrefix("file:"))
            if (!file.exists()) return null
            BitmapDrawable(context.resources, BitmapFactory.decodeFile(file.absolutePath))
        } else {
            val parts = serialized.split("|", limit = 4)
            if (parts.size < 4) return null
            val entry = IconEntry(parts[0], parts[1], IconType.valueOf(parts[3]))
            IconPackProvider.INSTANCE.get(context)
                .getDrawable(entry, context.resources.configuration.densityDpi, Process.myUserHandle())
        }
    } catch (e: Exception) {
        null
    } ?: return null

    iconCache[folderId] = drawable
    return drawable
}

/** Invalidate cache for a specific folder (called after icon change). */
fun invalidateFolderIconCache(folderId: Int) {
    iconCache.remove(folderId)
}

fun loadFolderOverrideIconAsync(context: Context, folderId: Int, onLoaded: (Drawable?) -> Unit) {
    iconCache[folderId]?.let {
        com.android.launcher3.util.Executors.MAIN_EXECUTOR.execute { onLoaded(it) }
        return
    }
    val prefs = PreferenceManager.getInstance(context)
    if (prefs.folderCustomIcon[folderId] == null) {
        com.android.launcher3.util.Executors.MAIN_EXECUTOR.execute { onLoaded(null) }
        return
    }
    com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR.execute {
        val icon = loadFolderOverrideIcon(context, folderId)
        com.android.launcher3.util.Executors.MAIN_EXECUTOR.execute { onLoaded(icon) }
    }
}

private const val FOLDER_ICONS_DIR = "folder_icons"

fun folderIconFile(context: Context, folderId: Int): File = File(context.filesDir, "$FOLDER_ICONS_DIR/folder_$folderId.png")

private const val MAX_ICON_SIZE = 192

/**
 * Save a gallery image to app-private storage and return the file path.
 * Downscales to [MAX_ICON_SIZE]px to save memory and storage.
 */
fun saveGalleryIconToPrivateStorage(context: Context, folderId: Int, sourceUri: android.net.Uri): String? {
    return try {
        val file = folderIconFile(context, folderId)
        file.parentFile?.mkdirs()
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return null
            val scaled = if (original.width > MAX_ICON_SIZE || original.height > MAX_ICON_SIZE) {
                val scale = MAX_ICON_SIZE.toFloat() / maxOf(original.width, original.height)
                val w = (original.width * scale).toInt()
                val h = (original.height * scale).toInt()
                Bitmap.createScaledBitmap(original, w, h, true).also {
                    if (it !== original) original.recycle()
                }
            } else {
                original
            }
            file.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (scaled !== original) scaled.recycle()
        }
        "file:${file.absolutePath}"
    } catch (e: Exception) {
        null
    }
}

/** Check if folder badge should be visible. Default true. */
fun isFolderBadgeVisible(context: Context, folderId: Int): Boolean {
    val prefs = PreferenceManager.getInstance(context)
    return prefs.folderBadgeHidden[folderId] == null
}

/** Toggle folder badge visibility. */
fun setFolderBadgeVisible(context: Context, folderId: Int, visible: Boolean) {
    val prefs = PreferenceManager.getInstance(context)
    prefs.folderBadgeHidden[folderId] = if (visible) null else "hidden"
}

/** Load the folder badge drawable (ic_folder from lawnchair resources). */
fun loadFolderBadgeDrawable(context: Context): Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_folder)
