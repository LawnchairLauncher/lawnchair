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
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.AppCompatEditText
import android.view.View
import android.widget.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.provider.RestoreDbTask

class RestoreBackupActivity : SettingsBaseActivity(), LawnchairBackup.MetaLoader.Callback, ColorEngine.OnColorChangeListener {
    private val backupName by lazy { findViewById<AppCompatEditText>(R.id.name) }
    private val backupTimestamp by lazy { findViewById<AppCompatEditText>(R.id.timestamp) }

    private val backupHomescreen by lazy { findViewById<CheckBox>(R.id.content_homescreen) }
    private val backupSettings by lazy { findViewById<CheckBox>(R.id.content_settings) }
    private val backupWallpaper by lazy { findViewById<CheckBox>(R.id.content_wallpaper) }

    private val backup by lazy {
        if (intent.hasExtra(EXTRA_URI))
            LawnchairBackup(this, Uri.parse(intent.getStringExtra(EXTRA_URI)))
        else
            LawnchairBackup(this, intent.data)
    }
    private val backupMetaLoader by lazy { LawnchairBackup.MetaLoader(backup) }

    private val config by lazy { findViewById<View>(R.id.config) }
    private val startButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }
    private val progress by lazy { findViewById<View>(R.id.progress) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progressBar) }
    private val progressText by lazy { findViewById<TextView>(R.id.progress_text) }
    private val successIcon by lazy { findViewById<ImageView>(R.id.success_icon) }

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
        setContentView(R.layout.activity_restore_backup)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when {
            intent.hasExtra(EXTRA_URI) -> {  }
            intent.data != null -> { fromExternal = true }
            intent.hasExtra(EXTRA_SUCCESS) -> {
                inProgress = true
                showMessage(R.drawable.ic_check, R.string.restore_success)
                Utilities.getLawnchairPrefs(this).blockingEdit { restoreSuccess = false }
                Handler().postDelayed({ finish() }, 2000)
                return
            }
            else -> {
                finish()
                return
            }
        }

        startButton.setOnClickListener {
            startRestore()
        }

        loadMeta()
    }

    fun loadMeta() {
        backupMetaLoader.callback = this
        backupMetaLoader.loadMeta()

        config.visibility = View.GONE
        startButton.visibility = View.GONE
        progress.visibility = View.VISIBLE
        progressText.visibility = View.GONE
    }

    override fun onMetaLoaded() {
        config.visibility = View.VISIBLE
        startButton.visibility = View.VISIBLE
        progress.visibility = View.GONE
        backupMetaLoader.callback = null
        if (backup.meta != null) {
            backupName.setText(backup.meta?.name)
            backupTimestamp.setText(backup.meta?.localizedTimestamp)
            val contents = backup.meta!!.contents
            val includeHomescreen = contents and LawnchairBackup.INCLUDE_HOMESCREEN != 0
            backupHomescreen.isEnabled = includeHomescreen
            backupHomescreen.isChecked = includeHomescreen
            val includeSettings = contents and LawnchairBackup.INCLUDE_SETTINGS != 0
            backupSettings.isEnabled = includeSettings
            backupSettings.isChecked = includeSettings
            val includeWallpaper = contents and LawnchairBackup.INCLUDE_WALLPAPER != 0
            backupWallpaper.isEnabled = includeWallpaper
            backupWallpaper.isChecked = includeWallpaper
        } else {
            showMessage(R.drawable.ic_close, R.string.backup_invalid)
        }
    }

    private fun validateOptions(): Int {
        return if (backupName.text == null || backupName.text.toString() == "") {
            R.string.backup_error_blank_name
        } else if (!backupHomescreen.isChecked && !backupSettings.isChecked && !backupWallpaper.isChecked) {
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

            config.visibility = View.GONE
            startButton.visibility = View.GONE

            progress.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE

            inProgress = true
        }

        override fun doInBackground(vararg params: Void?): Int {
            var contents = 0
            if (backupHomescreen.isChecked) {
                contents = contents or LawnchairBackup.INCLUDE_HOMESCREEN
            }
            if (backupSettings.isChecked) {
                contents = contents or LawnchairBackup.INCLUDE_SETTINGS
            }
            if (backupWallpaper.isChecked) {
                contents = contents or LawnchairBackup.INCLUDE_WALLPAPER
            }
            return if (backup.restore(contents)) contents else -1
        }

        override fun onPostExecute(result: Int) {
            super.onPostExecute(result)

            if (result > -1) {
                progressText.text = getString(R.string.backup_restarting)

                if (result and LawnchairBackup.INCLUDE_SETTINGS == 0) {
                    Utilities.getLawnchairPrefs(this@RestoreBackupActivity).blockingEdit {
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
                        val intent = Intent(this@RestoreBackupActivity,
                                RestoreBackupActivity::class.java).putExtra(EXTRA_SUCCESS, true)
                        startActivity(intent)
                    }
                    Utilities.killLauncher()
                }, 500)
            } else {
                inProgress = false

                showMessage(R.drawable.ic_close, R.string.failed)
            }
        }

    }

    private fun showMessage(icon: Int, text: Int) {
        config.visibility = View.GONE
        startButton.visibility = View.GONE
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
                startButton.apply {
                    DrawableCompat.setTint(background, resolveInfo.color)
                    DrawableCompat.setTint(drawable, resolveInfo.foregroundColor)
                    backgroundTintList = tintList
                }
                backupName.apply {
                    highlightColor = resolveInfo.color
                    supportBackgroundTintList = tintList
                }
                backupTimestamp.apply {
                    highlightColor = resolveInfo.color
                    supportBackgroundTintList = tintList
                }
                backupHomescreen.buttonTintList = tintList
                backupSettings.buttonTintList = tintList
                backupWallpaper.buttonTintList = tintList
                progressBar.indeterminateTintList = tintList
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(this).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    companion object {

        const val EXTRA_URI = "uri"
        const val EXTRA_SUCCESS = "success"
    }
}
