package app.lawnchair.overview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.isOnePlusStock
import com.android.launcher3.R
import com.android.quickstep.views.OverviewActionsView

class LawnchairOverviewActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : OverviewActionsView<TaskOverlayFactoryImpl.OverlayUICallbacks>(context, attrs, defStyleAttr) {

    private val prefs = PreferenceManager.getInstance(context)
    private lateinit var container: LinearLayout
    private lateinit var screenshotAction: Button
    private lateinit var shareAction: Button
    private lateinit var lensAction: Button
    private lateinit var clearAllAction: Button
    private lateinit var lockedAction: Button

    override fun onFinishInflate() {
        super.onFinishInflate()

        container = ViewCompat.requireViewById(this, R.id.action_buttons)
        clearAllAction = ViewCompat.requireViewById(this, R.id.action_clear_all)
        shareAction = ViewCompat.requireViewById(this, R.id.action_share)
        lensAction = ViewCompat.requireViewById(this, R.id.action_lens)
        screenshotAction = ViewCompat.requireViewById(this, R.id.action_screenshot)
        lockedAction = ViewCompat.requireViewById(this, R.id.action_locked)

        shareAction.setOnClickListener { mCallbacks?.onShare() }
        lensAction.setOnClickListener { mCallbacks?.onLens() }

        prefs.recentsActionClearAll.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionLens.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionScreenshot.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionShare.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionLocked.subscribeChanges(this, ::updateVisibilities)
        prefs.recentActionOrder.subscribeChanges(this, ::updateVisibilities)

        updateVisibilities()
    }

    private fun updateVisibilities() {
        val order = prefs.recentActionOrder.get().split(",").map { it.toInt() }

        val buttonMap = mutableMapOf<Int, View>()
        if (prefs.recentsActionScreenshot.get() && !isOnePlusStock) {
            buttonMap[0] = screenshotAction
        }
        if (prefs.recentsActionShare.get()) {
            buttonMap[1] = shareAction
        }
        if (prefs.recentsActionLens.get() && isLensAvailable()) {
            buttonMap[2] = lensAction
        }
        if (prefs.recentsActionLocked.get()) {
            buttonMap[3] = lockedAction
        }
        if (prefs.recentsActionClearAll.get()) {
            buttonMap[4] = clearAllAction
        }

        val buttonsInOrder = order.mapNotNull { buttonMap[it] }

        container.removeAllViews()
        container.addView(createSpace())
        buttonsInOrder.forEach { view ->
            view.isVisible = true
            container.addView(view)
            container.addView(createSpace())
        }
    }

    private fun isLensAvailable(): Boolean {
        val lensIntent = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
        return lensIntent != null
    }

    private fun createSpace(): View {
        return Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1).apply { weight = 1f }
        }
    }

    override fun setClearAllClickListener(clearAllClickListener: OnClickListener?) {
        clearAllAction.setOnClickListener(clearAllClickListener)
    }
}
