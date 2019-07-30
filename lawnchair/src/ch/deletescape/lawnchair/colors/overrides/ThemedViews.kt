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

package ch.deletescape.lawnchair.colors.overrides

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat
import android.support.v7.preference.ListPreferenceDialogFragmentCompat
import android.support.v7.preference.MultiSelectListPreferenceDialogFragmentCompat
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import android.widget.EditText
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.colors.ColorEngine

open class ThemedListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog?)?.applyAccent()
    }

    companion object {
        fun newInstance(key: String): ThemedListPreferenceDialogFragment {
            val fragment = ThemedListPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}

class ThemedEditTextPreferenceDialogFragmentCompat : EditTextPreferenceDialogFragmentCompat() {

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog?)?.applyAccent()
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)
        val color = ColorEngine.getInstance(context!!).accent
        val tintList = ColorStateList.valueOf(color)
        view?.findViewById<EditText>(android.R.id.edit)?.apply {
            highlightColor = color
            backgroundTintList = tintList
        }
    }

    companion object {
        fun newInstance(key: String): ThemedEditTextPreferenceDialogFragmentCompat {
            val fragment = ThemedEditTextPreferenceDialogFragmentCompat()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}

class ThemedMultiSelectListPreferenceDialogFragmentCompat : MultiSelectListPreferenceDialogFragmentCompat() {

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog?)?.applyAccent()
    }

    companion object {
        fun newInstance(key: String): ThemedMultiSelectListPreferenceDialogFragmentCompat {
            val fragment = ThemedMultiSelectListPreferenceDialogFragmentCompat()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}