package app.lawnchair.views

import android.content.Context
import android.util.FloatProperty
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.Interpolator
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.minus
import com.android.launcher3.Launcher
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.views.AbstractSlideInView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer

class ComposeBottomSheet<T>(context: Context) : AbstractSlideInView<T>(context, null, 0) where T : Context, T : ActivityContext {

    private val container = ComposeView(context)
    private var imeShift = 0f
    private var _hintCloseProgress = mutableFloatStateOf(0f)
    private var hintCloseDistance = 0f
    val hintCloseProgress: Float get() = _hintCloseProgress.floatValue

    init {
        layoutParams = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            .apply { ignoreInsets = true }
        gravity = Gravity.BOTTOM

        mContent = LinearLayout(context).apply { addView(container) }
    }

    fun show() {
        val parent = parent
        if (parent is ViewGroup) {
            parent.removeView(this)
        }
        removeAllViews()
        addView(mContent)
        attachToContainer()
        animateOpen()
    }

    fun setContent(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        container.setContent {
            Providers {
                ContentWrapper(contentPaddings = contentPaddings) {
                    content(this)
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        setTranslationShift(mTranslationShift)
    }

    private fun animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.animationPlayer.isRunning) {
            return
        }
        mIsOpen = true
        setUpDefaultOpenAnimation().start()
    }

    override fun handleClose(animate: Boolean) {
        if (mActivityContext is Launcher) {
            mActivityContext.hideKeyboard()
        }
        handleClose(animate, DEFAULT_CLOSE_DURATION)
    }

    override fun onCloseComplete() {
        super.onCloseComplete()
        setSystemUiFlags(0)
    }

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_COMPOSE_VIEW != 0
    }

    override fun getScrimColor(context: Context): Int {
        return ColorTokens.WidgetsPickerScrim.resolveColor(context)
    }

    override fun setTranslationShift(translationShift: Float) {
        mTranslationShift = translationShift
        updateContentShift()
        if (mColorScrim != null) {
            mColorScrim.alpha = 1 - mTranslationShift
        }
    }

    private fun setImeShift(shift: Float) {
        imeShift = shift
        updateContentShift()
    }

    private fun updateContentShift() {
        mContent.translationY = mTranslationShift * mContent.height + imeShift
    }

    override fun addHintCloseAnim(
        distanceToMove: Float,
        interpolator: Interpolator,
        target: PendingAnimation,
    ) {
        super.addHintCloseAnim(distanceToMove, interpolator, target)
        hintCloseDistance = distanceToMove
        target.setFloat(this, HINT_CLOSE_PROGRESS, 1f, interpolator)
    }

    private fun setSystemUiFlags(flags: Int) {
        if (mActivityContext is Launcher) {
            mActivityContext.systemUiController?.updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                flags,
            )
        }
    }

    @Composable
    private fun SystemUi(setStatusBar: Boolean = true, setNavBar: Boolean = true) {
        // todo change to isLight
        val useDarkIcons = true

        SideEffect {
            var flags = 0
            if (setStatusBar) {
                flags = flags or (
                    if (useDarkIcons) {
                        SystemUiController.FLAG_LIGHT_STATUS
                    } else {
                        SystemUiController.FLAG_DARK_STATUS
                    }
                    )
            }
            if (setNavBar) {
                flags = flags or (
                    if (useDarkIcons) {
                        SystemUiController.FLAG_LIGHT_NAV
                    } else {
                        SystemUiController.FLAG_DARK_NAV
                    }
                    )
            }
            setSystemUiFlags(flags)
        }
    }

    @Composable
    private fun Providers(
        content: @Composable () -> Unit,
    ) {
        LawnchairTheme {
            ProvideLifecycleState {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun ContentWrapper(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        val imePaddings = WindowInsets.ime
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues()

        val translation = imePaddings - contentPaddings
        setImeShift(with(LocalDensity.current) { -translation.calculateBottomPadding().toPx() })

        SystemUi(setStatusBar = false)
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = backgroundShape,

            ) {
                Box(
                    modifier = Modifier
                        .padding(contentPaddings)
                        .graphicsLayer(
                            alpha = 1f - (hintCloseProgress * 0.5f),
                            translationY = hintCloseProgress * -hintCloseDistance,
                        ),
                ) {
                    content(this@ComposeBottomSheet)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_CLOSE_DURATION = 200L
        private val backgroundShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

        private val HINT_CLOSE_PROGRESS = object : FloatProperty<ComposeBottomSheet<*>>("hintCloseProgress") {

            override fun setValue(view: ComposeBottomSheet<*>, value: Float) {
                view._hintCloseProgress.floatValue = value
            }

            override fun get(view: ComposeBottomSheet<*>) = view._hintCloseProgress.floatValue
        }

        fun <T> show(
            context: T,
            contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
            content: @Composable ComposeBottomSheet<T>.() -> Unit,
        ) where T : Context, T : ActivityContext {
            closeAllOpenViews(context)
            val view = ComposeBottomSheet<T>(context)
            view.setContent(contentPaddings, content)
            view.show()
        }
    }
}
