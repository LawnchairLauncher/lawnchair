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

package ch.deletescape.lawnchair.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.View
import android.widget.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.provider.RestoreDbTask

class RestoreBackupFileActivity : SettingsBaseActivity(), LawnchairBackupCustom.MetaLoader.Callback, ColorEngine.OnColorChangeListener {

    private var backupWallpaper = true
    private var backupHomescreen = true
    private var backupSettings = true


    private val backup by lazy {
        if (intent.hasExtra(EXTRA_PATH)) {
            LawnchairBackupCustom(this, intent.getStringExtra(EXTRA_PATH))
        }
        else
            LawnchairBackupCustom(this, "")
    }
    private val backupMetaLoader by lazy { LawnchairBackupCustom.MetaLoader(backup) }

    private val progress by lazy { findViewById<View>(R.id.progress_custom) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progressBar_custom) }
    private val progressText by lazy { findViewById<TextView>(R.id.progress_text_custom) }
    private val successIcon by lazy { findViewById<ImageView>(R.id.success_icon_custom) }

    private var fromExternal = false

    private var inProgress = false
        set(value) {
            if (value) {
                supportActionBar?.setDisplayShowHomeEnabled(false)
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            } else {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
            field = value
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore_custom_backup)

        when {
            intent.hasExtra(EXTRA_PATH) -> {  }
            intent.data != null -> { fromExternal = true }
        }

        loadMeta()

        startRestore()
    }

    fun loadMeta() {
        backupMetaLoader.callback = this
        backupMetaLoader.loadMeta()

        progress.visibility = View.VISIBLE
        progressText.visibility = View.GONE
    }

    override fun onMetaLoaded() {
        backupMetaLoader.callback = null
        if (backup.meta != null) {
            val contents = backup.meta!!.contents
            val includeHomescreen = contents and LawnchairBackup.INCLUDE_HOMESCREEN != 0
            backupHomescreen = includeHomescreen
            val includeSettings = contents and LawnchairBackup.INCLUDE_SETTINGS != 0
            backupSettings = includeSettings
            val includeWallpaper = contents and LawnchairBackup.INCLUDE_WALLPAPER != 0
            backupWallpaper = includeWallpaper
        } else {
            showMessage(R.drawable.ic_close, R.string.backup_invalid)
        }
    }

    private fun validateOptions(): Int {
        return if (!backupHomescreen && !backupSettings && !backupWallpaper) {
            R.string.backup_error_blank_contents
        } else {
            0
        }
    }

    private fun startRestore() {
        val error = validateOptions()
        if (error == 0) {
            RestoreBackupTask(this).execute()
        } else {
            Snackbar.make(findViewById(R.id.content), error, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (!inProgress) super.onBackPressed()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class RestoreBackupTask(val context: Context) : AsyncTask<Void, Void, Int>() {

        override fun onPreExecute() {
            super.onPreExecute()

            progress.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE

            inProgress = true
        }

        override fun doInBackground(vararg params: Void?): Int {
            var contents = 0
            if (backupHomescreen) {
                contents = contents or LawnchairBackup.INCLUDE_HOMESCREEN
            }
            if (backupSettings) {
                contents = contents or LawnchairBackup.INCLUDE_SETTINGS
            }
            if (backupWallpaper) {
                contents = contents or LawnchairBackup.INCLUDE_WALLPAPER
            }
            return if (backup.restore(contents)) contents else -1
        }

        override fun onPostExecute(result: Int) {
            super.onPostExecute(result)

            if (result > -1) {

                showMessage(R.drawable.ic_check, R.string.restore_success)

                if (result and LawnchairBackup.INCLUDE_SETTINGS == 0) {
                    Utilities.getLawnchairPrefs(this@RestoreBackupFileActivity).blockingEdit {
                        restoreSuccess = true
                    }
                } else {
                    LauncherAppState.getInstance(context).iconCache.clear()
                }

                if (result and LawnchairBackup.INCLUDE_HOMESCREEN != 0) {
                    RestoreDbTask.setPending(context, true)
                }

                Handler().postDelayed({
                    if (fromExternal) {
                        showMessage(R.drawable.ic_check, R.string.restore_success)
                        Utilities.getLawnchairPrefs(this@RestoreBackupFileActivity).blockingEdit { restoreSuccess = false }
                    }
                    Utilities.killLauncher()
                }, 2000)
            } else {
                inProgress = false

                showMessage(R.drawable.ic_close, R.string.failed)
            }
        }

    }

    private fun showMessage(icon: Int, text: Int) {
        progress.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        progressText.visibility = View.VISIBLE
        successIcon.apply {
            visibility = View.VISIBLE
            setImageDrawable(getDrawable(icon))
            DrawableCompat.setTint(drawable, ColorEngine.getInstance(context).accent)
        }
        progressText.setText(text)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorEngine.getInstance(this).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.ACCENT -> {
                val tintList = ColorStateList.valueOf(resolveInfo.color)
                progressBar.indeterminateTintList = tintList
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(this).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    companion object {

        const val EXTRA_PATH = "path"
        const val EXTRA_SUCCESS = "success"
    }
}
