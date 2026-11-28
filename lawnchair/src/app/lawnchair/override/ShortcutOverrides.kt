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
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * LC: own name and icon for pinned shortcuts on the home screen (e.g. a chat or a contact),
 * like apps have. Both are stored per item and applied whenever the shortcut is shown, so an
 * update of the shortcut by its app does not undo them.
 */
object ShortcutOverrides {

    private const val TAG = "ShortcutOverrides"

    /**
     * The shortcut's own name, icon and accessibility label, to go back to when the override is
     * removed, and the values put into the item last, to notice updates by the shortcut's app.
     */
    private class Original(
        val title: CharSequence?,
        val bitmap: BitmapInfo,
        val contentDescription: CharSequence?,
        val appliedTitle: CharSequence?,
        val appliedBitmap: BitmapInfo,
        val appliedContentDescription: CharSequence?,
    )

    private val originals = WeakHashMap<WorkspaceItemInfo, Original>()

    // Icon pack icons turned into launcher icons; null when loading failed.
    private val icons = HashMap<FolderIconOverride, BitmapInfo?>()

    // Shortcuts waiting for an icon that is being loaded; all are bound again when it is ready.
    private val waiting = HashMap<FolderIconOverride, MutableList<Pair<WeakReference<View>, WorkspaceItemInfo>>>()

    /** Pinned shortcuts on the home screen, in the dock or in a folder. */
    @JvmStatic
    fun isCustomizable(info: ItemInfo?): Boolean = info is WorkspaceItemInfo &&
        info.id != ItemInfo.NO_ID &&
        (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT || info.itemType == Favorites.ITEM_TYPE_SHORTCUT) &&
        (info.container == Favorites.CONTAINER_DESKTOP || info.container == Favorites.CONTAINER_HOTSEAT || info.container >= 0)

    /** The shortcut's name as its app set it, without the user's own name. */
    fun originalTitle(info: WorkspaceItemInfo): String = (currentOriginal(info)?.title ?: info.title)?.toString().orEmpty()

    /**
     * The item's original values. Values in the item that differ from what was put in last were
     * set by the shortcut's app in the meantime (e.g. a new name) and become the new originals.
     */
    private fun currentOriginal(info: WorkspaceItemInfo): Original? {
        val original = originals[info] ?: return null
        return Original(
            title = if (info.title?.toString() != original.appliedTitle?.toString()) info.title else original.title,
            bitmap = if (info.bitmap !== original.appliedBitmap) info.bitmap else original.bitmap,
            contentDescription = if (info.contentDescription?.toString() != original.appliedContentDescription?.toString()) {
                info.contentDescription
            } else {
                original.contentDescription
            },
            appliedTitle = original.appliedTitle,
            appliedBitmap = original.appliedBitmap,
            appliedContentDescription = original.appliedContentDescription,
        )
    }

    /** Puts the user's name and icon into [info] before [view] shows it. */
    @JvmStatic
    fun apply(view: View, info: WorkspaceItemInfo) {
        if (!isCustomizable(info)) return
        val context = view.context
        val prefs = PreferenceManager2.getInstance(context)
        val title = prefs.getItemTitleCached(info.id)
        val icon = prefs.getFolderIconCached(info.id)
        val original = currentOriginal(info)
        if (title == null && icon == null) {
            if (original != null) {
                info.title = original.title
                info.bitmap = original.bitmap
                info.contentDescription = original.contentDescription
                originals.remove(info)
            }
            return
        }
        val baseTitle = original?.title ?: info.title
        val baseBitmap = original?.bitmap ?: info.bitmap
        val baseDescription = original?.contentDescription ?: info.contentDescription
        info.title = title ?: baseTitle
        // Screen readers announce the user's name, still marked with the work profile if needed.
        info.contentDescription = if (title != null) {
            context.packageManager.getUserBadgedLabel(title, info.user)
        } else {
            baseDescription
        }
        info.bitmap = when {
            icon == null -> baseBitmap

            icons.containsKey(icon) -> icons[icon] ?: baseBitmap

            else -> {
                load(view, info, icon)
                baseBitmap
            }
        }
        originals[info] = Original(baseTitle, baseBitmap, baseDescription, info.title, info.bitmap, info.contentDescription)
    }

    private fun load(view: View, info: WorkspaceItemInfo, icon: FolderIconOverride) {
        val binding = WeakReference(view) to info
        waiting[icon]?.let {
            it += binding
            return
        }
        waiting[icon] = mutableListOf(binding)
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
                waiting.remove(icon)?.forEach { (viewRef, item) ->
                    val target = viewRef.get()
                    if (target is BubbleTextView && target.tag === item) target.applyFromWorkspaceItem(item)
                }
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
