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

package ch.deletescape.lawnchair.groups

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R

class LegacyDrawerTabsAdapter(context: Context) : DrawerTabsAdapter(context) {

    override fun createGroupHolder(parent: ViewGroup): TabHolder {
        return LegacyTabHolder(LayoutInflater.from(parent.context).inflate(R.layout.legacy_tab_item, parent, false))
    }

    override fun createHeaderItem(): Item? {
        return null
    }

    override fun createAddItem(): Item? {
        return null
    }

    inner class LegacyTabHolder(itemView: View) : TabHolder(itemView) {

        override fun formatSummary(summary: String?): String? {
            return summary
        }
    }
}
