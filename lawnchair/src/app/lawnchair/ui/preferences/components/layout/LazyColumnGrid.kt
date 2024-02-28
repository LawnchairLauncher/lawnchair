package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates a vertical grid of items via LazyList
 *
 * TODO: use [LazyVerticalGrid]
 */
fun LazyListScope.verticalGridItems(
    modifier: Modifier = Modifier,
    count: Int,
    numColumns: Int,
    horizontalGap: Dp = 0.dp,
    verticalGap: Dp = 0.dp,
    itemContent: @Composable GridItemScope.(index: Int) -> Unit,
) {
    if (numColumns == 0) return
    val numRows = (count - 1) / numColumns + 1
    items(numRows) { row ->
        val gridItemScope = object : GridItemScope {
            override suspend fun LazyListState.scrollToThisItem() {
                animateScrollToItem(row, 0)
            }
        }
        if (row != 0) {
            Spacer(modifier = Modifier.height(verticalGap))
        }
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (col in 0 until numColumns) {
                if (col != 0) {
                    Spacer(modifier = Modifier.requiredWidth(horizontalGap))
                }
                val index = row * numColumns + col
                if (index < count) {
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(gridItemScope, index)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun <T> LazyListScope.verticalGridItems(
    modifier: Modifier = Modifier,
    items: List<T>,
    numColumns: Int,
    horizontalGap: Dp = 0.dp,
    verticalGap: Dp = 0.dp,
    itemContent: @Composable GridItemScope.(index: Int, item: T) -> Unit,
) {
    verticalGridItems(
        modifier = modifier,
        count = items.size,
        numColumns = numColumns,
        horizontalGap = horizontalGap,
        verticalGap = verticalGap,
    ) { index ->
        itemContent(index, items[index])
    }
}

interface GridItemScope {
    suspend fun LazyListState.scrollToThisItem()
}
