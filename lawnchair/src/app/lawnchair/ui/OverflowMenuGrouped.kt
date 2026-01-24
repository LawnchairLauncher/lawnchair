package app.lawnchair.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.ClickableIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverflowMenuGrouped(
    modifier: Modifier = Modifier,
    block: @Composable OverflowMenuGroupedScope.() -> Unit,
) {
    val showMenu = remember { mutableStateOf(false) }
    val overflowMenuGroupedScope = remember { OverflowMenuGroupedScopeImpl(showMenu) }

    Box(
        modifier = modifier,
    ) {
        ClickableIcon(
            imageVector = Icons.Rounded.MoreVert,
            onClick = { showMenu.value = true },
        )
        DropdownMenuPopup(
            expanded = showMenu.value,
            onDismissRequest = { showMenu.value = false },
            offset = DpOffset(x = (-2).dp, y = (-48).dp),
        ) {
            block(overflowMenuGroupedScope)
        }
    }
}

sealed interface OverflowMenuGroupedScope {
    fun hideMenu()
}

private class OverflowMenuGroupedScopeImpl(private val showState: MutableState<Boolean>) : OverflowMenuGroupedScope {
    override fun hideMenu() {
        showState.value = false
    }
}
