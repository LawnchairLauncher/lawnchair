package app.lawnchair.ui.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun rememberExtendPadding(
    padding: PaddingValues,
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp
): PaddingValues = remember(start, top, end, bottom) {
    CombinePaddingValues(
        padding, PaddingValues(
            start = start,
            top = top,
            end = end,
            bottom = bottom
        )
    )
}

private class CombinePaddingValues(private val a: PaddingValues, private val b: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        a.calculateLeftPadding(layoutDirection) + b.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() =
        a.calculateTopPadding() + b.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        a.calculateRightPadding(layoutDirection) + b.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() =
        a.calculateBottomPadding() + b.calculateBottomPadding()
}
