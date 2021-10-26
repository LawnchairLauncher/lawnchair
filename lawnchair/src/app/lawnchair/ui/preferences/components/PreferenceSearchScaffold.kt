package app.lawnchair.ui.preferences.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf
import com.android.quickstep.SysUINavigationMode
import com.google.accompanist.insets.*
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import com.google.accompanist.insets.ui.Scaffold

@ExperimentalAnimationApi
@Composable
fun PreferenceSearchScaffold(
    searchInput: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val innerPadding = remember { MutablePaddingValues() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val searchBarVerticalMargin = 8.dp
    val searchBarHeight = 56.dp
    val statusBarHeight = with(LocalDensity.current) { LocalWindowInsets.current.statusBars.top.toDp() }
    val contentShift = statusBarHeight + searchBarVerticalMargin + searchBarHeight / 2

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(bottom = false)
                    .padding(horizontal = 16.dp, vertical = searchBarVerticalMargin)
                    .height(searchBarHeight),
                shape = MaterialTheme.shapes.small,
                elevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarSize)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ClickableIcon(
                        imageVector = backIcon(),
                        onClick = { backDispatcher?.onBackPressed() }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 32.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            searchInput()
                        }
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Row(
                                Modifier.fillMaxHeight(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Spacer(
                Modifier
                    .navigationBarsHeight()
                    .fillMaxWidth()
                    .addIf(navigationMode() != SysUINavigationMode.Mode.NO_BUTTON) {
                        background(color = MaterialTheme.colors.background.copy(alpha = 0.9f))
                    }
            )
        },
        contentPadding = rememberInsetsPaddingValues(
            insets = LocalWindowInsets.current.systemBars,
            applyTop = false,
            applyBottom = false
        )
    ) {
        val layoutDirection = LocalLayoutDirection.current
        innerPadding.left = it.calculateLeftPadding(layoutDirection)
        innerPadding.top = it.calculateTopPadding() - contentShift
        innerPadding.right = it.calculateRightPadding(layoutDirection)
        innerPadding.bottom = it.calculateBottomPadding()
        CompositionLocalProvider(
            LocalScaffoldPadding provides innerPadding
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
