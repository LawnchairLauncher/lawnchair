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

package ch.deletescape.lawnchair.gestures.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import ch.deletescape.lawnchair.preferences.AppsAdapterWithShortcuts
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R

class SelectAppActivity : SettingsBaseActivity(), AppsAdapterWithShortcuts.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_insettable_recyclerview)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView = findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = AppsAdapterWithShortcuts(this, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAppSelected(app: AppsAdapterWithShortcuts.AppItem) {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra("type", "app")
            putExtra("appName", app.info.label)
            putExtra("target", app.key.toString())
        })
        finish()
    }

    override fun onShortcutSelected(shortcut: AppsAdapterWithShortcuts.ShortcutItem) {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra("type", "shortcut")
            putExtra("appName", shortcut.label)
            putExtra("intent", shortcut.info.makeIntent().toUri(0))
            putExtra("user", shortcut.info.userHandle)
            putExtra("packageName", shortcut.info.`package`)
            putExtra("id", shortcut.info.id)
        })
        finish()
    }
}
