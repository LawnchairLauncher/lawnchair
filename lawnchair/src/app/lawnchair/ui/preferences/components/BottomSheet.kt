package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.*
import app.lawnchair.util.backHandler
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun BottomSheet(
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (showSheet: suspend () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    var isAnimatingShow by remember { mutableStateOf(false) }

    val currentSheetContent by rememberUpdatedState(sheetContent)
    val scope = rememberCoroutineScope()

    if (state.isVisible || isAnimatingShow) {
        Portal {
            ModalBottomSheetLayout(
                sheetState = state,
                sheetContent = currentSheetContent
            ) {
                backHandler {
                    scope.launch { state.hide() }
                }
            }
        }
    }

    val showSheet = remember { suspend {
        try {
            isAnimatingShow = true
            state.show()
        } finally {
            isAnimatingShow = false
        }
    } }
    content(showSheet)
}
