package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.lawnchair.util.backHandler
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun BottomSheet(
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (showSheet: suspend () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    val currentSheetContent by rememberUpdatedState(sheetContent)
    val addedToHost = rememberSaveable { mutableStateOf(false) }
    val wasVisible = rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (wasVisible.value && !state.isVisible) {
        addedToHost.value = false
    }
    wasVisible.value = state.isVisible

    if (addedToHost.value) {
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

    val showSheet = remember {
        suspend {
            addedToHost.value = true
            state.show()
        }
    }
    content(showSheet)
}
