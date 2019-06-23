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

package ch.deletescape.lawnchair.perms

import android.content.Context
import android.content.DialogInterface
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.FrameLayout
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.util.ThemedContextProvider
import com.android.launcher3.R
import kotlinx.android.synthetic.lawnchair.perm_request_dialog.view.*

class CustomPermissionRequestDialog private constructor(private val context: Context, private val string: Int, private val icon: Int, private val explanation: Int?) {
    private val key = Pair(string, icon)
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    fun onResult(listener: (allowed: Boolean) -> Unit): CustomPermissionRequestDialog {
        listeners.add(listener)
        return this
    }

    fun show() {
        if (SHOWING.containsKey(key)) {
            SHOWING[key]?.let {
                it.listeners.addAll(listeners)
            }
            return
        }
        SHOWING[key] = this
        val themedContext = ThemedContextProvider(context.lawnchairApp.activityHandler.foregroundActivity
                ?: context, null, ThemeOverride.Settings()).get()
        AlertDialog.Builder(themedContext, ThemeOverride.AlertDialog().getTheme(context))
                .setView(DialogView(context, string, icon, explanation))
                .setIcon(icon)
                .setPositiveButton(context.getString(R.string.allow).toUpperCase(), DialogInterface.OnClickListener { _, _ ->
                    SHOWING.remove(key)
                    listeners.forEach { it(true) }
                })
                .setNegativeButton(context.getString(R.string.deny).toUpperCase(), DialogInterface.OnClickListener { _, _ ->
                    SHOWING.remove(key)
                    listeners.forEach { it(false) }
                })
                .setOnDismissListener {
                    SHOWING.remove(key)
                }
                .create()
                .apply {
                    applyAccent()
                    show()
                }
    }

    companion object {
        fun create(context: Context, @StringRes string: Int, @DrawableRes icon: Int, explanation: Int?) = CustomPermissionRequestDialog(context, string, icon, explanation)

        private val SHOWING = mutableMapOf<Pair<Int, Int>, CustomPermissionRequestDialog>()
    }

    inner class DialogView(context: Context, @StringRes private val string: Int, @DrawableRes private val icn: Int, @StringRes private val explanation: Int?) : FrameLayout(context) {
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            View.inflate(context, R.layout.perm_request_dialog, this)
            message.setText(string)
            icon.setImageResource(icn)
            icon.tintDrawable(context.getColorEngineAccent())
            icon_info.isVisible = explanation != null
            if (explanation != null) {
                text_explanation.setText(explanation)
            }
        }
    }
}