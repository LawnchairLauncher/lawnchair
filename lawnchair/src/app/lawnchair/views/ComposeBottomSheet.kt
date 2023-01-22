package app.lawnchair.views

import android.animation.PropertyValuesHolder
import android.content.Context
import android.util.FloatProperty
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.Interpolator
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.minus
import com.android.launcher3.Launcher
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.views.AbstractSlideInView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.MaterialTheme as Material3Theme

class ComposeBottomSheet<T>(context: Context)
: AbstractSlideInView<T>(context, null, 0) where T : Context, T : ActivityContext {

    private val container = ComposeView(context)
    private var imeShift = 0f
    private var _hintCloseProgress = mutableStateOf(0f)
    private val hintCloseProgress get() = _hintCloseProgress.value
    private var hintCloseDistance = 0f

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
        content: @Composable ComposeBottomSheet<T>.() -> Unit
    ) {
        container.setContent {
            Providers {
                ContentWrapper(contentPaddings) {
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
        if (mIsOpen || mOpenCloseAnimator.isRunning) {
            return
        }
        mIsOpen = true
        mOpenCloseAnimator.setValues(
            PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED)
        )
        mOpenCloseAnimator.interpolator = Interpolators.FAST_OUT_SLOW_IN
        mOpenCloseAnimator.start()
    }

    override fun handleClose(animate: Boolean) {
        if (mActivityContext is Launcher) {
            mActivityContext.hideKeyboard()
        }
        handleClose(animate, defaultCloseDuration)
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
        target: PendingAnimation
    ) {
        super.addHintCloseAnim(distanceToMove, interpolator, target)
        hintCloseDistance = distanceToMove
        target.setFloat(this, HINT_CLOSE_PROGRESS, 1f, interpolator)
    }

    private fun setSystemUiFlags(flags: Int) {
        if (mActivityContext is Launcher) {
            mActivityContext.systemUiController.updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                flags
            )
        }
    }

    @Composable
    private fun SystemUi(setStatusBar: Boolean = true, setNavBar: Boolean = true) {
        val useDarkIcons = MaterialTheme.colors.isLight

        SideEffect {
            var flags = 0
            if (setStatusBar) {
                flags = flags or (
                        if (useDarkIcons) SystemUiController.FLAG_LIGHT_STATUS
                        else SystemUiController.FLAG_DARK_STATUS)
            }
            if (setNavBar) {
                flags = flags or (
                        if (useDarkIcons) SystemUiController.FLAG_LIGHT_NAV
                        else SystemUiController.FLAG_DARK_NAV)
            }
            setSystemUiFlags(flags)
        }
    }

    @Composable
    private fun Providers(content: @Composable () -> Unit) {
        LawnchairTheme {
            ProvideLifecycleState {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.onSurface,
                    M3LocalContentColor provides Material3Theme.colorScheme.onSurface
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun ContentWrapper(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit
    ) {
        val imePaddings = WindowInsets.ime
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues()

        val translation = imePaddings - contentPaddings
        setImeShift(with(LocalDensity.current) { -translation.calculateBottomPadding().toPx() })

        SystemUi(setStatusBar = false)
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = backgroundShape,
            color = Material3Theme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .padding(contentPaddings)
                    .graphicsLayer(
                        alpha = 1f - (hintCloseProgress * 0.5f),
                        translationY = hintCloseProgress * -hintCloseDistance
                    )
            ) {
                content(this@ComposeBottomSheet)
            }
        }
    }

    companion object {
        private const val defaultCloseDuration = 200L
        private val backgroundShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

        private val HINT_CLOSE_PROGRESS = object : FloatProperty<ComposeBottomSheet<*>>("hintCloseProgress") {

            override fun setValue(view: ComposeBottomSheet<*>, value: Float) {
                view._hintCloseProgress.value = value
            }

            override fun get(view: ComposeBottomSheet<*>) = view._hintCloseProgress.value
        }

        fun <T> show(
            context: T,
            contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
            content: @Composable ComposeBottomSheet<T>.() -> Unit
        ) where T : Context, T : ActivityContext {
            val view = ComposeBottomSheet<T>(context)
            view.setContent(contentPaddings, content)
            view.show()
        }
    }
}
