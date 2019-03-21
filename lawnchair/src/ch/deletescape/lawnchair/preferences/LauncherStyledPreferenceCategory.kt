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
import android.preference.PreferenceCategory
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine

class LauncherStyledPreferenceCategory(context: Context, attrs: AttributeSet) : PreferenceCategory(context, attrs) {

    override fun onBindView(view: View) {
        super.onBindView(view)
        val title = view.findViewById(android.R.id.title) as TextView
        title.setTextColor(ColorEngine.getInstance(context).accent)
    }
}
