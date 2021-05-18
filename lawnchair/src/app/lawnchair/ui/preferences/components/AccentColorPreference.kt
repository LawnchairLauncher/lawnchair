package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun AccentColorPreference(showDivider: Boolean = true) {
    val scope = rememberCoroutineScope()

    BottomSheet(
        sheetContent = { }
    ) { showSheet ->
        ClickablePreference(
            label = stringResource(id = R.string.accent_color),
            showDivider = showDivider,
            onClick = { scope.launch { showSheet() } }
        )
    }
}