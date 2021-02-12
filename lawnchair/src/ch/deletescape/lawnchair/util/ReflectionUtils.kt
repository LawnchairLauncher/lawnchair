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

import java.lang.reflect.Field
import kotlin.reflect.KProperty

class ReflectionDelegate<T : Any, R : Any?>(private val fieldName: String) {

    private var cachedField: Field? = null

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: T, property: KProperty<*>): R {
        return getField(thisRef).get(thisRef) as R
    }

    operator fun setValue(thisRef: T, property: KProperty<*>, value: R) {
        return getField(thisRef).set(thisRef, value)
    }

    private fun getField(thisRef: T): Field {
        if (cachedField == null) {
            cachedField = thisRef::class.java.getDeclaredField(fieldName)
            cachedField!!.isAccessible = true
        }
        return cachedField!!
    }

}
