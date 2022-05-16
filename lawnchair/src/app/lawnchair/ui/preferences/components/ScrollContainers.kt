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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf
import app.lawnchair.ui.util.rememberExtendPadding
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import kotlinx.coroutines.awaitCancellation

@Composable
fun PreferenceColumn(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    ConsumeScaffoldPadding { contentPadding ->
        NestedScrollStretch {
            Column(
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                modifier = Modifier
                    .fillMaxHeight()
                    .addIf(scrollState != null) {
                        this
                            .verticalScroll(scrollState!!)
                    }
                    .padding(rememberExtendPadding(contentPadding, bottom = 16.dp)),
                content = content
            )
        }
    }
}

@Composable
fun PreferenceLazyColumn(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    isChild: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    if (!enabled) {
        LaunchedEffect(key1 = null) {
            state.scroll(scrollPriority = MutatePriority.PreventUserInput) {
                awaitCancellation()
            }
        }
    }
    ConsumeScaffoldPadding { contentPadding ->
        NestedScrollStretch {
            LazyColumn(
                modifier = modifier
                    .addIf(!isChild) {
                        fillMaxHeight()
                    },
                contentPadding = rememberExtendPadding(
                    contentPadding,
                    bottom = if (isChild) 0.dp else 16.dp
                ),
                state = state,
                content = content
            )
        }
    }
}

@Composable
fun ConsumeScaffoldPadding(
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val contentPadding = LocalScaffoldPadding.current
    CompositionLocalProvider(
        LocalScaffoldPadding provides PaddingValues(0.dp)
    ) {
        content(contentPadding)
    }
}
