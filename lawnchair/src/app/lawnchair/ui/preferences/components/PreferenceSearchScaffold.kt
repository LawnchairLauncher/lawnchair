package app.lawnchair.ui.preferences.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import com.google.accompanist.insets.ui.Scaffold

@Composable
fun PreferenceSearchScaffold(
    searchInput: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val innerPadding = remember { MutablePaddingValues() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val searchBarVerticalMargin = 8.dp
    val searchBarHeight = 56.dp
    val statusBarHeight = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val contentShift = statusBarHeight + searchBarVerticalMargin + searchBarHeight / 2

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(horizontal = 16.dp, vertical = searchBarVerticalMargin)
                    .height(searchBarHeight),
                shape = MaterialTheme.shapes.small,
                elevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarSize)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ClickableIcon(
                        imageVector = backIcon(),
                        onClick = { backDispatcher?.onBackPressed() },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 36.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            searchInput()
                        }
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Row(
                                Modifier.fillMaxHeight(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = { BottomSpacer() },
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        val layoutDirection = LocalLayoutDirection.current
        innerPadding.left = it.calculateLeftPadding(layoutDirection)
        innerPadding.top = it.calculateTopPadding() - contentShift
        innerPadding.right = it.calculateRightPadding(layoutDirection)
        innerPadding.bottom = it.calculateBottomPadding()
        CompositionLocalProvider(
            LocalScaffoldPadding provides innerPadding,
        ) {
            Box(modifier = Modifier.padding(top = contentShift)) {
                content(it)
            }
        }
    }
}

@Stable
internal class MutablePaddingValues : PaddingValues {
    var left: Dp by mutableStateOf(0.dp)
    var top: Dp by mutableStateOf(0.dp)
    var right: Dp by mutableStateOf(0.dp)
    var bottom: Dp by mutableStateOf(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = left

    override fun calculateTopPadding(): Dp = top

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = right

    override fun calculateBottomPadding(): Dp = bottom
}
