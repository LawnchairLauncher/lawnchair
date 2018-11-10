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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SelectGestureHandlerFragment : PreferenceDialogFragmentCompat() {

    private val requestCode = "config".hashCode() and 65535

    private val key by lazy { arguments!!.getString("key") }
    private val value by lazy { arguments!!.getString("value") }
    private val isSwipeUp by lazy { arguments!!.getBoolean("isSwipeUp") }
    private val currentClass by lazy { GestureController.getClassName(value) }

    private var selectedHandler: GestureHandler? = null

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val recyclerView = view.findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = HandlerListAdapter(activity as Context, isSwipeUp, currentClass, ::onSelectHandler)
        recyclerView.layoutManager = LinearLayoutManager(activity)
    }

    fun onSelectHandler(handler: GestureHandler) {
        selectedHandler = handler
        if (handler.configIntent != null) {
            startActivityForResult(handler.configIntent, requestCode)
        } else {
            saveChanges()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK) {
            selectedHandler?.onConfigResult(data)
            saveChanges()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun saveChanges() {
        Utilities.getLawnchairPrefs(activity).sharedPrefs.edit().putString(key, selectedHandler.toString()).apply()
        dismiss()
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setPositiveButton(null, null)
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).applyAccent()
    }

    override fun onDialogClosed(positiveResult: Boolean) {

    }

    companion object {

        fun newInstance(preference: GesturePreference) = SelectGestureHandlerFragment().apply {
            arguments = Bundle(2).apply {
                putString("key", preference.key)
                putString("value", preference.value)
                putBoolean("isSwipeUp", preference.isSwipeUp)
            }
        }
    }
}