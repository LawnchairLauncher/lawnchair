package app.lawnchair.views

import android.content.Context
import android.graphics.Rect
import android.util.FloatProperty
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.theme.color.colorToken
import app.lawnchair.ui.preferences.components.BottomSheet
import app.lawnchair.ui.preferences.components.BottomSheetState
import app.lawnchair.ui.preferences.components.rememberBottomSheetState
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.portal.PortalNode
import app.lawnchair.ui.util.portal.PortalNodeView
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.minus
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.util.SystemUiController
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import kotlinx.coroutines.launch

typealias CloseHandler = (animate: Boolean) -> Unit

class ComposeFloatingView(context: Context) :
    AbstractFloatingView(context, null), PortalNode, Insettable {

    private val launcher = context.launcher
    private val container = object : PortalNodeView(context) {
        override fun removeView(view: View?) {
            super.removeView(view)
            if (childCount == 1) {
                removeFromDragLayer()
            }
        }
    }
    private var _hintCloseProgress = mutableStateOf(0f)
    val hintCloseProgress get() = _hintCloseProgress.value
    var hintCloseDistance = 0f
        private set
    var closeHandler: CloseHandler? = null

    init {
        mIsOpen = true
        addView(container)
    }

    override fun handleClose(animate: Boolean) {
        launcher.hideKeyboard()
        val handler = closeHandler ?: throw IllegalStateException("Close handler is null")
        handler(animate)
    }

    fun setSystemUiFlags(flags: Int) {
        launcher.systemUiController.updateUiState(SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, flags)
    }

    fun removeFromDragLayer() {
        launcher.dragLayer.removeView(this)
        launcher.systemUiController.updateUiState(SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0)
    }

    override fun setInsets(insets: Rect) {

    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_COMPOSE_VIEW != 0
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

    companion object {

        private val HINT_CLOSE_PROGRESS = object : FloatProperty<ComposeFloatingView>("hintCloseDistance") {

            override fun setValue(floatingView: ComposeFloatingView, value: Float) {
                floatingView._hintCloseProgress.value = value
            }

            override fun get(floatingView: ComposeFloatingView) = floatingView._hintCloseProgress.value
        }

        fun show(launcher: LawnchairLauncher, content: @Composable ComposeFloatingView.() -> Unit) {
            val view = ComposeFloatingView(launcher)
            view.container.setContent {
                LawnchairTheme {
                    ProvideWindowInsets {
                        ProvideLifecycleState {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colors.onSurface,
                                M3LocalContentColor provides Material3Theme.colorScheme.onSurface
                            ) {
                                content(view)
                            }
                        }
                    }
                }
            }
            launcher.dragLayer.addView(view)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
fun LawnchairLauncher.showBottomSheet(
    contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
    content: @Composable (state: BottomSheetState) -> Unit
) {
    ComposeFloatingView.show(this) {
        val state = rememberBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmStateChange = {
                if (it == ModalBottomSheetValue.Hidden) hideKeyboard()
                true
            }
        )
        val scope = rememberCoroutineScope()

        closeHandler = { animate ->
            scope.launch {
                if (animate) {
                    state.hide()
                } else {
                    state.snapTo(ModalBottomSheetValue.Hidden)
                }
            }
        }

        LaunchedEffect("") {
            state.show()
        }

        val windowInsets = LocalWindowInsets.current
        val imePaddings = rememberInsetsPaddingValues(
            insets = windowInsets.ime,
            applyStart = true, applyEnd = true, applyBottom = true
        )

        SystemUi(setStatusBar = false)
        BottomSheet(
            modifier = Modifier
                .padding(imePaddings - contentPaddings),
            sheetState = state,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .padding(contentPaddings)
                        .graphicsLayer(
                            alpha = 1f - (hintCloseProgress * 0.5f),
                            translationY = hintCloseProgress * -hintCloseDistance
                        )
                ) {
                    content(state)
                }
            },
            scrimColor = colorToken(ColorTokens.WidgetsPickerScrim),
            sheetShape = LauncherSheetShape,
            sheetBackgroundColor = colorToken(ColorTokens.Surface)
        )
    }
}

private val LauncherSheetShape = RoundedCornerShape(24.dp)

@Composable
fun ComposeFloatingView.SystemUi(setStatusBar: Boolean = true, setNavBar: Boolean = true) {
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
