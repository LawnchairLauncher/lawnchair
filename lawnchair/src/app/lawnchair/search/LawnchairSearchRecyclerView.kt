package app.lawnchair.search

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.R
import com.android.launcher3.allapps.SearchRecyclerView

class LawnchairSearchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : SearchRecyclerView(context, attrs, defStyleAttr, defStyleRes) {

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        val extraPadding = resources.getDimensionPixelSize(R.dimen.search_result_recycler_view_padding)
        super.setPadding(left + extraPadding, top, right + extraPadding, bottom)
    }
}
