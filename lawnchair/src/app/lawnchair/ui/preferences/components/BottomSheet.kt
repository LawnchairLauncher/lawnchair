package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.*
import androidx.compose.runtime.*
import app.lawnchair.util.backHandler
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun BottomSheet(
    sheetContent: @Composable ColumnScope.() -> Unit,
    sheetState: ModalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden),
    content: @Composable (showSheet: suspend () -> Unit) -> Unit,
) {
    var isAnimatingShow by remember { mutableStateOf(false) }

    val currentSheetContent by rememberUpdatedState(sheetContent)
    val scope = rememberCoroutineScope()

    if (sheetState.isVisible || isAnimatingShow) {
        Portal {
            ModalBottomSheetLayout(
                sheetState = sheetState,
                sheetContent = currentSheetContent
            ) {
                backHandler {
                    scope.launch { sheetState.hide() }
                }
            }
        }
    }

    val showSheet = remember { suspend {
        try {
            isAnimatingShow = true
            sheetState.show()
        } finally {
            isAnimatingShow = false
        }
    } }
    content(showSheet)
}
