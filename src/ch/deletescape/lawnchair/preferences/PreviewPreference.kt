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
