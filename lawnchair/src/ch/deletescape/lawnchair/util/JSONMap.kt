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

import ch.deletescape.lawnchair.getNullable
import org.json.JSONObject

class JSONMap(private val obj: JSONObject) : Map<String, Any> {

    override val size get() = obj.length()

    override fun isEmpty() = size != 0

    override fun containsKey(key: String) = obj.has(key)

    override fun containsValue(value: Any): Boolean {
        return false
    }

    override fun get(key: String) = obj.getNullable(key)

    override val entries = EntrySet()

    override val keys = KeySet()

    override val values = ValueCollection()

    inner class Entry(override val key: String) : Map.Entry<String, Any> {

        override val value: Any
            get() = obj.get(key)
    }

    inner class EntrySet : Set<Entry> {

        override val size = obj.length()

        override fun contains(element: Entry): Boolean {
            return obj.has(element.key)
        }

        override fun containsAll(elements: Collection<Entry>): Boolean {
            return elements.all { obj.has(it.key) }
        }

        override fun isEmpty() = size != 0

        override fun iterator() = TransformIterator(obj.keys()) { key -> Entry(key) }
    }

    inner class KeySet : Set<String> {

        override val size = obj.length()

        override fun contains(element: String): Boolean {
            return obj.has(element)
        }

        override fun containsAll(elements: Collection<String>): Boolean {
            return elements.all { obj.has(it) }
        }

        override fun isEmpty() = size != 0

        override fun iterator(): Iterator<String> = obj.keys()
    }

    inner class ValueCollection : Collection<Any> {

        override val size = obj.length()

        override fun contains(element: Any): Boolean {
            val it = iterator()
            while (it.hasNext()) {
                if (element == it.next()) return true
            }
            return false
        }

        override fun containsAll(elements: Collection<Any>): Boolean {
            val set = HashSet<Any>()
            val it = iterator()
            while (it.hasNext()) {
                set.add(it.next())
            }
            return set.containsAll(elements)
        }

        override fun isEmpty() = size != 0

        override fun iterator() = TransformIterator(obj.keys()) { key -> obj.get(key)}
    }

    class TransformIterator<T1, T2>(private val base: Iterator<T1>, private val transform: (T1) -> T2) : Iterator<T2> {

        override fun hasNext() = base.hasNext()

        override fun next(): T2 {
            return transform(base.next())
        }
    }
}
