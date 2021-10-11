package app.lawnchair.qsb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import com.android.launcher3.BaseActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.views.ActivityContext

class QsbLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private lateinit var assistantIcon: AssistantIconView
    private lateinit var lensIcon: ImageView

    override fun onFinishInflate() {
        super.onFinishInflate()
        assistantIcon = ViewCompat.requireViewById(this, R.id.mic_icon)
        assistantIcon.setIcon()
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_icon)

        val searchPackage = QsbContainerView.getSearchWidgetPackageName(context)
        setOnClickListener {
            val intent = Intent("android.search.action.GLOBAL_SEARCH")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setPackage(searchPackage)
            context.startActivity(intent)
        }
        if (searchPackage == GOOGLE_PACKAGE) {
            setupLensIcon()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val dp = activity.deviceProfile
        val cellWidth = DeviceProfile.calculateCellWidth(
            requestedWidth,
            dp.cellLayoutBorderSpacingPx,
            dp.numShownHotseatIcons
        )
        val iconSize = (dp.iconSizePx * 0.92f).toInt()
        val width = requestedWidth - (cellWidth - iconSize)
        setMeasuredDimension(width, height)

        children.forEach { child ->
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }
    }

    private fun setupLensIcon() {
        val lensIntent = Intent.makeMainActivity(ComponentName(LENS_PACKAGE, LENS_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (context.packageManager.resolveActivity(lensIntent, 0) == null) return

        lensIcon.isVisible = true
        lensIcon.setImageResource(R.drawable.ic_lens_color)
        lensIcon.setOnClickListener {
            context.startActivity(lensIntent)
        }
    }

    companion object {
        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY = "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"
    }
}
