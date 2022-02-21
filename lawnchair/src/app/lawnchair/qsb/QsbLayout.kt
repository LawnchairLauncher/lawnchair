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
import app.lawnchair.util.pendingIntent
import app.lawnchair.util.recursiveChildren
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.BaseActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorListeners.forSuccessCallback
import com.android.launcher3.qsb.QsbContainerView
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.preferencemanager.firstBlocking
import com.patrykmichalik.preferencemanager.onEach
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

    override fun onFinishInflate() {
        super.onFinishInflate()

        gIcon = ViewCompat.requireViewById<ImageView>(this, R.id.g_icon)
        micIcon = ViewCompat.requireViewById(this, R.id.mic_icon)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_icon)
        inner = ViewCompat.requireViewById(this, R.id.inner)
        preferenceManager = PreferenceManager.getInstance(context)
        preferenceManager2 = PreferenceManager2.getInstance(context)

        val searchProvider = getSearchProvider(context, preferenceManager2)
        val useWebsite = preferenceManager2.hotseatQsbUseWebsite.firstBlocking()
        setUpMainSearch(searchProvider, useWebsite)
        setUpBackground()
        clipIconRipples()

        val isGoogle = searchProvider == QsbSearchProvider.Google ||
                searchProvider == QsbSearchProvider.GoogleGo

        val supportsLens = searchProvider == QsbSearchProvider.Google

        preferenceManager2.themedHotseatQsb.onEach(launchIn = viewAttachedScope) { themed ->
            setUpBackground(themed)

            val iconRes = if (themed) searchProvider.themedIcon else searchProvider.icon
            gIcon.setThemedIconResource(iconRes, themed, searchProvider.themingMethod)

            micIcon.setIcon(isGoogle, themed)
            if (supportsLens) {
                lensIcon.setThemedIconResource(R.drawable.ic_lens_color, themed)
            }
        }

        if (supportsLens) setUpLensIcon()
    }

    private fun subscribeSearchWidget(searchPackage: String) {
        val info = QsbContainerView.getSearchWidgetProviderInfo(context, searchPackage) ?: return
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

    private fun setUpMainSearch(searchProvider: QsbSearchProvider, useWebsite: Boolean) {
        if (useWebsite) {
            val intent = searchProvider.createWebsiteIntent()
            setOnClickListener {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                } else {
                    val launcher = context.launcher
                    launcher.stateManager.goToState(
                        LauncherState.ALL_APPS,
                        true,
                        forSuccessCallback {
                            launcher.appsView.searchUiManager.editText?.showKeyboard()
                        }
                    )
                }
            }
            return
        }


        subscribeSearchWidget(searchProvider.packageName)
        setOnClickListener {
            val pendingIntent = searchPendingIntent
            if (pendingIntent != null) {
                val launcher = context.launcher
                launcher.startIntentSender(
                    pendingIntent.intentSender, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    0
                )
                return@setOnClickListener
            }

            val intent = searchProvider.createSearchIntent()
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
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY = "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"

        fun getSearchProvider(
            context: Context,
            preferenceManager: PreferenceManager2
        ): QsbSearchProvider {
            val provider = preferenceManager.hotseatQsbProvider.firstBlocking()

            return if (resolveSearchIntent(context, provider)) provider else {
                val searchPackage = QsbContainerView.getSearchWidgetPackageName(context)
                return if (!searchPackage.isNullOrEmpty()) {
                    QsbSearchProvider.resolve(searchPackage)
                } else {
                    QsbSearchProvider.None
                }
            }
        }

        fun resolveSearchIntent(context: Context, provider: QsbSearchProvider): Boolean =
            context.packageManager.resolveActivity(provider.createSearchIntent(), 0) != null

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
