package app.lawnchair.curated

import android.content.ContentValues
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes a curated folder to the launcher database. Installed apps become normal entries; the
 * rest become greyed promise icons that carry their source, so tapping one offers F-Droid or
 * Obtainium, and the launcher turns it into the real app once it is installed. Nothing is
 * downloaded or installed here.
 */
class CuratedFolderInstaller(private val context: Context) {

    data class Result(val installed: Int, val notInstalled: Int)

    private val model get() = LauncherAppState.getInstance(context).model
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    suspend fun add(folder: CuratedFolder, container: Int, screen: Int, cellX: Int, cellY: Int): Result {
        val result = withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
            write(folder, container, screen, cellX, cellY)
        }
        withContext(Dispatchers.Main) { model.forceReload() }
        return result
    }

    private fun write(folder: CuratedFolder, container: Int, screen: Int, cellX: Int, cellY: Int): Result {
        val db = model.modelDbController
        val user = Process.myUserHandle()
        val profileId = UserCache.INSTANCE.get(context).getSerialNumberForUser(user)
        var installed = 0
        var notInstalled = 0

        fun insert(values: ContentValues): Int {
            val id = db.generateNewItemId()
            values.put(Favorites._ID, id)
            values.put(Favorites.SPANX, 1)
            values.put(Favorites.SPANY, 1)
            values.put(Favorites.APPWIDGET_ID, -1)
            values.put(Favorites.APPWIDGET_SOURCE, -1)
            values.put(Favorites.OPTIONS, 0)
            values.put(Favorites.PROFILE_ID, profileId)
            return if (db.insert(values) >= 0) id else -1
        }

        db.newTransaction().use { transaction ->
            val folderId = insert(
                ContentValues().apply {
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER)
                    put(Favorites.TITLE, folder.title)
                    put(Favorites.CONTAINER, container)
                    put(Favorites.SCREEN, screen)
                    put(Favorites.CELLX, cellX)
                    put(Favorites.CELLY, cellY)
                    put(Favorites.RANK, 0)
                },
            )
            check(folderId >= 0) { "Could not create folder" }

            var rank = 0
            for (app in folder.apps) {
                val pkg = app.installedPackage(launcherApps)
                if (pkg == null && app.ifInstalled) continue
                val values = ContentValues().apply {
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.CONTAINER, folderId)
                    put(Favorites.SCREEN, 0)
                    put(Favorites.CELLX, 0)
                    put(Favorites.CELLY, 0)
                    put(Favorites.RANK, rank)
                    put(Favorites.TITLE, app.name)
                }
                val activity = pkg?.let { launcherApps.getActivityList(it, user).firstOrNull() }
                if (activity != null) {
                    values.put(Favorites.INTENT, AppInfo.makeLaunchIntent(activity.componentName).toUri(0))
                    installed++
                } else {
                    values.put(Favorites.INTENT, CuratedSource.promiseIntent(app).toUri(0))
                    values.put(Favorites.RESTORED, WorkspaceItemInfo.FLAG_RESTORED_ICON or WorkspaceItemInfo.FLAG_RESTORE_STARTED)
                    values.put(Favorites.ICON, GraphicsUtils.flattenBitmap(letterIcon(app.name)))
                    notInstalled++
                }
                if (insert(values) >= 0) rank++
            }
            transaction.commit()
        }
        return Result(installed, notInstalled)
    }

    /** Initials on a colour derived from the name, until the real icon arrives with the app */
    private fun letterIcon(name: String): Bitmap {
        val size = LauncherAppState.getIDP(context).iconBitmapSize
        val hue = ((name.hashCode() % 360) + 360) % 360f
        val background = Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.85f))
        // Adaptive foregrounds are 108dp with a 72dp safe zone; draw the letters inside it
        val foreground = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(0xDD, 0x10, 0x10, 0x10)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.26f
            isFakeBoldText = true
        }
        val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        Canvas(foreground).drawText(initials, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        val adaptive = AdaptiveIconDrawable(ColorDrawable(background), BitmapDrawable(context.resources, foreground))
        return LauncherIcons.obtain(context).use { it.createBadgedIconBitmap(adaptive).icon }
    }
}
