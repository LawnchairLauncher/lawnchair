package ch.deletescape.lawnchair.views

import android.content.Context
import android.support.animation.FloatValueHolder
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.SearchUiManager

class BlankSearchLayout(context: Context, attrs: AttributeSet?) : View(context, attrs), SearchUiManager {

    private val launcher = Launcher.getLauncher(context)
    private val spring = SpringAnimation(FloatValueHolder()).setSpring(SpringForce(0f))!!

    var topMargin = 0
        set(value) {
            if (value != field) {
                field = value
                layoutParams.height = value
            }
        }

    override fun initialize(appsList: AlphabeticalAppsList, recyclerView: AllAppsRecyclerView) {
        recyclerView.isVerticalFadingEdgeEnabled = true
    }

    override fun getSpringForFling() = spring

    override fun refreshSearchResult() {

    }

    override fun reset() {

    }

    override fun preDispatchKeyEvent(keyEvent: KeyEvent?) {

    }

    override fun addOnScrollRangeChangeListener(listener: SearchUiManager.OnScrollRangeChangeListener) {
        launcher.hotseat.addOnLayoutChangeListener({ _, _, top, _, bottom, _, _, _, _ ->
            listener.onScrollRangeChanged(if (launcher.deviceProfile.isVerticalBarLayout) bottom else top)
        })
    }

    override fun startSearch() {

    }

    override fun startAppsSearch() {
        
    }
}