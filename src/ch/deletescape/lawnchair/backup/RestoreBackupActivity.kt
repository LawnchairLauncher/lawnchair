package ch.deletescape.lawnchair.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.android.launcher3.R
import com.android.launcher3.Utilities

class RestoreBackupActivity : AppCompatActivity(), LawnchairBackup.MetaLoader.Callback {

    private val backupName by lazy { findViewById<EditText>(R.id.name) }
    private val backupTimestamp by lazy { findViewById<EditText>(R.id.timestamp) }

    private val backupHomescreen by lazy { findViewById<CheckBox>(R.id.content_homescreen) }
    private val backupSettings by lazy { findViewById<CheckBox>(R.id.content_settings) }
    private val backupWallpaper by lazy { findViewById<CheckBox>(R.id.content_wallpaper) }

    private val backup by lazy { LawnchairBackup(this, Uri.parse(intent.getStringExtra(EXTRA_URI))) }
    private val backupMetaLoader by lazy { LawnchairBackup.MetaLoader(backup) }

    private val config by lazy { findViewById<View>(R.id.config) }
    private val startButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }
    private val progress by lazy { findViewById<View>(R.id.progress) }
    private val progressBar by lazy { findViewById<View>(R.id.progressBar) }
    private val progressText by lazy { findViewById<TextView>(R.id.progress_text) }
    private val successIcon by lazy { findViewById<ImageView>(R.id.success_icon) }

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

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (intent.hasExtra(EXTRA_URI)) {
            startButton.setOnClickListener {
                startRestore()
            }

            loadMeta()
        } else if (intent.hasExtra(EXTRA_SUCCESS)) {
            inProgress = true

            showMessage(R.drawable.ic_check, R.string.restore_success)

            Handler().postDelayed({ finish() }, 2000)
        }
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
            backupTimestamp.setText(backup.meta?.timestamp)
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
            showMessage(R.drawable.ic_close, R.string.restore_read_meta_fail)
        }
    }

    private fun validateOptions(): Int {
        return if (backupName.text == null || backupName.text.toString() == "") {
            R.string.backup_error_blank_name
        } else if (!backupHomescreen.isChecked && !backupSettings.isChecked) {
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
                }

                Handler().postDelayed({ Utilities.killLauncher() }, 1000)
            } else {
                inProgress = false

                showMessage(R.drawable.ic_close, R.string.restore_failed)
            }
        }

    }

    private fun showMessage(icon: Int, text: Int) {
        config.visibility = View.GONE
        startButton.visibility = View.GONE
        progress.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        successIcon.visibility = View.VISIBLE
        successIcon.setImageDrawable(getDrawable(icon))
        progressText.setText(text)
    }

    companion object {

        const val EXTRA_URI = "uri"
        const val EXTRA_SUCCESS = "success"
    }
}
