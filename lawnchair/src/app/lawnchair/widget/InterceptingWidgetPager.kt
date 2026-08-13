package app.lawnchair.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Thin [ViewPager2] host. Touch paging is handled by [WidgetStackView] so nested widgets cannot
 * steal the stack gesture.
 */
class InterceptingWidgetPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    val pager: ViewPager2 = ViewPager2(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isSaveEnabled = false
        offscreenPageLimit = 2
        overScrollMode = OVER_SCROLL_NEVER
        isUserInputEnabled = false
    }

    var isVertical: Boolean
        get() = pager.orientation == ViewPager2.ORIENTATION_VERTICAL
        set(value) {
            val next = if (value) {
                ViewPager2.ORIENTATION_VERTICAL
            } else {
                ViewPager2.ORIENTATION_HORIZONTAL
            }
            if (pager.orientation != next) {
                pager.orientation = next
            }
        }

    var currentItem: Int
        get() = pager.currentItem
        set(value) {
            pager.setCurrentItem(value, false)
        }

    var offscreenPageLimit: Int
        get() = pager.offscreenPageLimit
        set(value) {
            pager.offscreenPageLimit = value
        }

    var adapter: RecyclerView.Adapter<*>?
        get() = pager.adapter
        set(value) {
            pager.adapter = value
            pager.post { configureInnerRecycler() }
        }

    init {
        addView(pager)
        pager.post { configureInnerRecycler() }
    }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        pager.setCurrentItem(item, smoothScroll)
    }

    fun registerOnPageChangeCallback(callback: ViewPager2.OnPageChangeCallback) {
        pager.registerOnPageChangeCallback(callback)
    }

    private fun configureInnerRecycler() {
        val rv = pager.getChildAt(0) as? RecyclerView ?: return
        rv.overScrollMode = OVER_SCROLL_NEVER
        rv.isNestedScrollingEnabled = false
        // DefaultItemAnimator fades pages in/out on bind — looks like a dissolve on every swipe.
        rv.itemAnimator = null
    }
}
