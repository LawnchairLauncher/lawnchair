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

package ch.deletescape.lawnchair.groups.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.groups.AppGroupsManager
import kotlinx.android.synthetic.lawnchair.app_categorization_type_item.view.*

class AppCategorizationTypeItem(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs),
        View.OnClickListener, LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = context.lawnchairPrefs
    private val manager = prefs.appGroupsManager
    private lateinit var type: AppGroupsManager.CategorizationType

    init {
        setOnClickListener(this)

        val tintSelected = context.getColorEngineAccent()
        val tintNormal = ColorUtils.setAlphaComponent(context.getColorAttr(android.R.attr.colorControlHighlight), 255)
        val tintList = ColorStateList(arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()),
                intArrayOf(
                        tintSelected,
                        tintNormal))
        background.setTintList(tintList)

        val rippleTintList = ColorStateList(arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()),
                intArrayOf(
                        ColorUtils.setAlphaComponent(tintSelected, 31),
                        ColorUtils.setAlphaComponent(tintNormal, 31)))
        (background as RippleDrawable).setColor(rippleTintList)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        checkMark.tintDrawable(context.getColorEngineAccent())
    }

    fun setup(type: AppGroupsManager.CategorizationType, titleRes: Int, summaryRes: Int) {
        this.type = type
        title.setText(titleRes)
        summary.setText(summaryRes)
    }

    override fun onClick(v: View) {
        manager.categorizationType = type
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        checkMark.isVisible = selected
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener("pref_appsCategorizationType", this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener("pref_appsCategorizationType", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        isSelected = type == manager.categorizationType
    }
}
