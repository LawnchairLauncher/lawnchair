package app.lawnchair.ui.util.portal

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView

open class PortalNodeView(context: Context) : FrameLayout(context), PortalNode {
    private val composeView = ComposeView(context)
    private val content = mutableStateOf<(@Composable () -> Unit)?>(null)

    init {
        addView(composeView)
        composeView.setContent {
            CompositionLocalProvider(
                LocalPortalNode provides this
            ) {
                content.value?.invoke()
            }
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        this.content.value = content
    }
}
