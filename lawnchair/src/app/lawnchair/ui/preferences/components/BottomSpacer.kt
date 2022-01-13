package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.quickstep.SysUINavigationMode
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.navigationBarsWithImePadding

@Composable
fun BottomSpacer() {
    Box(
        contentAlignment = Alignment.BottomStart
    ) {
        Spacer(modifier = Modifier.navigationBarsWithImePadding())
        if (navigationMode() != SysUINavigationMode.Mode.NO_BUTTON) {
            Spacer(
                modifier = Modifier
                    .navigationBarsHeight()
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colors.background.copy(alpha = 0.9f))
            )
        }
    }
}
