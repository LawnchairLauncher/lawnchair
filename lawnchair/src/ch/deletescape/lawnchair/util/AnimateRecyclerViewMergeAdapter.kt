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

import android.support.v7.widget.RecyclerView
import me.mvdw.recyclerviewmergeadapter.adapter.RecyclerViewMergeAdapter

open class AnimateRecyclerViewMergeAdapter : RecyclerViewMergeAdapter() {

    override fun addAdapter(index: Int, adapter: RecyclerView.Adapter<*>) {
        super.addAdapter(index, adapter)

        val subAdapterOffset = getSubAdapterFirstGlobalPosition(adapter)
        if (adapter.itemCount > 0) {
            notifyItemRangeInserted(subAdapterOffset + 0, adapter.itemCount)
        }
    }

    override fun removeAdapter(index: Int) {
        val adapter = getSubAdapter(index)

        val subAdapterOffset = getSubAdapterFirstGlobalPosition(adapter)
        val itemCount = adapter.itemCount

        super.removeAdapter(index)

        if (itemCount > 0) {
            notifyItemRangeRemoved(subAdapterOffset, itemCount)
        }
    }
}
