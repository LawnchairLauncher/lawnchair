package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import app.lawnchair.ui.StretchEdgeEffect

@Composable
fun NestedScrollStretch(content: @Composable () -> Unit) {
    val connection = remember { NestedScrollStretchConnection() }
    Box(
        modifier = Modifier
            .nestedScroll(connection)
            .drawWithContent {
                connection.effect.draw(this) {
                    drawContent()
                }
            }
    ) {
        content()
    }
}

private class NestedScrollStretchConnection : NestedScrollConnection {
    val effect = StretchEdgeEffect()
    private var isFlinging = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        when {
            isFlinging -> return Offset.Zero
            available.y != 0F -> effect.onPull(available.y)
            else -> effect.onRelease()
        }
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        effect.onRelease()
        isFlinging = true
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        isFlinging = false
        effect.onAbsorb(available.y)
        return Velocity(0f, available.y)
    }
}
