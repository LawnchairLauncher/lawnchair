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

import android.content.Context
import ch.deletescape.lawnchair.LawnchairAppFilter
import com.android.launcher3.util.ComponentKey

class DrawerTabEditAdapter(context: Context, tabIndex: Int, callback: Callback? = null)
    : SelectableAppsAdapter(context, callback, LawnchairAppFilter(context)) {

    override fun getInitialSelections(): Set<ComponentKey> {
        return emptySet()
    }

    override fun setSelections(selections: Set<ComponentKey>) {

    }
}
