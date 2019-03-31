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

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.support.annotation.Keep
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceGroup
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import com.android.launcher3.R
import java.lang.IllegalStateException

@Keep
class AdvancedPreferencesGroup(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): PreferenceGroup(context, attrs, defStyleAttr, defStyleRes),
        ValueAnimator.AnimatorUpdateListener,
        ControlledPreference by ControlledPreference.Delegate(context, attrs) {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val hasSummary: Boolean
    private var bound: Boolean = false

    init {
        layoutResource = R.layout.preference_expandable

        val ta = context.obtainStyledAttributes(attrs, R.styleable.AdvancedPreferencesGroup)
        val topic = ta.getString(R.styleable.AdvancedPreferencesGroup_topic) ?: "Bug"
        ta.recycle()

        title = if(title.isNullOrEmpty()) context.getString(R.string.advanced_settings, topic) else title
        hasSummary = !summary.isNullOrEmpty()
    }
    private val caretDrawable = CaretDrawable(context).apply { caretProgress = CaretDrawable.PROGRESS_CARET_POINTING_DOWN }
    private var caretView: ImageView? = null
    private var summaryView: View? = null
    private val preferences = mutableSetOf<Preference>()
    var expanded = false
        set(value) {
            if (value != field) {
                field = value
                updateUi()
            }
        }
    private var caretPointingUp = expanded
        set(value) {
            animator?.cancel()
            field = value
        }
    private var animator: ValueAnimator? = null
        set(value) {
            field?.cancel()
            field = value
            field?.addUpdateListener(this)
            field?.start()
        }

    fun contains(key: String?) = preferences.any { it.key == key }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        if (preferences.size <= 1) {
            // Expand and remove ourselves if there is only one preference contained inside
            for (pref in preferences) {
                // hack to ensure proper positioning of the preference
                super.addPreference(pref)
                parent?.addPreference(pref)
            }
            parent?.removePreference(this)
            return
        }
        super.onBindViewHolder(holder)
        summaryView = holder.findViewById(android.R.id.summary)
        summaryView?.isVisible = !expanded
        caretView = holder.findViewById(R.id.caretImageView) as? ImageView
        caretView?.setImageDrawable(caretDrawable)
        caretDrawable.caretProgress = if (caretPointingUp) CaretDrawable.PROGRESS_CARET_POINTING_UP else CaretDrawable.PROGRESS_CARET_POINTING_DOWN
    }

    override fun onClick() {
        expanded = !expanded
        animateCaretPointingUp(expanded)
        super.onClick()
    }

    override fun addPreference(preference: Preference): Boolean {
        if (preference is ControlledPreference && preference.controller?.isVisible == false) {
            return false
        }
        preferences.add(preference)
        updateSummary()
        return !expanded || super.addPreference(preference)
    }

    override fun removePreference(preference: Preference?): Boolean {
        preferences.remove(preference)
        updateSummary()
        return super.removePreference(preference)
    }

    private fun animateCaretPointingUp(pointingUp: Boolean) {
        caretPointingUp = pointingUp
        animator = ObjectAnimator.ofFloat(caretDrawable.caretProgress, if (pointingUp) CaretDrawable.PROGRESS_CARET_POINTING_UP else CaretDrawable.PROGRESS_CARET_POINTING_DOWN)
                .setDuration(200)
    }

    override fun onAnimationUpdate(animator: ValueAnimator) {
        caretDrawable.caretProgress = animator.animatedValue as Float
    }

    private fun updateUi() {
        if (expanded) {
            for (pref in preferences) {
                super.addPreference(pref)
            }
        } else {
            super.removeAll()
        }
        summaryView?.isVisible = !expanded
        notifyChanged()
        updateSummary()
    }

    private fun updateSummary() {
        summary = if (hasSummary) {
            summary
        } else {
            var first = true
            var str = ""
            for (pref in preferences) {
                if (!first) str += ", "
                first = false
                str += pref.title
            }
            "$str."
        }
    }
}