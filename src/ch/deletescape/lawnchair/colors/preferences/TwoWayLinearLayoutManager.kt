package ch.deletescape.lawnchair.colors.preferences

import android.content.Context
import android.support.v7.widget.LinearLayoutManager

class TwoWayLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun canScrollHorizontally(): Boolean = false
    override fun canScrollVertically(): Boolean = false
}