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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SettingsBottomSheetDialog(context: Context) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (window != null && Utilities.ATLEAST_OREO) {
            findViewById<View>(android.support.design.R.id.container)!!.fitsSystemWindows = false
        }
    }

    override fun setContentView(view: View) {
        super.setContentView(wrapInContainer(view))
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(wrapInContainer(view), null)
    }

    private fun wrapInContainer(view: View): View {
        val container = View.inflate(context, R.layout.settings_bottom_sheet_container, null)
                as ViewGroup
        container.addView(view)
        return container
    }
}
