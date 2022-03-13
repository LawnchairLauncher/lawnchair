package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.quickstep.SysUINavigationMode
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold

@ExperimentalAnimationApi
@Composable
fun PreferenceScaffold(
    backArrowVisible: Boolean = true,
    floating: State<Boolean> = remember { mutableStateOf(false) },
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(
                backArrowVisible = backArrowVisible,
                floating = floating.value,
                label = label,
                actions = actions,
            )
        },
        bottomBar = { BottomSpacer() },
        contentPadding = rememberInsetsPaddingValues(
            insets = LocalWindowInsets.current.systemBars,
            applyTop = false,
            applyBottom = false
        )
    ) {
        content(it)
    }
}

@Composable
fun navigationMode(): SysUINavigationMode.Mode {
    val context = LocalContext.current
    return SysUINavigationMode.getMode(context)
}
