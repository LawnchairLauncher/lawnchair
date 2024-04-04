package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf
import kotlinx.coroutines.awaitCancellation

@Composable
fun PreferenceColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    NestedScrollStretch(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            modifier = Modifier
                .fillMaxHeight()
                .addIf(scrollState != null) {
                    this
                        .verticalScroll(scrollState!!)
                }
                .padding(contentPadding)
                .padding(top = 8.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
fun PreferenceLazyColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChild: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    if (!enabled) {
        LaunchedEffect(key1 = null) {
            state.scroll(scrollPriority = MutatePriority.PreventUserInput) {
                awaitCancellation()
            }
        }
    }
    NestedScrollStretch(
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .addIf(!isChild) {
                    fillMaxHeight()
                },
            contentPadding = contentPadding,
            state = state,
            content = content,
        )
    }
}
