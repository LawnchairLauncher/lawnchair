package ch.deletescape.lawnchair

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.iconpack.EditIconActivity
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.override.CustomInfoProvider
import com.android.launcher3.AppInfo
import com.android.launcher3.ItemInfo
import com.android.launcher3.ItemInfoWithIcon
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.NexusLauncherActivity

class LawnchairLauncher : NexusLauncherActivity() {

    fun startEditIcon(itemInfo: ItemInfoWithIcon) {
        val component: ComponentKey? = when (itemInfo) {
            is AppInfo -> itemInfo.toComponentKey()
            is ShortcutInfo -> itemInfo.targetComponent?.let { ComponentKey(it, itemInfo.user) }
            else -> null
        }
        currentEditIcon = when (itemInfo) {
            is AppInfo -> IconPackManager.getInstance(this).getEntryForComponent(component!!).drawable
            is ShortcutInfo -> BitmapDrawable(resources, itemInfo.iconBitmap)
            else -> null
        }
        currentEditInfo = itemInfo
        val infoProvider = CustomInfoProvider.forItem<ItemInfo>(this, itemInfo)
        startActivityForResult(EditIconActivity.newIntent(this, infoProvider.getTitle(itemInfo), component), CODE_EDIT_ICON)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CODE_EDIT_ICON && resultCode == Activity.RESULT_OK) {
            val itemInfo = currentEditInfo ?: return
            val entryString = data?.getStringExtra(EditIconActivity.EXTRA_ENTRY)
            val customIconEntry = entryString?.let { IconPackManager.CustomIconEntry.fromString(it) }
            CustomInfoProvider.forItem<ItemInfo>(this, itemInfo).setIcon(itemInfo, customIconEntry)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {

        const val CODE_EDIT_ICON = 100

        var currentEditInfo: ItemInfo? = null
        var currentEditIcon: Drawable? = null

        fun getLauncher(context: Context): LawnchairLauncher {
            return context as? LawnchairLauncher ?: (context as ContextWrapper).baseContext as LawnchairLauncher
        }
    }
}
