package ch.deletescape.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import ch.deletescape.lawnchair.AppInfo
import ch.deletescape.lawnchair.BaseRecyclerViewFastScrollBar
import ch.deletescape.lawnchair.BubbleTextView
import ch.deletescape.lawnchair.FastBitmapDrawable

class AllAppsIconRowView(context: Context, attrs: AttributeSet) :
        LinearLayout(context, attrs), BaseRecyclerViewFastScrollBar.FastScrollFocusableView {

    lateinit var icon: BubbleTextView
    lateinit var title: TextView

    var text: CharSequence?
        get() = title.text
        set(value) {
            title.text = value
        }

    var textColor: Int
        get() = title.currentTextColor
        set(value) {
            title.setTextColor(value)
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        icon = findViewById(android.R.id.icon)
        title = findViewById(android.R.id.title)
    }

    override fun setFastScrollFocusState(focusState: FastBitmapDrawable.State?, animated: Boolean) {
        icon.setFastScrollFocusState(focusState, animated)
    }

    fun applyFromApplicationInfo(appInfo: AppInfo) {
        icon.applyFromApplicationInfo(appInfo, false)
    }

}