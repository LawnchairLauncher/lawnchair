/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.Hotseat

open class CustomHotseat @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : Hotseat(context, attrs, defStyleAttr) {

    private val hotseatDisabled = context.lawnchairPrefs.dockHide

    init {
        if (hotseatDisabled) {
            super.setVisibility(View.GONE)
        }
    }

    override fun setVisibility(visibility: Int) {
        if (!hotseatDisabled) {
            super.setVisibility(visibility)
        }
    }
}
