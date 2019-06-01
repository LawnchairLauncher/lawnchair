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

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.deletescape.lawnchair.getThemeAttr
import com.android.launcher3.R

class PreferenceScreenDialogFragment : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_preference_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val content = arguments!!.getInt(KEY_CONTENT)
        val fragment = SettingsActivity.DialogSettingsFragment.newInstance("", content)
        childFragmentManager.beginTransaction()
                .replace(R.id.fragment_content, fragment)
                .commit()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(activity!!, activity!!.getThemeAttr(R.attr.alertDialogTheme))
    }

    companion object {

        private const val KEY_THEME = "theme"
        private const val KEY_CONTENT = "content"

        fun newInstance(preference: PreferenceDialogPreference) = PreferenceScreenDialogFragment().apply {
            arguments = Bundle(2).apply {
                putInt(KEY_THEME, preference.context.getThemeAttr(R.attr.alertDialogTheme))
                putInt(KEY_CONTENT, preference.content)
            }
        }
    }
}
