package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun BottomSpacer() {
    Box(
        contentAlignment = Alignment.BottomStart
    ) {
        Spacer(modifier = Modifier.navigationBarsPadding().imePadding())
        Spacer(
            modifier = Modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .fillMaxWidth()
                .pointerInput(Unit) {},
        )
    }
}
