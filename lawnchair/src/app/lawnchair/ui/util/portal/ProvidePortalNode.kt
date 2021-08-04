package app.lawnchair.ui.util.portal

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ProvidePortalNode(content: @Composable () -> Unit) {
    val views = remember { mutableStateListOf<View>() }
    val node = remember { PortalNodeImpl(views) }
    Box {
        CompositionLocalProvider(
            LocalPortalNode provides node
        ) {
            content()
        }
        views.forEach { view ->
            AndroidView(factory = { view })
        }
    }
}

class PortalNodeImpl(private val views: MutableList<View>) : PortalNode {
    override fun addView(view: View) {
        views.add(view)
    }

    override fun removeView(view: View) {
        views.remove(view)
    }
}
