package app.lawnchair.qsb

import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
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
import androidx.lifecycle.lifecycleScope
import app.lawnchair.HeadlessWidgetsManager
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.subscribeBlocking
import app.lawnchair.qsb.providers.AppSearch
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.util.pendingIntent
import app.lawnchair.util.recursiveChildren
import app.lawnchair.util.repeatOnAttached
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.BaseActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class QsbLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private lateinit var gIcon: ImageView
    private lateinit var micIcon: AssistantIconView
    private lateinit var lensIcon: ImageView
    private lateinit var inner: FrameLayout
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var preferenceManager2: PreferenceManager2
    private var searchPendingIntent: PendingIntent? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onFinishInflate() {
        super.onFinishInflate()

        gIcon = ViewCompat.requireViewById<ImageView>(this, R.id.g_icon)
        micIcon = ViewCompat.requireViewById(this, R.id.mic_icon)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_icon)
        inner = ViewCompat.requireViewById(this, R.id.inner)
        preferenceManager = PreferenceManager.getInstance(context)
        preferenceManager2 = PreferenceManager2.getInstance(context)

        val searchProvider = getSearchProvider(context, preferenceManager2)
        setUpBackground()
        clipIconRipples()

        val isGoogle = searchProvider == Google ||
                searchProvider == GoogleGo

        val supportsLens = searchProvider == Google

        preferenceManager2.themedHotseatQsb.subscribeBlocking(scope = viewAttachedScope) { themed ->
            setUpBackground(themed)

            val iconRes = if (themed) searchProvider.themedIcon else searchProvider.icon

            // The default search icon should always be themed
            gIcon.setThemedIconResource(
                resId = iconRes,
                themed = themed || iconRes == R.drawable.ic_qsb_search,
                method = searchProvider.themingMethod
            )

            micIcon.setIcon(isGoogle, themed)
            if (supportsLens) {
                lensIcon.setThemedIconResource(R.drawable.ic_lens_color, themed)
            }
        }

        if (supportsLens) setUpLensIcon()

        setOnClickListener {
            val launcher = context.launcher
            launcher.lifecycleScope.launch {
                searchProvider.launch(launcher)
            }
        }
        if (searchProvider == Google) {
            repeatOnAttached {
                val forceWebsite = preferenceManager2.hotseatQsbForceWebsite.get()
                forceWebsite
                    .flatMapLatest {
                        if (it) Google.getSearchIntent(context) else flowOf(null)
                    }
                    .collect()
            }
            subscribeGoogleSearchWidget()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val dp = activity.deviceProfile
        val cellWidth = DeviceProfile.calculateCellWidth(
            requestedWidth,
            dp.cellLayoutBorderSpacePx.x,
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

    private fun subscribeGoogleSearchWidget() {
        val info = QsbContainerView.getSearchWidgetProviderInfo(context, Google.packageName) ?: return
        context.launcherNullable?.lifecycleScope?.launch {
            val headlessWidgetsManager = HeadlessWidgetsManager.INSTANCE.get(context)
            headlessWidgetsManager.subscribeUpdates(info, "hotseatWidgetId")
                .collect { findSearchIntent(it) }
        }
    }

    private fun findSearchIntent(view: AppWidgetHostView) {
        view.measure(
            MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY)
        )
        searchPendingIntent = view.recursiveChildren
            .filter { it.pendingIntent != null }
            .sortedByDescending { it.measuredWidth * it.measuredHeight }
            .firstOrNull()
            ?.pendingIntent
    }

    private fun setUpLensIcon() {
        val lensIntent = Intent.makeMainActivity(ComponentName(LENS_PACKAGE, LENS_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (context.packageManager.resolveActivity(lensIntent, 0) == null) return

        with(lensIcon) {
            isVisible = true
            setOnClickListener {
                runCatching { context.startActivity(lensIntent) }
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
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY = "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"

        fun getSearchProvider(
            context: Context,
            preferenceManager: PreferenceManager2
        ): QsbSearchProvider {
            val provider = preferenceManager.hotseatQsbProvider.firstBlocking()

            return if (provider == AppSearch ||
                resolveIntent(context, provider.createSearchIntent()) ||
                resolveIntent(context, provider.createWebsiteIntent())
            ) provider else AppSearch
        }

        fun resolveIntent(context: Context, intent: Intent): Boolean =
            context.packageManager.resolveActivity(intent, 0) != null

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
