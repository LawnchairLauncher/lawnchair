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

package ch.deletescape.lawnchair.util.diff

import android.os.Bundle

class BundleDiff(first: Bundle, second: Bundle) : Diff<String, Bundle>(first, second) {
    override val added: List<String> by lazy { second.keySet().filterNot { first.containsKey(it) } }
    override val removed: List<String> by lazy { first.keySet().filterNot { second.containsKey(it) } }
    override val changed: List<String> by lazy { first.keySet().filter { second.containsKey(it) }.filter { first[it] != second[it] } }
}

inline infix fun Bundle.diff(other: Bundle): BundleDiff = BundleDiff(this, other)