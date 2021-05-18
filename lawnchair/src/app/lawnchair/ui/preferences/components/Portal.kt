package app.lawnchair.ui.preferences.components

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ProvidePortalNode(content: @Composable () -> Unit) {
    val views = remember { mutableStateListOf<View>() }
    Box {
        CompositionLocalProvider(
            LocalPortalNode provides remember { PortalNode(views) }
        ) {
            content()
        }
        views.forEach { view ->
            AndroidView(factory = { view })
        }
    }
}

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

class PortalNode(private val views: MutableList<View>) {
    fun addView(view: View) {
        views.add(view)
    }

    fun removeView(view: View) {
        views.remove(view)
    }
}

val LocalPortalNode = compositionLocalOf<PortalNode> {
    error("CompositionLocal LocalPortalNode not present")
}
