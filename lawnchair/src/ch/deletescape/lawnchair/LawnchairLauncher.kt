/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v4.app.ActivityCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import ch.deletescape.lawnchair.animations.LawnchairAppTransitionManagerImpl
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.bugreport.BugReportClient
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.iconpack.EditIconActivity
import ch.deletescape.lawnchair.iconpack.IconPackManager
import ch.deletescape.lawnchair.override.CustomInfoProvider
import ch.deletescape.lawnchair.root.RootHelperManager
import ch.deletescape.lawnchair.sensors.BrightnessManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.views.LawnchairBackgroundView
import ch.deletescape.lawnchair.views.OptionsPanel
import com.android.launcher3.*
import com.android.launcher3.uioverrides.OverviewState
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.SystemUiController
import com.android.quickstep.views.LauncherRecentsView
import com.google.android.apps.nexuslauncher.NexusLauncherActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore

open class LawnchairLauncher : NexusLauncherActivity(),
        LawnchairPreferences.OnPreferenceChangeListener,
        ColorEngine.OnColorChangeListener {
    val hideStatusBarKey = "pref_hideStatusBar"
    val gestureController by lazy { GestureController(this) }
    val background by lazy { findViewById<LawnchairBackgroundView>(R.id.lawnchair_background)!! }
    val dummyView by lazy { findViewById<View>(R.id.dummy_view)!! }
    val optionsView by lazy { findViewById<OptionsPanel>(R.id.options_view)!! }

    protected open val isScreenshotMode = false
    private val prefCallback = LawnchairPreferencesChangeCallback(this)
    private var paused = false

    private val customLayoutInflater by lazy {
        LawnchairLayoutInflater(super.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater, this)
    }

    private val colorsToWatch = arrayOf(ColorEngine.Resolvers.WORKSPACE_ICON_LABEL)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !Utilities.hasStoragePermission(this)) {
            Utilities.requestStoragePermission(this)
        }

        IconPackManager.getInstance(this).defaultPack.dynamicClockDrawer

        super.onCreate(savedInstanceState)

        hookGoogleSansDialogTitle()

        lawnchairPrefs.registerCallback(prefCallback)
        lawnchairPrefs.addOnPreferenceChangeListener(hideStatusBarKey, this)

        if (lawnchairPrefs.autoLaunchRoot) {
            RootHelperManager.getInstance(this).run {  }
        }

        ColorEngine.getInstance(this).addColorChangeListeners(this, *colorsToWatch)

        performSignatureVerification()
    }

    override fun startActivitySafely(v: View?, intent: Intent, item: ItemInfo?): Boolean {
        val success = super.startActivitySafely(v, intent, item)
        if (success) {
            (launcherAppTransitionManager as LawnchairAppTransitionManagerImpl)
                    .playLaunchAnimation(this, v, intent)
        }
        return success
    }

    override fun onStart() {
        super.onStart()
        (launcherAppTransitionManager as LawnchairAppTransitionManagerImpl)
                .overrideResumeAnimation(this)
    }

    private fun performSignatureVerification() {
        if (!verifySignature()) {
            val message = "The \"${BuildConfig.FLAVOR_build}\" build flavor is reserved for " +
                    "official Lawnchair distributions only. Please do not use it.\n" +
                    "\n" +
                    "If you're a ROM developer and including Lawnchair in your ROM, please use " +
                    "the official apks provided as a prebuilt or change the package name so that " +
                    "users can still update to official versions if they wish to."
            AlertDialog.Builder(this)
                    .setTitle(R.string.derived_app_name)
                    .setMessage(message)
                    .setPositiveButton(R.string.action_apply) { _, _ -> }
                    .setCancelable(false)
                    .show().applyAccent()
        }
    }

    private fun verifySignature(): Boolean {
        if (!BuildConfig.SIGNATURE_VERIFICATION) return true

        val signatureHash = resources.getInteger(R.integer.lawnchair_signature_hash)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            if (signingInfo.hasMultipleSigners()) return false
            return signingInfo.signingCertificateHistory.any { it.hashCode() == signatureHash }
        } else {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures.forEach {
                if (it.hashCode() != signatureHash) return false
            }
            return info.signatures.isNotEmpty()
        }
    }

    override fun finishBindingItems(currentScreen: Int) {
        super.finishBindingItems(currentScreen)
        Utilities.onLauncherStart()
    }

    override fun onRestart() {
        super.onRestart()
        Utilities.onLauncherStart()
    }

    inline fun prepareDummyView(view: View, crossinline callback: (View) -> Unit) {
        val rect = Rect()
        dragLayer.getViewRectRelativeToSelf(view, rect)
        prepareDummyView(rect.left, rect.top, rect.right, rect.bottom, callback)
    }

    inline fun prepareDummyView(left: Int, top: Int, crossinline callback: (View) -> Unit) {
        val size = resources.getDimensionPixelSize(R.dimen.options_menu_thumb_size)
        val halfSize = size / 2
        prepareDummyView(left - halfSize, top - halfSize, left + halfSize, top + halfSize, callback)
    }

    inline fun prepareDummyView(left: Int, top: Int, right: Int, bottom: Int,
                                crossinline callback: (View) -> Unit) {
        (dummyView.layoutParams as ViewGroup.MarginLayoutParams).let {
            it.width = right - left
            it.height = bottom - top
            it.leftMargin = left
            it.topMargin = top
        }
        dummyView.requestLayout()
        dummyView.post { callback(dummyView) }
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key == hideStatusBarKey) {
            if (prefs.hideStatusBar) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else if (!force) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.WORKSPACE_ICON_LABEL -> {
                systemUiController.updateUiState(SystemUiController.UI_STATE_BASE_WINDOW, resolveInfo.isDark)
            }
        }
    }

    override fun onBackPressed() {
        if (isInState(LauncherState.OVERVIEW) && getOverviewPanel<LauncherRecentsView>().onBackPressed()) {
            // Handled
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()

        restartIfPending()
        // lawnchairPrefs.checkFools()

        BrightnessManager.getInstance(this).startListening()
        BugReportClient.getInstance(this).rebindIfNeeded()

        paused = false
    }

    override fun onPause() {
        super.onPause()

        BrightnessManager.getInstance(this).stopListening()

        paused = true
    }

    open fun restartIfPending() {
        if (sRestart) {
            lawnchairApp.restart(false)
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

        ColorEngine.getInstance(this).removeColorChangeListeners(this, *colorsToWatch)
        Utilities.getLawnchairPrefs(this).unregisterCallback()

        if (sRestart) {
            sRestart = false
            LauncherAppState.destroyInstance()
            LawnchairPreferences.destroyInstance()
        }
    }

    fun startEditIcon(itemInfo: ItemInfo, infoProvider: CustomInfoProvider<ItemInfo>) {
        val component: ComponentKey? = when (itemInfo) {
            is AppInfo -> itemInfo.toComponentKey()
            is ShortcutInfo -> itemInfo.targetComponent?.let { ComponentKey(it, itemInfo.user) }
            is FolderInfo -> itemInfo.toComponentKey()
            else -> null
        }
        currentEditIcon = when (itemInfo) {
            is AppInfo -> IconPackManager.getInstance(this).getEntryForComponent(component!!)?.drawable
            is ShortcutInfo -> BitmapDrawable(resources, itemInfo.iconBitmap)
            is FolderInfo -> itemInfo.getDefaultIcon(this)
            else -> null
        }
        currentEditInfo = itemInfo
        val intent = EditIconActivity.newIntent(this, infoProvider.getTitle(itemInfo), itemInfo is FolderInfo, component)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_CLEAR_TASK
        BlankActivity.startActivityForResult(this, intent, CODE_EDIT_ICON,
                flags) { resultCode, data -> handleEditIconResult(resultCode, data) }
    }

    private fun handleEditIconResult(resultCode: Int, data: Bundle?) {
        if (resultCode == Activity.RESULT_OK) {
            val itemInfo = currentEditInfo ?: return
            val entryString = data?.getString(EditIconActivity.EXTRA_ENTRY)
            val customIconEntry = entryString?.let { IconPackManager.CustomIconEntry.fromString(it) }
            CustomInfoProvider.forItem<ItemInfo>(this, itemInfo)?.setIcon(itemInfo, customIconEntry)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if (requestCode == REQUEST_PERMISSION_STORAGE_ACCESS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)){
                AlertDialog.Builder(this)
                        .setTitle(R.string.title_storage_permission_required)
                        .setMessage(R.string.content_storage_permission_required)
                        .setPositiveButton(android.R.string.ok) { _, _ -> Utilities.requestStoragePermission(this@LawnchairLauncher) }
                        .setCancelable(false)
                        .create().apply {
                            show()
                            applyAccent()
                        }
                }
        } else if(requestCode == REQUEST_PERMISSION_LOCATION_ACCESS) {
            lawnchairApp.smartspace.updateWeatherData()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onRotationChanged() {
        super.onRotationChanged()
        BlurWallpaperProvider.getInstance(this).updateAsync()
    }

    fun getShelfHeight(): Int {
        return if (lawnchairPrefs.showPredictions) {
            val qsbHeight = resources.getDimensionPixelSize(R.dimen.qsb_widget_height)
            (OverviewState.getDefaultSwipeHeight(deviceProfile) + qsbHeight).toInt()
        } else {
            deviceProfile.hotseatBarSizePx
        }
    }

    override fun getSystemService(name: String): Any? {
        if (name == Context.LAYOUT_INFLATER_SERVICE) {
            return customLayoutInflater
        }
        return super.getSystemService(name)
    }

    fun shouldRecreate() = !sRestart

    class Screenshot : LawnchairLauncher() {

        override val isScreenshotMode = true

        override fun onCreate(savedInstanceState: Bundle?) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            super.onCreate(savedInstanceState)

            findViewById<LauncherRootView>(R.id.launcher).setHideContent(true)
        }

        override fun finishBindingItems(currentScreen: Int) {
            super.finishBindingItems(currentScreen)

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

        override fun getLauncherThemeSet(): ThemeOverride.ThemeSet {
            return ThemeOverride.LauncherScreenshot()
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
        const val REQUEST_PERMISSION_LOCATION_ACCESS = 667
        const val REQUEST_PERMISSION_MODIFY_NAVBAR = 668
        const val CODE_EDIT_ICON = 100

        var sRestart = false

        var currentEditInfo: ItemInfo? = null
        var currentEditIcon: Drawable? = null

        @JvmStatic
        fun getLauncher(context: Context): LawnchairLauncher {
            return context as? LawnchairLauncher
                    ?: (context as ContextWrapper).baseContext as? LawnchairLauncher
                    ?: LauncherAppState.getInstance(context).launcher as LawnchairLauncher
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
