package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.quickstep.SysUINavigationMode
import com.google.accompanist.insets.ui.Scaffold

@Composable
fun PreferenceScaffold(
    backArrowVisible: Boolean = true,
    floating: State<Boolean> = remember { mutableStateOf(false) },
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
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
        bottomBar = bottomBar,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    ) {
        content(it)
    }
}

@Composable
fun navigationMode(): SysUINavigationMode.Mode {
    val context = LocalContext.current
    return SysUINavigationMode.getMode(context)
}
