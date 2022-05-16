package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView
import com.patrykmichalik.preferencemanager.firstBlocking

class SmartspaceViewContainer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, private val previewMode: Boolean = false
) : FrameLayout(context, attrs) {

    private val longPressHelper = CheckLongPressHelper(this) { performLongClick() }
    private val smartspaceView: View

    init {
        val prefs = PreferenceManager2.getInstance(context)
        val inflater = LayoutInflater.from(context)
        smartspaceView = if (prefs.enableEnhancedSmartspace.firstBlocking()) {
            val view = inflater.inflate(R.layout.smartspace_enhanced, this, false) as BcSmartspaceView
            view.previewMode = previewMode
            setOnLongClickListener {
                openOptions()
                true
            }
            view
        } else {
            inflater.inflate(R.layout.smartspace_legacy, this, false)
        }
        addView(smartspaceView)
    }

    private fun openOptions() {
        if (previewMode) return
        if (!PreferenceManager2.getInstance(context).enableEnhancedSmartspace.firstBlocking()) return

        val launcher = context.launcher
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(smartspaceView, pos)
        OptionsPopupView.show(launcher, RectF(pos), listOf(getCustomizeOption()), true)
    }
    
    private fun getCustomizeOption() = OptionsPopupView.OptionItem(
        context, R.string.customize_button_text, R.drawable.ic_setting,
        StatsLogManager.LauncherEvent.IGNORE
    ) {
        context.startActivity(Intent(context, SmartspacePreferencesShortcut::class.java))
        true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }
}
