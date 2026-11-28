package app.lawnchair.folder

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import java.util.Collections
import java.util.WeakHashMap

/**
 * LC: draws the custom icon of a home screen folder in place of its preview. Icon pack icons are
 * loaded in the background; until one is ready the folder keeps its normal preview. A failed load
 * is retried after a while, e.g. for an icon pack that is installed after a restore.
 */
object FolderCustomIcon {

    private const val TAG = "FolderCustomIcon"

    // A failed load (e.g. the icon pack is not installed yet) is tried again after this time.
    private const val RETRY_MS = 30_000L

    private val cache = HashMap<FolderIconOverride, Drawable>()
    private val failedAt = HashMap<FolderIconOverride, Long>()

    // Views waiting for an icon that is being loaded; all are redrawn when it is ready.
    private val waiting = HashMap<FolderIconOverride, MutableSet<View>>()

    /**
     * Draws the folder's custom icon into [bounds] and returns true, or returns false when the
     * folder has none (or it is still loading) and the normal preview should be drawn.
     */
    @JvmStatic
    fun draw(view: View, itemId: Int, canvas: Canvas, bounds: Rect): Boolean {
        if (bounds.isEmpty) return false
        val drawable = get(view, itemId) ?: return false
        drawable.bounds = bounds
        drawable.draw(canvas)
        return true
    }

    private fun get(view: View, itemId: Int): Drawable? {
        if (itemId == ItemInfo.NO_ID) return null
        val context = view.context
        val icon = PreferenceManager2.getInstance(context).getFolderIconCached(itemId) ?: return null
        cache[icon]?.let { return it }
        failedAt[icon]?.let { if (SystemClock.uptimeMillis() - it < RETRY_MS) return null }

        val views = waiting[icon]
        if (views != null) {
            views += view
            return null
        }
        waiting[icon] = Collections.newSetFromMap(WeakHashMap<View, Boolean>()).apply { add(view) }
        val appContext = context.applicationContext
        UI_HELPER_EXECUTOR.execute {
            val drawable = try {
                val dpi = LauncherAppState.getIDP(appContext).fillResIconDpi
                IconPackProvider.INSTANCE.get(appContext)
                    .getDrawable(icon.toIconPickerItem().toIconEntry(), dpi, Process.myUserHandle())
            } catch (e: Exception) {
                Log.w(TAG, "Could not load folder icon $icon", e)
                null
            }
            MAIN_EXECUTOR.execute {
                if (drawable != null) {
                    cache[icon] = drawable
                    failedAt.remove(icon)
                } else {
                    failedAt[icon] = SystemClock.uptimeMillis()
                }
                waiting.remove(icon)?.forEach { it.invalidate() }
            }
        }
        return null
    }
}
