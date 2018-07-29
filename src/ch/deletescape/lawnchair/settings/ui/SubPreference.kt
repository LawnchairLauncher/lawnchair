package ch.deletescape.lawnchair.settings.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import ch.deletescape.lawnchair.preferences.StyledIconPreference
import com.android.launcher3.R

class SubPreference(context: Context, attrs: AttributeSet) : StyledIconPreference(context, attrs), View.OnLongClickListener {

    private var mContent: Int = 0
    private var mLongClickContent: Int = 0
    private var mLongClick: Boolean = false
    private var mHasPreview: Boolean = false

    val content: Int
        get() = if (mLongClick) mLongClickContent else mContent

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SubPreference)
        for (i in a.indexCount - 1 downTo 0) {
            val attr = a.getIndex(i)
            when (attr) {
                R.styleable.SubPreference_content -> mContent = a.getResourceId(attr, 0)
                R.styleable.SubPreference_longClickContent -> mLongClickContent = a.getResourceId(attr, 0)
                R.styleable.SubPreference_hasPreview -> mHasPreview = a.getBoolean(attr, false)
            }
        }
        a.recycle()
        fragment = SettingsActivity.SubSettingsFragment::class.java.name
    }

    override fun getExtras(): Bundle {
        val b = Bundle(2)
        b.putString(SettingsActivity.SubSettingsFragment.TITLE, title as String)
        b.putInt(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID, content)
        b.putBoolean(SettingsActivity.SubSettingsFragment.HAS_PREVIEW, hasPreview())
        return b
    }

    fun hasPreview(): Boolean {
        return mHasPreview
    }

    override fun onClick() {
        mLongClick = false
        super.onClick()
    }

    override fun onLongClick(view: View): Boolean {
        if (mLongClickContent != 0) {
            mLongClick = true
            super.onClick()
            return true
        }
        return false
    }
}