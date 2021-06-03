package app.lawnchair.views

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import app.lawnchair.LawnchairLauncherQuickstep
import app.lawnchair.launcher
import app.lawnchair.ui.preferences.components.BottomSheet
import app.lawnchair.ui.preferences.components.BottomSheetState
import app.lawnchair.ui.preferences.components.rememberBottomSheetState
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.portal.PortalNode
import app.lawnchair.ui.util.portal.PortalNodeView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.uioverrides.WallpaperColorInfo
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

    fun removeFromDragLayer() {
        launcher.dragLayer.removeView(this)
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
        fun show(launcher: LawnchairLauncherQuickstep, content: @Composable ComposeFloatingView.() -> Unit) {
            val view = ComposeFloatingView(launcher)
            view.container.setContent {
                LawnchairTheme {
                    ProvideWindowInsets {
                        content(view)
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
fun LawnchairLauncherQuickstep.showBottomSheet(
    content: @Composable ColumnScope.(state: BottomSheetState) -> Unit
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

        BottomSheet(
            sheetState = state,
            sheetContent = {
                content(state)
            },
            scrimColor = scrimColor()
        )
    }
}
