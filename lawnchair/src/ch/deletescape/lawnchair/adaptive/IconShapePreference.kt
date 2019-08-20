/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.adaptive

import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.util.AttributeSet
import android.view.View
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.colors.overrides.ThemedListPreferenceDialogFragment
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.util.buildEntries
import com.android.launcher3.R

class IconShapePreference(context: Context, attrs: AttributeSet?) :
        ListPreference(context, attrs),
        LawnchairPreferences.OnPreferenceChangeListener,
        ControlledPreference by ControlledPreference.Delegate(context, attrs) {

    private var iconShape = IconShapeManager.getInstance(context).iconShape
        set(value) {
            if (field != value) {
                field = value
                drawable.iconShape = value
                rebuildEntries()
                iconShapeString = value.toString()
                this.value = iconShapeString
            }
        }
    private var iconShapeString = iconShape.toString()
    private val drawable = IconShapeDrawable(dpToPx(48f).toInt(), iconShape).apply {
        setColorFilter(context.getColorAttr(android.R.attr.colorControlNormal), PorterDuff.Mode.SRC_IN)
    }
    private var forceCustomizeMode: Boolean? = null
    private val isCustomIcon get() = iconShapeString.startsWith("v1")

    init {
        layoutResource = R.layout.pref_with_preview_icon
        icon = drawable
        rebuildEntries()
    }

    private fun rebuildEntries() {
        buildEntries {
            val shapeString = iconShape.toString()
            if (shapeString.startsWith("v1")) {
                addEntry(R.string.custom, shapeString)
            }
            addEntry(R.string.icon_shape_system_default, "")
            addEntry(R.string.icon_shape_circle, "circle")
            addEntry(R.string.icon_shape_square, "square")
            addEntry(R.string.icon_shape_rounded_square, "roundedSquare")
            addEntry(R.string.icon_shape_squircle, "squircle")
            addEntry(R.string.icon_shape_teardrop, "teardrop")
            addEntry(R.string.icon_shape_cylinder, "cylinder")
        }
    }

    override fun onAttached() {
        super.onAttached()
        context.lawnchairPrefs.addOnPreferenceChangeListener("pref_iconShape", this)
    }

    override fun onDetached() {
        super.onDetached()
        context.lawnchairPrefs.removeOnPreferenceChangeListener("pref_iconShape", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        iconShape = IconShapeManager.getInstance(context).iconShape
    }

    override fun onClick() {
        forceCustomizeMode = null
        super.onClick()
    }

    private fun forceShowCustomize(showCustomize: Boolean) {
        forceCustomizeMode = showCustomize
        preferenceManager.showDialog(this)
    }

    fun createDialogFragment(): DialogFragment {
        return if (forceCustomizeMode ?: isCustomIcon) {
            CustomizeDialogFragment.newInstance(key)
        } else {
            ListDialogFragment.newInstance(key)
        }
    }

    override fun getDialogLayoutResource(): Int {
        return if (forceCustomizeMode ?: isCustomIcon) {
            R.layout.icon_shape_customize_view
        } else {
            super.getDialogLayoutResource()
        }
    }

    class ListDialogFragment : ThemedListPreferenceDialogFragment() {

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            builder.setNeutralButton(R.string.custom) { _, _ ->
                dismiss()
                (preference as IconShapePreference).forceShowCustomize(true)
            }
        }

        companion object {

            fun newInstance(key: String) = ListDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_KEY, key)
                }
            }
        }
    }

    class CustomizeDialogFragment : PreferenceDialogFragmentCompat() {

        private lateinit var customizeView: IconShapeCustomizeView

        override fun onStart() {
            super.onStart()
            (dialog as AlertDialog?)?.applyAccent()
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            builder.setNeutralButton(R.string.color_presets) { _, _ ->
                dismiss()
                (preference as IconShapePreference).forceShowCustomize(false)
            }
        }

        override fun onCreateDialogView(context: Context?): View {
            return super.onCreateDialogView(context).apply {
                customizeView = findViewById(R.id.customizeView)
            }
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                IconShapeManager.getInstance(context!!).iconShape = customizeView.currentShape
            }
        }

        companion object {

            fun newInstance(key: String) = CustomizeDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_KEY, key)
                }
            }
        }
    }
}
