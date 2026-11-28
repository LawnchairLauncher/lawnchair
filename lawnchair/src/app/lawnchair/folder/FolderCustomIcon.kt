package app.lawnchair.folder

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import android.view.View
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR

/**
 * LC: draws the custom icon of a home screen folder in place of its preview. Icon pack icons are
 * loaded in the background; until one is ready the folder keeps its normal preview.
 */
object FolderCustomIcon {

    private const val TAG = "FolderCustomIcon"

    // Loaded drawables by icon; a null value means loading failed, so it is not retried.
    private val cache = HashMap<FolderIconOverride, Drawable?>()
    private val loading = HashSet<FolderIconOverride>()

    /**
     * Draws the folder's custom icon into [bounds] and returns true, or returns false when the
     * folder has none (or it is still loading) and the normal preview should be drawn.
     */
    @JvmStatic
    fun draw(view: View, itemId: Int, canvas: Canvas, bounds: Rect): Boolean {
        val drawable = get(view, itemId) ?: return false
        drawable.bounds = bounds
        drawable.draw(canvas)
        return true
    }

    private fun get(view: View, itemId: Int): Drawable? {
        if (itemId == ItemInfo.NO_ID) return null
        val context = view.context
        val icon = PreferenceManager2.getInstance(context).getFolderIconCached(itemId) ?: return null
        if (cache.containsKey(icon)) return cache[icon]
        if (loading.add(icon)) {
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
                    cache[icon] = drawable
                    loading.remove(icon)
                    view.invalidate()
                }
            }
        }
        return null
    }
}
