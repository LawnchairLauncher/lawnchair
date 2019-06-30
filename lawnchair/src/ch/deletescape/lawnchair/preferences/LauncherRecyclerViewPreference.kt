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
import android.preference.DialogPreference
import android.support.v7.view.ContextThemeWrapper
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.setCustomFont
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.R

abstract class LauncherRecyclerViewPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    private val themeRes = ThemeOverride.LauncherDialog().getTheme(context)
    protected val themedContext = ContextThemeWrapper(context, themeRes)

    init {
        dialogLayoutResource = R.layout.dialog_preference_recyclerview
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        onBindRecyclerView(view.findViewById(R.id.list))

        view.post {
            val window = dialog.window ?: return@post
            window.findViewById<TextView>(android.R.id.button1)?.setCustomFont(CustomFontManager.FONT_DIALOG_TITLE)
            window.findViewById<TextView>(android.R.id.button2)?.setCustomFont(CustomFontManager.FONT_DIALOG_TITLE)
            window.findViewById<TextView>(android.R.id.button3)?.setCustomFont(CustomFontManager.FONT_DIALOG_TITLE)
        }
    }

    abstract fun onBindRecyclerView(recyclerView: RecyclerView)
}
