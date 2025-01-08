package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import app.lawnchair.search.LawnchairSearchUiDelegate
import com.android.launcher3.allapps.LauncherAllAppsContainerView

class SearchContainerView(context: Context?, attrs: AttributeSet?) : LauncherAllAppsContainerView(context, attrs) {

    override fun createSearchUiDelegate() = LawnchairSearchUiDelegate(this)
}
