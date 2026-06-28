package app.lawnchair.qsb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import app.lawnchair.animateToAllApps
import app.lawnchair.launcher
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.firstCached
import app.lawnchair.qsb.providers.AppSearch
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.BaseActivity
import com.android.launcher3.DeviceProfile
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class LawnQsbLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val activity: ActivityContext = ActivityContext.lookupContext<BaseActivity>(context)
    private val composeView = ComposeView(context)
    private lateinit var preferenceManager2: PreferenceManager2

    private lateinit var searchProvider: QsbSearchProvider

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onFinishInflate() {
        super.onFinishInflate()

        preferenceManager2 = PreferenceManager2.getInstance(context)
        searchProvider = getSearchProvider(context, preferenceManager2)

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LawnchairTheme {
                    ProvideLifecycleState {
                        val context = LocalContext.current

                        val prefs = preferenceManager()
                        val prefs2 = preferenceManager2

                        val searchProviderPref by prefs2.hotseatQsbProvider.asState()
                        val searchProvider = remember(searchProviderPref, context) {
                            getSearchProvider(context, searchProviderPref)
                        }
                        val themed by prefs2.themedHotseatQsb.asState()

                        val supportsLens = searchProvider == Google || searchProvider == PixelSearch
                        val voiceIntent = remember(searchProvider, context) {
                            getVoiceIntent(searchProvider, context)
                        }
                        val lensIntent = remember(supportsLens, context) {
                            if (supportsLens) getLensIntent(context) else null
                        }

                        val state = rememberHotseatQsbState(
                            searchProvider = searchProvider,
                            themed = themed,
                            showMic = voiceIntent != null,
                            showLens = lensIntent != null,
                        )

                        val style = buildQsbStyle(
                            context = LocalContext.current,
                            themed = themed,
                            backgroundColor = getHotseatBackgroundColor(context, themed),
                            backgroundAlpha = prefs.hotseatQsbAlpha.observeAsState().value,
                            cornerRadius = prefs.hotseatQsbCornerRadius.observeAsState().value,
                            // Use light color as strokeColor is a static color that doesn't use darkColor
                            strokeColor = prefs2.strokeColorStyle.asState().value.colorPreferenceEntry.lightColor.invoke(context),
                            strokeWidth = prefs.hotseatQsbStrokeWidth.observeAsState().value,
                        )

                        val actions = QsbActions(
                            onQsbClick = {
                                val launcher = context.launcher
                                launcher.lifecycleScope.launch {
                                    if (prefs2.matchHotseatQsbStyle.firstCached()) {
                                        launcher.appsView.searchUiManager.editText?.showKeyboard()
                                        launcher.animateToAllApps()
                                    } else {
                                        searchProvider.launch(launcher)
                                    }
                                }
                            },
                            onStartIconClick = null,
                            onEndIconClick = { id ->
                                runCatching {
                                    when (id) {
                                        QsbIconId.MIC -> voiceIntent?.let { context.startActivity(it) }
                                        QsbIconId.LENS -> lensIntent?.let { context.startActivity(it) }
                                        else -> null
                                    }
                                }
                            },
                        )

                        LawnQsbUi(
                            state = state,
                            style = style,
                            actions = actions,
                        )
                    }
                }
            }
        }

        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        if (searchProvider == Google) {
            repeatOnAttached {
                val forceWebsite = preferenceManager2.hotseatQsbForceWebsite.get()
                forceWebsite
                    .flatMapLatest {
                        if (it) Google.getSearchIntent(context) else flowOf(null)
                    }
                    .collect()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dp = activity.deviceProfile
        // Unlike Phone, for Foldable/Tablet we let the original onMeasure do that instead since it
        // matched what we need. It perfectly fit the QSB with the grid.
        if (!dp.deviceProperties.isPhone) {
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

        if (!composeView.isAttachedToWindow) {
            // Ignore to prevent crash on preview contexts
            return
        }

        children.forEach { child ->
            measureChildWithMargins(child, widthMeasureSpec, widthReduction, heightMeasureSpec, 0)
        }
    }

    companion object {
        private const val LENS_PACKAGE = "com.google.ar.lens"
        private const val LENS_ACTIVITY = "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"

        fun getVoiceIntent(
            provider: QsbSearchProvider,
            context: Context,
        ): Intent? {
            val intent = if (provider.supportVoiceIntent) provider.createVoiceIntent() else null

            return if (intent == null || !resolveIntent(context, intent)) {
                null
            } else {
                intent
            }
        }

        fun getLensIntent(context: Context): Intent? {
            val lensIntent = Intent.makeMainActivity(ComponentName(LENS_PACKAGE, LENS_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (context.packageManager.resolveActivity(lensIntent, 0) == null) return null

            return lensIntent
        }

        fun getSearchProvider(
            context: Context,
            provider: QsbSearchProvider,
        ): QsbSearchProvider {
            return if (provider == AppSearch ||
                resolveIntent(context, provider.createSearchIntent()) ||
                resolveIntent(context, provider.createWebsiteIntent())
            ) {
                provider
            } else {
                AppSearch
            }
        }

        fun getSearchProvider(
            context: Context,
            preferenceManager: PreferenceManager2,
        ): QsbSearchProvider {
            return getSearchProvider(context, preferenceManager.hotseatQsbProvider.firstCached())
        }

        fun resolveIntent(context: Context, intent: Intent): Boolean = context.packageManager.resolveActivity(intent, 0) != null
    }
}
