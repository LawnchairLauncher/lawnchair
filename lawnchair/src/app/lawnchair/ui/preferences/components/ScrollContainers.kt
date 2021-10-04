package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.rememberExtendPadding
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import kotlinx.coroutines.awaitCancellation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PreferenceColumn(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    NestedScrollStretch {
        Column(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .pointerInteropFilter {
                    // return true if scrolling
                    scrollState.isScrollInProgress
                }
                .padding(rememberExtendPadding(LocalScaffoldPadding.current, bottom = 8.dp)),
            content = content
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PreferenceLazyColumn(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    if (!enabled) {
        LaunchedEffect(key1 = null) {
            state.scroll(scrollPriority = MutatePriority.PreventUserInput) {
                awaitCancellation()
            }
        }
    }
    NestedScrollStretch {
        LazyColumn(
            modifier = modifier
                .fillMaxHeight()
                .pointerInteropFilter {
                    // return true if scrolling
                    state.isScrollInProgress
                },
            contentPadding = rememberExtendPadding(LocalScaffoldPadding.current, bottom = 8.dp),
            state = state,
            content = content
        )
    }
}
