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

package ch.deletescape.lawnchair.font

import android.graphics.Typeface
import android.widget.TextView
import ch.deletescape.lawnchair.runOnMainThread

class FontLoader(font: FontCache.Font) : FontCache.Font.LoadCallback {

    private var fontLoaded = false
    private var face: Typeface? = null
    private var textViews = HashMap<TextView, Typeface>()
    private var receivers = HashMap<FontReceiver, Typeface>()

    init {
        font.load(this)
    }

    override fun onFontLoaded(typeface: Typeface?) {
        runOnMainThread {
            fontLoaded = true
            face = typeface
            textViews.entries.forEach { into(it.key, it.value) }
            textViews.clear()
            receivers.entries.forEach { into(it.key, it.value) }
            receivers.clear()
        }
    }

    fun into(target: TextView, fallback: Typeface) {
        if (!fontLoaded) {
            target.typeface = fallback
            textViews[target] = fallback
        } else {
            target.typeface = face ?: fallback
        }
    }

    fun into(target: FontReceiver, fallback: Typeface) {
        if (!fontLoaded) {
            target.setTypeface(fallback)
            receivers[target] = fallback
        } else {
            target.setTypeface(face ?: fallback)
        }
    }

    interface FontReceiver {

        fun setTypeface(typeface: Typeface)
    }
}
