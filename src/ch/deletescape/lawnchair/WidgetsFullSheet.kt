package ch.deletescape.lawnchair

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import ch.deletescape.lawnchair.views.TopRoundedCornerView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.widget.WidgetsBottomSheet
import com.android.launcher3.widget.WidgetsRecyclerView

class WidgetsFullSheet(context: Context, attrs: AttributeSet?) : WidgetsBottomSheet(context, attrs) {

    private lateinit var launcher: Launcher

    private val container by lazy { findViewById<TopRoundedCornerView>(R.id.topRoundedCornerView) }
    private val recyclerView by lazy { findViewById<WidgetsRecyclerView>(R.id.widgets_list_view) }

    override fun onWidgetsBound() {

    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            mNoIntercept = true
            val scrollbar = recyclerView.scrollBar
            if (!launcher.dragLayer.isEventOverView(scrollbar, ev)) {
                if (recyclerView.shouldContainerScroll(ev, launcher.dragLayer)) {
                    mNoIntercept = false
                }
            }
        }
        return super.onControllerInterceptTouchEvent(ev)
    }

    override fun onCloseComplete() {
        super.onCloseComplete()
        container.removeAllViews()
    }

    override fun setInsets(insets: Rect) {
        (layoutParams as MarginLayoutParams).topMargin = insets.top +
                resources.getDimensionPixelSize(R.dimen.bg_round_rect_radius)
        super.setInsets(insets)
    }

    companion object {

        fun show(launcher: Launcher) {
            val sheet = launcher.layoutInflater
                    .inflate(R.layout.widgets_full_sheet, launcher.dragLayer, false) as WidgetsFullSheet
            sheet.launcher = launcher
            sheet.container.addView(launcher.widgetsView.apply {
                visibility = View.VISIBLE
                findViewById<View>(R.id.main_content).visibility = View.VISIBLE
            })
            sheet.populateAndShow(null)
        }
    }
}