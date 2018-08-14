package ch.deletescape.lawnchair.views

import android.content.Context
import android.support.animation.FloatValueHolder
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.SearchUiManager

class BlankSearchLayout(context: Context, attrs: AttributeSet?) : View(context, attrs), SearchUiManager {

    var topMargin = 0
        set(value) {
            if (value != field) {
                field = value
                layoutParams.height = value
            }
        }

    override fun initialize(containerView: AllAppsContainerView) {
        containerView.isVerticalFadingEdgeEnabled = false
    }

    override fun resetSearch() {

    }

    override fun preDispatchKeyEvent(keyEvent: KeyEvent?) {

    }

    override fun startSearch() {

    }
}
