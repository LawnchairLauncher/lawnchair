package ch.deletescape.lawnchair.backup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NewBackupActivity : SettingsBaseActivity() {

    private val permissionRequestReadExternalStorage = 0

    private val backupName by lazy { findViewById<EditText>(R.id.name) }

    private val backupHomescreen by lazy { findViewById<CheckBox>(R.id.content_homescreen) }
    private val backupSettings by lazy { findViewById<CheckBox>(R.id.content_settings) }
    private val backupWallpaper by lazy { findViewById<CheckBox>(R.id.content_wallpaper) }

    private val backupLocationDevice by lazy { findViewById<RadioButton>(R.id.location_device) }

    private val config by lazy { findViewById<View>(R.id.config) }
    private val startButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }
    private val progress by lazy { findViewById<View>(R.id.progress) }

    private var backupUri = Uri.parse("/")
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
        setContentView(R.layout.activity_new_backup)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        backupName.setText(getTimestamp())

        startButton.setOnClickListener {
            onStartBackup()
        }
    }

    private fun onStartBackup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Snackbar.make(findViewById(R.id.content), R.string.read_external_storage_required,
                        Snackbar.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    permissionRequestReadExternalStorage)
        } else {
            val error = validateOptions()
            if (error == 0) {
                val fileName = "${backupName.text}.${LawnchairBackup.EXTENSION}"
                if (backupLocationDevice.isChecked) {
                    backupUri = Uri.fromFile(File(LawnchairBackup.getFolder(), fileName))
                    startBackup()
                } else {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = LawnchairBackup.MIME_TYPE
                    intent.putExtra(Intent.EXTRA_TITLE, fileName)
                    startActivityForResult(intent, 1)
                }
            } else {
                Snackbar.make(findViewById(R.id.content), error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBackup() {
        CreateBackupTask(this).execute()
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

    fun getTimestamp(): String {
        val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy hh:mm:ss", Locale.US)
        return simpleDateFormat.format(Date())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            permissionRequestReadExternalStorage -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    onStartBackup()
                } else {
                    Snackbar.make(findViewById(R.id.content), R.string.read_external_storage_required,
                            Snackbar.LENGTH_SHORT).show()
                }
            }
            else -> {

            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                val takeFlags = intent.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(resultData.data, takeFlags)
                backupUri = resultData.data
                startBackup()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, resultData)
        }
    }

    override fun onBackPressed() {
        if (!inProgress) super.onBackPressed()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class CreateBackupTask(val context: Context) : AsyncTask<Void, Void, Boolean>() {

        override fun onPreExecute() {
            super.onPreExecute()

            config.visibility = View.GONE
            startButton.visibility = View.GONE

            progress.visibility = View.VISIBLE

            inProgress = true
        }

        override fun doInBackground(vararg params: Void?): Boolean {
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
            return LawnchairBackup.create(
                    context = context,
                    name = backupName.text.toString(),
                    location = backupUri,
                    contents = contents
            )
        }

        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)

            if (result) {
                setResult(Activity.RESULT_OK, Intent().setData(backupUri))
                finish()
            } else {
                inProgress = false
                Snackbar.make(findViewById(R.id.content), R.string.backup_failed, Snackbar.LENGTH_SHORT).show()
            }
        }

    }
}
