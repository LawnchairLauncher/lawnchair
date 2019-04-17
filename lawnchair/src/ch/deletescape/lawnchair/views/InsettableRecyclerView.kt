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
import android.graphics.Rect
import android.util.AttributeSet
import com.android.launcher3.Insettable

open class InsettableRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SpringRecyclerView(context, attrs, defStyleAttr), Insettable {

    private val currentInsets = Rect()
    private val currentPadding = Rect()

    override fun setInsets(insets: Rect) {
        super.setPadding(
                paddingLeft + insets.left - currentInsets.left,
                paddingTop + insets.top - currentInsets.top,
                paddingRight + insets.right - currentInsets.right,
                paddingBottom + insets.bottom - currentInsets.bottom)
        currentInsets.set(insets)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(
                paddingLeft + left - currentPadding.left,
                paddingTop + top - currentPadding.top,
                paddingRight + right - currentPadding.right,
                paddingBottom + bottom - currentPadding.bottom)
        currentPadding.set(left, top, right, bottom)
    }
}
