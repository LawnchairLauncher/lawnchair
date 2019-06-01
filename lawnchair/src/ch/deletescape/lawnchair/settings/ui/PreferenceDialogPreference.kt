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
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import com.android.launcher3.R

open class PreferenceDialogPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    var content = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PreferenceDialogPreference)
        content = a.getResourceId(R.styleable.PreferenceDialogPreference_content, 0)
        a.recycle()

        dialogLayoutResource = R.layout.dialog_preference_fragment
    }
}
