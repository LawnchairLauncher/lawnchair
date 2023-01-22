package app.lawnchair.nexuslauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.qsb.QsbWidgetHostView
import com.android.launcher3.views.BaseDragLayer.TouchCompleteListener
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem

open class SmartSpaceHostView(context: Context) : QsbWidgetHostView(context), OnLongClickListener, TouchCompleteListener {
    private val mLauncher: Launcher by lazy { Launcher.getLauncher(context) }
    @Suppress("LeakingThis")
    private val mLongPressHelper: CheckLongPressHelper = CheckLongPressHelper(this, this)

    override fun getErrorView(): View {
        return SmartspaceQsb.getDateView(this)
    }

    override fun onLongClick(view: View): Boolean {
        if (!hasSettings(view.context)) {
            return false
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val pos = Rect()
        mLauncher.dragLayer.getDescendantRectRelativeToSelf(this, pos)
        val centerPos = RectF()
        centerPos.right = pos.exactCenterX()
        centerPos.left = centerPos.right
        centerPos.top = 0f
        centerPos.bottom = pos.bottom.toFloat()
        centerPos.bottom = findBottomRecur(this, pos.top, pos).toFloat().coerceAtMost(centerPos.bottom)
        val item = OptionItem(view.context,
            R.string.smartspace_preferences,
            R.drawable.ic_smartspace_preferences,
            NexusLauncherEnum.SMARTSPACE_TAP_OR_LONGPRESS
        ) { v: View -> openSettings(v) }
        OptionsPopupView.show(mLauncher, centerPos, listOf(item), true)
        return true
    }

    private fun findBottomRecur(view: View, max: Int, tempRect: Rect): Int {
        var ret = max
        if (view.visibility != VISIBLE) {
            return ret
        }
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                ret = findBottomRecur(view.getChildAt(i), ret, tempRect).coerceAtLeast(ret)
            }
        }
        if (!view.willNotDraw()) {
            mLauncher.dragLayer.getDescendantRectRelativeToSelf(view, tempRect)
            return ret.coerceAtLeast(tempRect.bottom)
        }
        return ret
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        mLongPressHelper.onTouchEvent(ev)
        return mLongPressHelper.hasPerformedLongPress()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        mLongPressHelper.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        mLongPressHelper.cancelLongPress()
    }

    override fun onTouchComplete() {
        if (!mLongPressHelper.hasPerformedLongPress()) {
            mLongPressHelper.cancelLongPress()
        }
    }

    private fun openSettings(v: View): Boolean {
        v.context.startActivity(createSettingsIntent())
        return true
    }

    companion object {
        private const val SETTINGS_INTENT_ACTION = "com.google.android.apps.gsa.smartspace.SETTINGS"
        fun hasSettings(context: Context): Boolean {
            val info = context.packageManager
                .resolveActivity(createSettingsIntent(), 0)
            return info != null
        }

        fun createSettingsIntent(): Intent {
            return Intent(SETTINGS_INTENT_ACTION)
                .setPackage(SmartspaceQsb.WIDGET_PACKAGE_NAME)
                .setFlags(
                    Intent.FLAG_RECEIVER_FOREGROUND
                            or Intent.FLAG_ACTIVITY_NO_HISTORY
                            or Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
        }
    }

}

enum class NexusLauncherEnum(private val mId: Int) : EventEnum {
    SMARTSPACE_TAP_OR_LONGPRESS(520);

    override fun getId(): Int {
        return mId
    }
}
