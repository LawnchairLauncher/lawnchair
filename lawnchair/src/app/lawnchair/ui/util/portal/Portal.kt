package app.lawnchair.ui.util.portal

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext

@Composable
fun Portal(portalNode: PortalNode = LocalPortalNode.current, content: @Composable () -> Unit) {
    val currentContent by rememberUpdatedState(content)
    val compositionContext = rememberCompositionContext()
    val context = LocalContext.current

    DisposableEffect("portal") {
        val view = ComposeView(context)
        view.setParentCompositionContext(compositionContext)
        view.setContent { currentContent() }
        portalNode.addView(view)
        onDispose { portalNode.removeView(view) }
    }
}

val LocalPortalNode = compositionLocalOf<PortalNode> {
    error("CompositionLocal LocalPortalNode not present")
}
