package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.android.quickstep.SysUINavigationMode

@Composable
fun BottomSpacer() {
    Box(
        contentAlignment = Alignment.BottomStart,
    ) {
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding(),
        )
        if (navigationMode() != SysUINavigationMode.Mode.NO_BUTTON) {
            Spacer(
                modifier = Modifier
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colors.background.copy(alpha = 0.9f)),
            )
        } else {
            Spacer(
                modifier = Modifier
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .pointerInput(Unit) {},
            )
        }
    }
}
