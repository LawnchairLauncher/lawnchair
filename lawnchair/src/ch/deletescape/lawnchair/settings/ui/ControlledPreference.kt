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

package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.R

interface ControlledPreference {

    val controller: PreferenceController?

    class Delegate(private val context: Context, attrs: AttributeSet?) : ControlledPreference {

        override var controller: PreferenceController? = null

        init {
            parseAttributes(attrs)
        }

        fun parseAttributes(attrs: AttributeSet?) {
            if (attrs == null) return
            val a = context.obtainStyledAttributes(attrs, R.styleable.ControlledPreference)
            for (i in a.indexCount - 1 downTo 0) {
                val attr = a.getIndex(i)
                if (attr == R.styleable.ControlledPreference_controllerClass) {
                    setControllerClass(a.getString(attr))
                }
            }
            a.recycle()
        }

        private fun setControllerClass(controllerClass: String?) {
            controller = PreferenceController.create(context, controllerClass)
        }
    }
}
