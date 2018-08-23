package ch.deletescape.lawnchair

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v4.app.ActivityCompat
import android.util.Log
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.iconpack.EditIconActivity
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.override.CustomInfoProvider
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.*
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.NexusLauncherActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore

open class LawnchairLauncher : NexusLauncherActivity() {

    val gestureController by lazy { GestureController(this) }
    val blurWallpaperProvider by lazy { BlurWallpaperProvider(this, isScreenshotMode) }
    var updateWallpaper = true

    protected open val isScreenshotMode = false
    private var prefCallback = LawnchairPreferencesChangeCallback(this)
    private var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !Utilities.hasStoragePermission(this)) {
            Utilities.requestStoragePermission(this)
        }

        val prefs = Utilities.getLawnchairPrefs(this)

        ThemeManager.getInstance(this).addOverride(
                if (prefs.allAppsSearch) ThemeOverride.LauncherQsb(this) else ThemeOverride.Launcher(this))

        super.onCreate(savedInstanceState)

        Utilities.getLawnchairPrefs(this).registerCallback(prefCallback)
    }

    override fun onResume() {
        super.onResume()

        restartIfPending()

        paused = false

        if (updateWallpaper) {
            updateWallpaper = false
            blurWallpaperProvider.updateAsync()
        }
    }

    override fun onPause() {
        super.onPause()

        paused = true
    }

    open fun restartIfPending() {
        if (sRestart) {
            lawnchairApp.restart(true)
        }
    }

    fun scheduleRestart() {
        if (paused) {
            sRestart = true
        } else {
            Utilities.restartLauncher(this)
        }
    }

    fun refreshGrid() {
        workspace.refreshChildren()
    }

    override fun onDestroy() {
        super.onDestroy()

        Utilities.getLawnchairPrefs(this).unregisterCallback()

        if (sRestart) {
            sRestart = false
            LauncherAppState.destroyInstance()
            LawnchairPreferences.destroyInstance()
        }
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

    override fun onRotationChanged() {
        super.onRotationChanged()
        blurWallpaperProvider.updateAsync()
    }

    fun shouldRecreate() = !sRestart

    class Screenshot : LawnchairLauncher() {

        override val isScreenshotMode = true

        override fun onCreate(savedInstanceState: Bundle?) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            super.onCreate(savedInstanceState)

            findViewById<LauncherRootView>(R.id.launcher).setHideContent(true)
        }

        override fun finishBindingItems() {
            super.finishBindingItems()

            findViewById<LauncherRootView>(R.id.launcher).post(::takeScreenshot)
        }

        private fun takeScreenshot() {
            val rootView = findViewById<LauncherRootView>(R.id.launcher)
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.setHideContent(false)
            rootView.draw(canvas)
            rootView.setHideContent(true)
            val folder = File(filesDir, "tmp")
            folder.mkdirs()
            val file = File(folder, "screenshot.png")
            val out = FileOutputStream(file)
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
                val result = Bundle(1).apply { putString("uri", Uri.fromFile(file).toString()) }
                intent.getParcelableExtra<ResultReceiver>("callback").send(Activity.RESULT_OK, result)
            } catch (e: Exception) {
                out.close()
                intent.getParcelableExtra<ResultReceiver>("callback").send(Activity.RESULT_CANCELED, null)
                e.printStackTrace()
            }
            finish()
        }

        override fun getLauncherTheme(): ThemeOverride {
            return ThemeOverride.LauncherScreenshot(this)
        }

        override fun restartIfPending() {
            sRestart = true
        }

        override fun onDestroy() {
            super.onDestroy()

            sRestart = true
        }
    }

    companion object {

        const val REQUEST_PERMISSION_STORAGE_ACCESS = 666
        const val CODE_EDIT_ICON = 100

        var sRestart = false

        var currentEditInfo: ItemInfo? = null
        var currentEditIcon: Drawable? = null

        fun getLauncher(context: Context): LawnchairLauncher {
            return context as? LawnchairLauncher ?: (context as ContextWrapper).baseContext as LawnchairLauncher
        }

        fun takeScreenshotSync(context: Context): Uri? {
            var uri: Uri? = null
            val waiter = Semaphore(0)
            takeScreenshot(context, uiWorkerHandler) {
                uri = it
                waiter.release()
            }
            waiter.acquireUninterruptibly()
            waiter.release()
            return uri
        }

        fun takeScreenshot(context: Context, handler: Handler = Handler(), callback: (Uri?) -> Unit) {
            context.startActivity(Intent(context, Screenshot::class.java).apply {
                putExtra("screenshot", true)
                putExtra("callback", object : ResultReceiver(handler) {

                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode == Activity.RESULT_OK) {
                            callback(Uri.parse(resultData!!.getString("uri")))
                        } else {
                            callback(null)
                        }
                    }
                })
            })
        }
    }
}
