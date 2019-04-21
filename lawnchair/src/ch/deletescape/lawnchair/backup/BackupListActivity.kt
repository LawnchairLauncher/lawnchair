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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import ch.deletescape.lawnchair.settings.ui.SettingsBottomSheet
import ch.deletescape.lawnchair.settings.ui.SettingsBottomSheetDialog
import com.android.launcher3.R
import com.android.launcher3.Utilities

class BackupListActivity : SettingsBaseActivity(), BackupListAdapter.Callbacks {

    private val permissionRequestReadExternalStorage = 0

    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.recyclerView) }
    private val adapter by lazy { BackupListAdapter(this) }

    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        adapter.callbacks = this
        loadLocalBackups()
        recyclerView.layoutManager = GridLayoutManager(this, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (position == 0) 2 else 1
            }
        }
        recyclerView.adapter = adapter

        Utilities.checkRestoreSuccess(this)
    }

    private fun loadLocalBackups() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Snackbar.make(findViewById(android.R.id.content), R.string.read_external_storage_required,
                        Snackbar.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    permissionRequestReadExternalStorage)
        } else {
            adapter.setData(LawnchairBackup.listLocalBackups(this))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            permissionRequestReadExternalStorage -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    adapter.setData(LawnchairBackup.listLocalBackups(this))
                }
            }
            else -> {

            }
        }
    }

    override fun openBackup() {
        startActivityForResult(Intent(this, NewBackupActivity::class.java), 1)
    }

    override fun openRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = LawnchairBackup.MIME_TYPE
        intent.putExtra(Intent.EXTRA_MIME_TYPES, LawnchairBackup.EXTRA_MIME_TYPES)
        startActivityForResult(intent, 2)
    }

    override fun openRestore(position: Int) {
        startActivity(Intent(this, RestoreBackupActivity::class.java).apply {
            putExtra(RestoreBackupActivity.EXTRA_URI, adapter[position].uri.toString())
        })
    }

    override fun openEdit(position: Int) {
        currentPosition = position
        val visibility = if (adapter[position].meta != null) View.VISIBLE else View.GONE

        val bottomSheetView = layoutInflater.inflate(R.layout.backup_bottom_sheet,
                findViewById(android.R.id.content), false)
        bottomSheetView.findViewById<TextView>(android.R.id.title).text =
                adapter[position].meta?.name ?: getString(R.string.backup_invalid)
        bottomSheetView.findViewById<TextView>(android.R.id.summary).text =
                adapter[position].meta?.localizedTimestamp ?: getString(R.string.backup_invalid)

        val restoreBackup = bottomSheetView.findViewById<View>(R.id.action_restore_backup)
        val shareBackup = bottomSheetView.findViewById<View>(R.id.action_share_backup)
        val removeBackup = bottomSheetView.findViewById<View>(R.id.action_remove_backup_from_list)
        val divider = bottomSheetView.findViewById<View>(R.id.divider)
        restoreBackup.visibility = visibility
        shareBackup.visibility = visibility
        divider.visibility = visibility

        val bottomSheet = SettingsBottomSheet.inflate(this)
        restoreBackup.setOnClickListener {
            bottomSheet.close(true)
            openRestore(currentPosition)
        }
        shareBackup.setOnClickListener {
            bottomSheet.close(true)
            shareBackup(currentPosition)
        }
        removeBackup.setOnClickListener {
            bottomSheet.close(true)
            removeItem(currentPosition)
        }
        bottomSheet.show(bottomSheetView, true)
    }

    private fun removeItem(position: Int) {
        adapter.removeItem(position)
        saveChanges()
    }

    private fun shareBackup(position: Int) {
        val shareTitle = getString(R.string.backup_share)
        val shareText = getString(R.string.backup_share_text)
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = LawnchairBackup.MIME_TYPE
        shareIntent.putExtra(Intent.EXTRA_STREAM, adapter[position].uri)
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareTitle)
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
        startActivity(Intent.createChooser(shareIntent, shareTitle))
    }

    private fun saveChanges() {
        Utilities.getLawnchairPrefs(this).blockingEdit {
            recentBackups.replaceWith(adapter.toUriList())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                adapter.addItem(LawnchairBackup(this, resultData.data))
                saveChanges()
            }
        } else if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                val takeFlags = intent.flags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(resultData.data, takeFlags)
                val uri = resultData.data
                if (!Utilities.getLawnchairPrefs(this).recentBackups.contains(uri)) {
                    adapter.addItem(LawnchairBackup(this, uri))
                    saveChanges()
                }
                openRestore(0)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, resultData)
        }
    }
}
