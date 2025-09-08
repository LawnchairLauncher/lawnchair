package app.lawnchair.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.ClickableIcon

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
        ClickableIcon(
            imageVector = Icons.Rounded.MoreVert,
            onClick = { showMenu.value = true },
        )
        DropdownMenu(
            expanded = showMenu.value,
            onDismissRequest = { showMenu.value = false },
            offset = DpOffset(x = (-2).dp, y = (-48).dp),
            shape = MaterialTheme.shapes.large,
        ) {
            block(overflowMenuScope)
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
