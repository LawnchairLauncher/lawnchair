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

package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.ExtendedEditText

// TODO: use proper font in the drawer
class FolderNameEditText(context: Context, attrs: AttributeSet?) : ExtendedEditText(context, attrs) {

    private val lockDesktop = context.lawnchairPrefs.lockDesktop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (lockDesktop) return true
        return super.onTouchEvent(event)
    }
}
