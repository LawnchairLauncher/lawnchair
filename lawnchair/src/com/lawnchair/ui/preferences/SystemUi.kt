package com.lawnchair.ui.preferences

import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.lawnchair.ui.theme.LawnchairTheme

@Composable
fun SystemUi(window: Window) =
    LawnchairTheme {
        window.statusBarColor = MaterialTheme.colors.background.toArgb()
        window.navigationBarColor = MaterialTheme.colors.background.toArgb()

        @Suppress("DEPRECATION")
        if (MaterialTheme.colors.background.luminance() > 0.5f) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        @Suppress("DEPRECATION")
        if (MaterialTheme.colors.background.luminance() > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }