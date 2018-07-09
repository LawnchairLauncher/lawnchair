package ch.deletescape.lawnchair

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.iconpack.EditIconActivity
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.override.CustomInfoProvider
import com.android.launcher3.*
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.NexusLauncherActivity

class LawnchairLauncher : NexusLauncherActivity() {

    val gestureController by lazy { GestureController(this) }
    private var prefCallback = LawnchairPreferencesChangeCallback(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !Utilities.hasStoragePermission(this)) {
            Utilities.requestStoragePermission(this)
        }

        super.onCreate(savedInstanceState)

        Utilities.getLawnchairPrefs(this).registerCallback(prefCallback)
    }

    override fun onDestroy() {
        super.onDestroy()

        Utilities.getLawnchairPrefs(this).unregisterCallback()
    }

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
        val infoProvider = CustomInfoProvider.forItem<ItemInfo>(this, itemInfo) ?: return
        startActivityForResult(EditIconActivity.newIntent(this, infoProvider.getTitle(itemInfo), component), CODE_EDIT_ICON)
    }

    override fun onWorkspaceLongPress() {
        gestureController.onLongPress()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CODE_EDIT_ICON && resultCode == Activity.RESULT_OK) {
            val itemInfo = currentEditInfo ?: return
            val entryString = data?.getStringExtra(EditIconActivity.EXTRA_ENTRY)
            val customIconEntry = entryString?.let { IconPackManager.CustomIconEntry.fromString(it) }
            CustomInfoProvider.forItem<ItemInfo>(this, itemInfo)?.setIcon(itemInfo, customIconEntry)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if (requestCode == REQUEST_PERMISSION_STORAGE_ACCESS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)){
                AlertDialog.Builder(this)
                        .setTitle(R.string.title_storage_permission_required)
                        .setMessage(R.string.content_storage_permission_required)
                        .setPositiveButton(android.R.string.ok, { _, _ -> Utilities.requestStoragePermission(this@LawnchairLauncher) })
                        .setCancelable(false)
                        .show()
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        const val REQUEST_PERMISSION_STORAGE_ACCESS = 666
        const val CODE_EDIT_ICON = 100

        var currentEditInfo: ItemInfo? = null
        var currentEditIcon: Drawable? = null

        fun getLauncher(context: Context): LawnchairLauncher {
            return context as? LawnchairLauncher ?: (context as ContextWrapper).baseContext as LawnchairLauncher
        }
    }
}
