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

package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet

class FadingImageView(context: Context?, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {

    private val transparentDrawable = resources.getDrawable(android.R.color.transparent)

    var image: Drawable? = null
        set(value) {
            field = value
            if (value != null) {
                setImageDrawable(TransitionDrawable(arrayOf(transparentDrawable, value))
                        .apply { startTransition(125) })
            } else {
                setImageDrawable(null)
            }
        }
}
