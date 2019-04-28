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

import android.content.ComponentName
import android.content.Context
import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ItemInfoMatcher

abstract class Filter<T>(val context: Context) {

    abstract val matches: Set<T>?

    open val size get() = matches?.size ?: 0

    abstract val matcher: ItemInfoMatcher
}

class CustomFilter(context: Context, override val matches: Set<ComponentKey>) : Filter<ComponentKey>(context) {

    override val matcher
        get() = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?): Boolean {
                return matches.contains(ComponentKey(info.targetComponent, info.user))
            }
        }
}

class IconPackFilter(context: Context) : Filter<String>(context) {

    override val matches = IconPackManager.getInstance(context).getPackProviders().map { it.name }.toHashSet()

    override val matcher
        get() = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?): Boolean {
                return matches.contains(info.targetComponent.packageName)
            }
        }
}