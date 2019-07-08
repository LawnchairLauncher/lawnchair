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

package ch.deletescape.lawnchair.preferences

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import ch.deletescape.lawnchair.applyAccent
import com.android.launcher3.R

class SmartspaceEventProvidersFragment : PreferenceDialogFragmentCompat() {

    private val providersPreference get() = preference as SmartspaceEventProvidersPreference
    private lateinit var adapter: SmartspaceEventProvidersAdapter

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        adapter = SmartspaceEventProvidersAdapter(view.context)

        val recyclerView = view.findViewById<RecyclerView>(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        adapter.itemTouchHelper = ItemTouchHelper(adapter.TouchHelperCallback()).apply {
            attachToRecyclerView(recyclerView)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            providersPreference.setProviders(adapter.saveSpecs())
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).applyAccent()
    }

    companion object {

        fun newInstance(key: String?) = SmartspaceEventProvidersFragment().apply {
            arguments = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
        }
    }
}
