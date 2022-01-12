package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

fun LazyListScope.verticalGridItems(
    count: Int,
    numColumns: Int,
    gap: Dp,
    itemContent: @Composable (index: Int) -> Unit
) {
    val numRows = (count - 1) / numColumns + 1
    items(numRows) { row ->
        if (row != 0) {
            Spacer(modifier = Modifier.height(gap))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (col in 0 until numColumns) {
                if (col != 0) {
                    Spacer(modifier = Modifier.requiredWidth(gap))
                }
                val index = row * numColumns + col
                if (index < count) {
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(index)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun <T> LazyListScope.verticalGridItems(
    items: List<T>,
    numColumns: Int,
    gap: Dp,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    verticalGridItems(
        count = items.size,
        numColumns = numColumns,
        gap = gap
    ) { index ->
        itemContent(index, items[index])
    }
}
