package app.lawnchair.views

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.rememberSheetState
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer

class ComposeBottomSheet<T>(context: Context) : AbstractFloatingView(context, null, 0) where T : Context, T : ActivityContext {

    private val mActivityContext: T = ActivityContext.lookupContext(context)
    private val container = ComposeView(context)

    init {
        layoutParams = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            .apply { ignoreInsets = true }
        addView(container)
    }

    fun show() {
        val parent = parent
        if (parent is ViewGroup) {
            parent.removeView(this)
        }
        mActivityContext.dragLayer.addView(this)
        mIsOpen = true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun setContent(
        sheetState: SheetState? = null,
        enabledValues: Set<SheetValue>? = null,
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        container.setContent {
            Providers(
                sheetState ?: rememberSheetState(
                    enabledValues = enabledValues ?: setOf(SheetValue.Hidden, SheetValue.Expanded),
                ),
            ) {
                content(this)
            }
        }
    }

    override fun handleClose(animate: Boolean) {
        if (mActivityContext is Launcher) {
            mActivityContext.hideKeyboard()
        }
        mIsOpen = false
        val parent = parent
        if (parent is ViewGroup) {
            parent.removeView(this)
        }
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_COMPOSE_VIEW != 0
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Providers(
        sheetState: SheetState = rememberSheetState(),
        content: @Composable () -> Unit,
    ) {
        LawnchairTheme {
            ProvideLifecycleState {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    ModalBottomSheet(
                        onDismissRequest = { handleClose(true) },
                        sheetState = sheetState,
                    ) {
                        content()
                    }
                }
            }
        }
    }

    companion object {
        @OptIn(ExperimentalMaterial3Api::class)
        fun <T> show(
            context: T,
            sheetState: SheetState? = null,
            enabledValues: Set<SheetValue>? = null,
            content: @Composable ComposeBottomSheet<T>.() -> Unit,
        ) where T : Context, T : ActivityContext {
            closeAllOpenViews(context)
            val view = ComposeBottomSheet<T>(context)
            view.setContent(sheetState, enabledValues, content)
            view.show()
        }
    }
}
