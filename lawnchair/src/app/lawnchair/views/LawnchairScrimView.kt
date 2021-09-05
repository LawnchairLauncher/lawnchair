package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.quickstep.views.ShelfScrimView

class LawnchairScrimView(context: Context, attrs: AttributeSet?) : ShelfScrimView(context, attrs) {

    init {
        val prefs = PreferenceManager.getInstance(context)
        prefs.drawerOpacity.subscribeValues(this) { opacity ->
            mEndAlpha = (opacity * 255).toInt()
            mIsScrimDark = if (opacity <= 0.3f) {
                !Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText)
            } else {
                ColorUtils.calculateLuminance(mEndScrim) < 0.5f
            }
        }
    }
}
