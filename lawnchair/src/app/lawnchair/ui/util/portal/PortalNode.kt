package app.lawnchair.ui.util.portal

import android.view.View

interface PortalNode {
    fun addView(view: View)
    fun removeView(view: View)
}
