package ch.deletescape.lawnchair.ui.preferences

import android.os.Build
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ch.deletescape.lawnchair.ui.theme.LawnchairTheme

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SystemUi(windows: Window) =
    LawnchairTheme {
        windows.statusBarColor = MaterialTheme.colors.background.toArgb()
        windows.navigationBarColor = MaterialTheme.colors.background.toArgb()

        @Suppress("DEPRECATION")
        if (MaterialTheme.colors.background.luminance() > 0.5f) {
            windows.decorView.systemUiVisibility = windows.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        @Suppress("DEPRECATION")
        if (MaterialTheme.colors.background.luminance() > 0.5f) {
            windows.decorView.systemUiVisibility = windows.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }