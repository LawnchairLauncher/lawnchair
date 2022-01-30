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
    defStyleAttr: Int = 0
) : OverviewActionsView<TaskOverlayFactoryImpl.OverlayUICallbacks>(context, attrs, defStyleAttr) {

    private val prefs = PreferenceManager.getInstance(context)

    private lateinit var container: LinearLayout
    private lateinit var screenshotAction: Button
    private lateinit var shareAction: Button
    private lateinit var lensAction: Button
    private lateinit var clearAllAction: Button

    override fun onFinishInflate() {
        super.onFinishInflate()

        container = ViewCompat.requireViewById(this, R.id.action_buttons)
        clearAllAction = ViewCompat.requireViewById(this, R.id.action_clear_all)
        lensAction = ViewCompat.requireViewById(this, R.id.action_lens)
        screenshotAction = ViewCompat.requireViewById(this, R.id.action_screenshot)
        shareAction = ViewCompat.requireViewById(this, R.id.action_share)
        
        lensAction.setOnClickListener { mCallbacks?.onLens() }

        prefs.recentsActionClearAll.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionLens.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionScreenshot.subscribeChanges(this, ::updateVisibilities)
        prefs.recentsActionShare.subscribeChanges(this, ::updateVisibilities)

        updateVisibilities()
    }

    private fun updateVisibilities() {
        val buttons = mutableListOf<View>()
        if (prefs.recentsActionScreenshot.get() && !isOnePlusStock) {
            buttons.add(screenshotAction)
        }
        if (prefs.recentsActionShare.get()) {
            buttons.add(shareAction)
        }
        if (prefs.recentsActionLens.get()) {
            val lensIntent = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            val lensAvailable = lensIntent != null
            if (lensAvailable) {
                buttons.add(lensAction)
            }
        }
        if (prefs.recentsActionClearAll.get()) {
            buttons.add(clearAllAction)
        }
        container.removeAllViews()
        container.addView(createSpace())
        buttons.forEach { view ->
            view.isVisible = true
            container.addView(view)
            container.addView(createSpace())
        }
    }

    private fun createSpace(): View {
        return Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1).apply { weight = 1f }
        }
    }
}
