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
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.Insettable

open class InsettableNestedScrollView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SpringNestedScrollView(context, attrs, defStyleAttr), Insettable {

    private val currentInsets = Rect()

    override fun setInsets(insets: Rect) {
        currentInsets.set(insets)
        (getChildAt(0) as Insettable).setInsets(insets)
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        (child as Insettable).setInsets(currentInsets)
    }
}
