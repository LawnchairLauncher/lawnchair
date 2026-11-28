package app.lawnchair.override

import android.content.Context
import android.os.Process
import android.util.Log
import android.view.View
import app.lawnchair.folder.FolderIconOverride
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import java.util.WeakHashMap

/**
 * LC: own name and icon for pinned shortcuts on the home screen (e.g. a chat or a contact),
 * like apps have. Both are stored per item and applied whenever the shortcut is shown, so an
 * update of the shortcut by its app does not undo them.
 */
object ShortcutOverrides {

    private const val TAG = "ShortcutOverrides"

    // The shortcut's own name and icon, to go back to when the override is removed.
    private val originals = WeakHashMap<WorkspaceItemInfo, Pair<CharSequence?, BitmapInfo>>()

    // Icon pack icons turned into launcher icons; null when loading failed.
    private val icons = HashMap<FolderIconOverride, BitmapInfo?>()
    private val loading = HashSet<FolderIconOverride>()

    /** Pinned shortcuts on the home screen, in the dock or in a folder. */
    @JvmStatic
    fun isCustomizable(info: ItemInfo?): Boolean = info is WorkspaceItemInfo &&
        info.id != ItemInfo.NO_ID &&
        (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT || info.itemType == Favorites.ITEM_TYPE_SHORTCUT) &&
        (info.container == Favorites.CONTAINER_DESKTOP || info.container == Favorites.CONTAINER_HOTSEAT || info.container >= 0)

    /** The shortcut's name as its app set it, without the user's own name. */
    fun originalTitle(info: WorkspaceItemInfo): String = (originals[info]?.first ?: info.title)?.toString().orEmpty()

    /** Puts the user's name and icon into [info] before [view] shows it. */
    @JvmStatic
    fun apply(view: View, info: WorkspaceItemInfo) {
        if (!isCustomizable(info)) return
        val prefs = PreferenceManager2.getInstance(view.context)
        val title = prefs.getItemTitleCached(info.id)
        val icon = prefs.getFolderIconCached(info.id)
        val original = originals[info]
        if (title == null && icon == null) {
            if (original != null) {
                info.title = original.first
                info.bitmap = original.second
                originals.remove(info)
            }
            return
        }
        val base = original ?: (info.title to info.bitmap).also { originals[info] = it }
        info.title = title ?: base.first
        info.bitmap = when {
            icon == null -> base.second

            icons.containsKey(icon) -> icons[icon] ?: base.second

            else -> {
                load(view, info, icon)
                base.second
            }
        }
    }

    private fun load(view: View, info: WorkspaceItemInfo, icon: FolderIconOverride) {
        if (!loading.add(icon)) return
        val context = view.context.applicationContext
        UI_HELPER_EXECUTOR.execute {
            val bitmap = try {
                createIcon(context, icon)
            } catch (e: Exception) {
                Log.w(TAG, "Could not load icon $icon", e)
                null
            }
            MAIN_EXECUTOR.execute {
                icons[icon] = bitmap
                loading.remove(icon)
                if (view is BubbleTextView && view.tag === info) view.applyFromWorkspaceItem(info)
            }
        }
    }

    private fun createIcon(context: Context, icon: FolderIconOverride): BitmapInfo? {
        val dpi = LauncherAppState.getIDP(context).fillResIconDpi
        val drawable = IconPackProvider.INSTANCE.get(context)
            .getDrawable(icon.toIconPickerItem().toIconEntry(), dpi, Process.myUserHandle()) ?: return null
        return LauncherIcons.obtain(context).use { it.createBadgedIconBitmap(drawable, BaseIconFactory.IconOptions()) }
    }
}
