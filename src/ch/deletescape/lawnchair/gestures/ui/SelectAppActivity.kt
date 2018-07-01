package ch.deletescape.lawnchair.gestures.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import ch.deletescape.lawnchair.preferences.AppsAdapter
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R

class SelectAppActivity : SettingsBaseActivity(), AppsAdapter.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_spring_recyclerview)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView = findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = AppsAdapter(this, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAppSelected(app: AppsAdapter.App) {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra("appName", app.info.label)
            putExtra("target", app.key.toString())
        })
        finish()
    }
}