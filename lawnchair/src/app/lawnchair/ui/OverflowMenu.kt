package app.lawnchair.ui

import androidx.compose.material.DropdownMenu
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.ClickableIcon

@Composable
fun OverflowMenu(block: @Composable OverflowMenuScope.() -> Unit) {
    val showMenu = remember { mutableStateOf(false) }
    val overflowMenuScope = remember { OverflowMenuScopeImpl(showMenu) }

    ClickableIcon(
        imageVector = Icons.Rounded.MoreVert,
        onClick = { showMenu.value = true },
        tint = MaterialTheme.colors.onSurface
    )
    DropdownMenu(
        expanded = showMenu.value,
        onDismissRequest = { showMenu.value = false },
        offset = DpOffset(x = 8.dp, y = 0.dp)
    ) {
        block(overflowMenuScope)
    }
}

interface OverflowMenuScope {
    fun hideMenu()
}

private class OverflowMenuScopeImpl(private val showState: MutableState<Boolean>) : OverflowMenuScope {
    override fun hideMenu() {
        showState.value = false
    }
}
