package app.lawnchair.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun OverflowMenu(
    modifier: Modifier = Modifier,
    block: @Composable OverflowMenuScope.() -> Unit,
) {
    val showMenu = remember { mutableStateOf(false) }
    val overflowMenuScope = remember { OverflowMenuScopeImpl(showMenu) }

    Box(
        modifier = modifier,
    ) {
        IconButton(
            onClick = { showMenu.value = true },
            modifier = Modifier
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options",
            )
        }
        AnimatedVisibility(
            visible = showMenu.value,
            enter = scaleIn(
                animationSpec = tween(
                    durationMillis = 400,
                    easing = LinearOutSlowInEasing,
                ),
                initialScale = 0.92f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.9f, 0.05f),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 250,
                    delayMillis = 50,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = scaleOut(
                animationSpec = tween(
                    durationMillis = 150,
                    easing = EaseOutQuart,
                ),
                targetScale = 0.95f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.9f, 0.05f),
            ) + fadeOut(
                animationSpec = tween(durationMillis = 100),
            ),
        ) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = -240, y = -4),
                onDismissRequest = { showMenu.value = false },
                properties = PopupProperties(focusable = true),
            ) {
                Card(
                    modifier = Modifier
                        .width(240.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        block(overflowMenuScope)
                    }
                }
            }
        }
    }
}

sealed interface OverflowMenuScope {
    fun hideMenu()
}

private class OverflowMenuScopeImpl(private val showState: MutableState<Boolean>) : OverflowMenuScope {
    override fun hideMenu() {
        showState.value = false
    }
}
