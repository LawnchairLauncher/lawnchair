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

package ch.deletescape.lawnchair.util

import android.view.View
import android.view.ViewParent

class ViewParents(private val view: View) : Iterable<ViewParent> {

    override fun iterator(): Iterator<ViewParent> = ViewParentIterator(view.parent)

    class ViewParentIterator(private var parent: ViewParent?) : Iterator<ViewParent> {

        override fun hasNext() = parent != null

        override fun next(): ViewParent {
            val current = parent ?: throw IllegalStateException("no next parent")
            parent = current.parent
            return current
        }
    }
}

val View.parents get() = ViewParents(this)
