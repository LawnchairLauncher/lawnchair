package app.lawnchair.views

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.components.BottomSheet
import app.lawnchair.ui.preferences.components.BottomSheetState
import app.lawnchair.ui.preferences.components.rememberBottomSheetState
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.portal.PortalNode
import app.lawnchair.ui.util.portal.PortalNodeView
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.uioverrides.WallpaperColorInfo
import com.android.launcher3.util.SystemUiController
import com.google.accompanist.insets.ProvideWindowInsets
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
    var closeHandler: CloseHandler? = null

    init {
        mIsOpen = true
        addView(container)
    }

    override fun handleClose(animate: Boolean) {
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

    override fun logActionCommand(command: Int) {

    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_COMPOSE_VIEW != 0
    }

    companion object {
        fun show(launcher: LawnchairLauncher, content: @Composable ComposeFloatingView.() -> Unit) {
            val view = ComposeFloatingView(launcher)
            view.container.setContent {
                LawnchairTheme {
                    ProvideWindowInsets {
                        ProvideLifecycleState {
                            content(view)
                        }
                    }
                }
            }
            launcher.dragLayer.addView(view)
        }
    }
}

@Composable
fun scrimColor(): Color {
    val context = LocalContext.current
    val colors = WallpaperColorInfo.INSTANCE[context]
    val alpha = integerResource(id = R.integer.extracted_color_gradient_alpha)
    val intColor = GraphicsUtils.setColorAlphaBound(colors.secondaryColor, alpha)
    return Color(intColor)
}

@OptIn(ExperimentalMaterialApi::class)
fun LawnchairLauncher.showBottomSheet(
    content: @Composable (state: BottomSheetState) -> Unit
) {
    ComposeFloatingView.show(this) {
        val state = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
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

        SystemUi(setStatusBar = false)
        BottomSheet(
            sheetState = state,
            sheetContent = {
                content(state)
            },
            scrimColor = scrimColor()
        )
    }
}

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
