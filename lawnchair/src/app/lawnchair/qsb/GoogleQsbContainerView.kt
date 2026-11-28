package app.lawnchair.qsb

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import app.lawnchair.qsb.providers.Google
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.qsb.QsbWidgetHostView
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.launch

class GoogleQsbContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : QsbContainerView(context, attrs, defStyleAttr) {

    init {
        val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.qsb_widget_vertical_padding)
        setPaddingUnchecked(0, verticalPadding, 0, verticalPadding)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.qsb_widget_vertical_padding)
        super.setPaddingUnchecked(0, verticalPadding, 0, verticalPadding)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val activity: ActivityContext? = ActivityContext.lookupContextNoThrow(context)
        val dp = activity?.deviceProfile
        if (dp == null || !dp.deviceProperties.isPhone) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val cellWidth = DeviceProfile.calculateCellWidth(
            requestedWidth,
            dp.cellLayoutBorderSpacePx.x,
            dp.numShownHotseatIcons,
        )
        val iconSize = (dp.iconSizePx * 0.92f).toInt()
        val widthReduction = cellWidth - iconSize
        val width = requestedWidth - widthReduction
        setMeasuredDimension(width, height)

        children.forEach { child ->
            measureChildWithMargins(child, widthMeasureSpec, widthReduction, heightMeasureSpec, 0)
        }
    }

    class QsbFragment : QsbContainerView.QsbFragment() {

        init {
            mKeyWidgetId = "google_hotseat_qsb_widget_id"
        }

        override fun createHost(): QsbWidgetHost {
            return QsbWidgetHost(
                context,
                GOOGLE_HOTSEAT_QSB_WIDGET_HOST_ID,
                { QsbWidgetHostView(it) },
                this::rebindFragment,
            )
        }

        override fun getSearchWidgetProvider(): AppWidgetProviderInfo? {
            val ctx = context ?: return null
            return getSearchWidgetProviderInfo(ctx, Google.packageName)
                ?: super.getSearchWidgetProvider()
        }

        override fun createBindOptions(): Bundle {
            val opts = super.createBindOptions()
            opts.putString("attached-launcher-identifier", context?.packageName)
            opts.putBoolean("com.google.android.apps.gsa.widget.PREINSTALLED", true)
            return opts
        }

        override fun getDefaultView(container: ViewGroup?, showSetupIcon: Boolean): View {
            val view = super.getDefaultView(container, showSetupIcon)
            view.findViewById<View>(R.id.btn_qsb_search)?.setOnClickListener {
                val launcher = Launcher.getLauncher(context)
                launcher.lifecycleScope.launch {
                    Google.launch(launcher, forceWebsite = false)
                }
            }
            return view
        }

        override fun isQsbEnabled(): Boolean = true

        companion object {
            private const val GOOGLE_HOTSEAT_QSB_WIDGET_HOST_ID = 1029
        }
    }
}
