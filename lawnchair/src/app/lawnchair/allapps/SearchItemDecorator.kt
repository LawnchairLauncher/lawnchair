package app.lawnchair.allapps

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.search.SearchAdapterItem
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsContainerView

class SearchItemDecorator(private val appsView: AllAppsContainerView) : RecyclerView.ItemDecoration() {
    private val context = appsView.context
    private val resources = context.resources

    private val searchDecorationPadding = resources.getDimensionPixelSize(R.dimen.search_decoration_padding)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.inset(0, searchDecorationPadding)
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapterItems = appsView.apps.adapterItems
        val searchAdapterProvider = appsView.searchAdapterProvider
        parent.children.forEach { child ->
            val adapterPosition = parent.getChildAdapterPosition(child)
            if (adapterPosition >= 0 && adapterPosition < adapterItems.size) {
                val adapterItem = adapterItems[adapterPosition]
                val background = (adapterItem as? SearchAdapterItem)?.background
                if (background != null) {
                    val isHighlightedItem = child == searchAdapterProvider.highlightedItem
                    val inputHasFocus = appsView.searchUiManager.editText?.hasFocus() == true
                    val isFocused = isHighlightedItem && inputHasFocus
                    background.draw(c, child, isFocused)
                }
            }
        }
    }
}
