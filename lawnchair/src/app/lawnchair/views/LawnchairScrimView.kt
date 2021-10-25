package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ScrimView

class LawnchairScrimView(context: Context, attrs: AttributeSet?) : ScrimView(context, attrs) {

    private val drawerOpacity = PreferenceManager.getInstance(context).drawerOpacity.get()

    override fun updateSysUiColors() {
        val threshold = STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD
        val forceChange = visibility == VISIBLE &&
                alpha > threshold && Color.alpha(mBackgroundColor) / (255f * drawerOpacity) > threshold
        with(systemUiController) {
            if (forceChange) {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, !isScrimDark)
            } else {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, 0)
            }
        }
    }

    override fun isScrimDark() = if (drawerOpacity <= 0.3f) {
        !Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
    } else {
        super.isScrimDark()
    }
}
