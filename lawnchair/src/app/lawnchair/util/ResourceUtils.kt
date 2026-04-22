package app.lawnchair.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun androidColorId(name: String) = androidResId(name = name, defType = "color")

@Composable
fun androidResId(name: String, defType: String): Int {
    val resources = LocalContext.current.resources
    return remember(name, defType) {
        @Suppress("DiscouragedApi")
        resources.getIdentifier(name, defType, "android")
    }
}
