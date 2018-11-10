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
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R

class PreviewPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var previewView: View? = null
    private var layoutId = 0

    init {
        layoutResource = R.layout.preference_preview
        val ta = context.obtainStyledAttributes(attrs, R.styleable.PreviewPreference)
        layoutId = ta.getResourceId(R.styleable.PreviewPreference_previewLayout, 0)
        ta.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val parent = holder.itemView as ViewGroup
        parent.removeAllViews()
        parent.addView(getPreviewView(parent))
    }

    private fun getPreviewView(parent: ViewGroup): View {
        if (previewView == null)
            previewView = LayoutInflater.from(context).inflate(layoutId, parent, false)
        return previewView!!.also { (it.parent as? ViewGroup)?.removeView(previewView) }
    }

    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        isVisible = isEnabled
    }
}
