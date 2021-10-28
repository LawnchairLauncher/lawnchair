package app.lawnchair.qsb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.BaseActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorListeners.forSuccessCallback
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext

class QsbLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private lateinit var gIcon: ImageView
    private lateinit var micIcon: AssistantIconView
    private lateinit var lensIcon: ImageView
    private lateinit var inner: FrameLayout
    private lateinit var preferenceManager: PreferenceManager

    override fun onFinishInflate() {
        super.onFinishInflate()

        gIcon = ViewCompat.requireViewById<ImageView>(this, R.id.g_icon)
        micIcon = ViewCompat.requireViewById(this, R.id.mic_icon)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_icon)
        inner = ViewCompat.requireViewById(this, R.id.inner)
        preferenceManager = PreferenceManager.getInstance(context)

        setUpMainSearch()
        setUpBackground()
        clipIconRipples()

        val searchPackage = getSearchPackageName(context)
        val isGoogle = searchPackage == GOOGLE_PACKAGE
        if (isGoogle) {
            preferenceManager.themedHotseatQsb.subscribeValues(this) {
                setThemed(it)
            }
            setUpLensIcon()
        } else {
            micIcon.setIcon(isGoogle = false, themed = false)
            with(gIcon) {
                setImageResource(R.drawable.ic_qsb_search)
                setColorFilter(Themes.getColorAccent(context))
            }
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
        val widthReduction = cellWidth - iconSize
        val width = requestedWidth - widthReduction
        setMeasuredDimension(width, height)

        children.forEach { child ->
            measureChildWithMargins(child, widthMeasureSpec, widthReduction, heightMeasureSpec, 0)
        }
    }

    private fun setThemed(themed: Boolean) {
        setUpBackground(themed)
        gIcon.setThemedIconResource(R.drawable.ic_super_g_color, themed)
        micIcon.setIcon(isGoogle = true, themed)
        lensIcon.setThemedIconResource(R.drawable.ic_lens_color, themed)
    }

    private fun setUpMainSearch() {
        setOnClickListener {
            val searchPackage = getSearchPackageName(context)
            val intent = Intent("android.search.action.GLOBAL_SEARCH")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setPackage(searchPackage)
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
            } else {
                val launcher = context.launcher
                launcher.stateManager.goToState(LauncherState.ALL_APPS, true, forSuccessCallback {
                    launcher.appsView.searchUiManager.editText?.showKeyboard()
                })
            }
        }
    }

    private fun setUpLensIcon() {
        val lensIntent = Intent.makeMainActivity(ComponentName(LENS_PACKAGE, LENS_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (context.packageManager.resolveActivity(lensIntent, 0) == null) return

        with(lensIcon) {
            isVisible = true
            setOnClickListener {
                context.startActivity(lensIntent)
            }
        }
    }

    private fun clipIconRipples() {
        val cornerRadius = getCornerRadius(context, preferenceManager)
        listOf(lensIcon, micIcon).forEach {
            it.clipToOutline = cornerRadius > 0
            it.background = PaintDrawable(Color.TRANSPARENT).apply {
                setCornerRadius(cornerRadius)
            }
        }
    }

    private fun setUpBackground(themed: Boolean = false) {
        val cornerRadius = getCornerRadius(context, preferenceManager)
        val color = if (themed) Themes.getColorBackgroundFloating(context) else Themes.getAttrColor(context, R.attr.qsbFillColor)
        with (inner) {
            clipToOutline = cornerRadius > 0
            background = PaintDrawable(color).apply {
                setCornerRadius(cornerRadius)
            }
        }
    }

    companion object {
        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY = "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"

        fun getSearchPackageName(context: Context): String {
            val searchPackage = QsbContainerView.getSearchWidgetPackageName(context)
            if (!searchPackage.isNullOrEmpty()) {
                return searchPackage
            }
            if (resolveSearchIntent(context, GOOGLE_PACKAGE)) {
                return GOOGLE_PACKAGE
            }
            return ""
        }

        private fun resolveSearchIntent(context: Context, searchPackage: String): Boolean {
            val intent = Intent("android.search.action.GLOBAL_SEARCH")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setPackage(searchPackage)
            return context.packageManager.resolveActivity(intent, 0) != null
        }

        private fun getCornerRadius(
            context: Context,
            preferenceManager: PreferenceManager
        ): Float {
            val resources = context.resources
            val qsbWidgetHeight = resources.getDimension(R.dimen.qsb_widget_height)
            val qsbWidgetPadding = resources.getDimension(R.dimen.qsb_widget_vertical_padding)
            val innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding
            return innerHeight / 2 * preferenceManager.hotseatQsbCornerRadius.get()
        }
    }
}
