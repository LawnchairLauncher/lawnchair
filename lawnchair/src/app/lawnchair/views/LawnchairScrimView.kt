package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ScrimView

class LawnchairScrimView(context: Context, attrs: AttributeSet?) : ScrimView(context, attrs) {

    private val drawerOpacity = PreferenceManager.getInstance(context).drawerOpacity

    override fun isScrimDark(): Boolean {
        return if (drawerOpacity.get() <= 0.3f) {
            !Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
        } else {
            super.isScrimDark()
        }
    }
}
