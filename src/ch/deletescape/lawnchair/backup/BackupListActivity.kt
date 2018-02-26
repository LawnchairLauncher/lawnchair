package ch.deletescape.lawnchair.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.TextView
import com.android.launcher3.R
import com.android.launcher3.Utilities

class BackupListActivity : AppCompatActivity(), BackupListAdapter.Callbacks {

    private val bottomSheet by lazy { BottomSheetDialog(this) }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.recyclerView) }
    private val adapter by lazy { BackupListAdapter(this) }

    private val bottomSheetView by lazy {
        layoutInflater.inflate(R.layout.backup_bottom_sheet,
                findViewById(android.R.id.content), false)
    }

    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bottomSheetView.findViewById<View>(R.id.action_restore_backup).setOnClickListener {
            bottomSheet.dismiss()
            openRestore(currentPosition)
        }
        bottomSheetView.findViewById<View>(R.id.action_share_backup).setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheetView.findViewById<View>(R.id.action_remove_backup_from_list).setOnClickListener {
            bottomSheet.dismiss()
            removeItem(currentPosition)
        }
        bottomSheet.setContentView(bottomSheetView)

        adapter.callbacks = this
        adapter.setData(Utilities.getLawnchairPrefs(this)
                .recentBackups.toList().map { LawnchairBackup(this, it) })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        Utilities.checkRestoreSuccess(this)
    }

    override fun openBackup() {
        startActivityForResult(Intent(this, NewBackupActivity::class.java), 1)
    }

    override fun openRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/lawnchair"
        startActivityForResult(intent, 2)
    }

    override fun openRestore(position: Int) {
        startActivity(Intent(this, RestoreBackupActivity::class.java).apply {
            putExtra(RestoreBackupActivity.EXTRA_URI, adapter[position].uri.toString())
        })
    }

    override fun openEdit(position: Int) {
        currentPosition = position
        adapter[position].meta?.apply {
            bottomSheetView.findViewById<TextView>(android.R.id.title).text = name
            bottomSheet.show()
        }
    }

    fun removeItem(position: Int) {
        adapter.removeItem(position)
        saveChanges()
    }

    private fun saveChanges() {
        Utilities.getLawnchairPrefs(this).blockingEdit {
            recentBackups.replaceWith(adapter.toUriList())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                adapter.addItem(LawnchairBackup(this, resultData.data))
                saveChanges()
                Utilities.getLawnchairPrefs(this).recentBackups.add(0, resultData.data)
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
