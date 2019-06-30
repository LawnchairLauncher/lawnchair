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

import android.app.AlertDialog
import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import ch.deletescape.lawnchair.gestures.BlankGestureHandler
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.preferences.LauncherRecyclerViewPreference

class LauncherGesturePreference(context: Context, attrs: AttributeSet?) : LauncherRecyclerViewPreference(context, attrs) {

    var value: String? = null
        set(value) {
            field = value
            notifyChanged()
        }
    var defaultValue = BlankGestureHandler::class.java.name
    lateinit var onSelectHandler: (GestureHandler) -> Unit

    private val blankGestureHandler = BlankGestureHandler(context, null)
    private val handler get() = GestureController.createGestureHandler(context, value, blankGestureHandler)

    override fun getSummary() = handler.displayName

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        value = if (restorePersistedValue) {
            getPersistedString(defaultValue as String?) ?: ""
        } else {
            defaultValue as String? ?: ""
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setPositiveButton(null, null)
    }

    override fun onBindRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = HandlerListAdapter(themedContext, false, getClassName(), onSelectHandler)
        recyclerView.layoutManager = LinearLayoutManager(themedContext)
    }

    fun getClassName(): String {
        return GestureController.getClassName(value ?: defaultValue)
    }
}
