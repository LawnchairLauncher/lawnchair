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
import android.support.v7.preference.ListPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.animations.AnimationType
import ch.deletescape.lawnchair.util.buildEntries

import com.android.launcher3.R
import com.android.launcher3.Utilities

class AnimationTypePreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    init {
        buildEntries {
            addEntry(R.string.animation_type_default, "")
            if (AnimationType.hasControlRemoteAppTransitionPermission(context)) {
                addEntry(R.string.animation_type_pie, AnimationType.TYPE_PIE)
            } else {
                addEntry(R.string.animation_type_pie_like, AnimationType.TYPE_PIE)
            }
            if (Utilities.ATLEAST_MARSHMALLOW) {
                addEntry(R.string.animation_type_reveal, AnimationType.TYPE_REVEAL)
            }
            addEntry(R.string.animation_type_slide_up, AnimationType.TYPE_SLIDE_UP)
            addEntry(R.string.animation_type_scale_up, AnimationType.TYPE_SCALE_UP)
            addEntry(R.string.animation_type_blink, AnimationType.TYPE_BLINK)
            addEntry(R.string.animation_type_fade, AnimationType.TYPE_FADE)
        }
    }
}
