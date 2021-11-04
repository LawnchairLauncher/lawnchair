package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.max
import kotlin.math.min

fun Modifier.pagerHeight(
    dynamicCount: Int,
    staticCount: Int
) = this.then(
    PagerHeightModifier(
        dynamicCount = dynamicCount,
        staticCount = staticCount
    )
)

class PagerHeightModifier(
    private val dynamicCount: Int,
    private val staticCount: Int
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val dynamicItemHeight = 54.dp.roundToPx()
        val dynamicListHeight = 16.dp.roundToPx() +
                dynamicItemHeight * dynamicCount +
                1.dp.roundToPx() * (dynamicCount - 1)

        val columnCount = SwatchGridDefaults.ColumnCount
        val rowCount = (staticCount - 1) / columnCount + 1

        val width = constraints.maxWidth
        val horizontalPadding = 16.dp.roundToPx() * 2
        val gutterSizePx = SwatchGridDefaults.GutterSize.roundToPx()
        val totalGutterWidth = gutterSizePx * (columnCount - 1)
        val availableWidth = width - horizontalPadding - totalGutterWidth
        val swatchMaxWidth = SwatchGridDefaults.SwatchMaxWidth.roundToPx()
        val swatchWidth = min(availableWidth / columnCount, swatchMaxWidth)

        val swatchGridVerticalPadding = 20.dp.roundToPx() + 16.dp.roundToPx()
        val swatchGridInnerHeight = swatchWidth * rowCount + gutterSizePx * (rowCount - 1)
        val swatchGridHeight = swatchGridInnerHeight + swatchGridVerticalPadding

        val height = constraints.constrainHeight(max(dynamicListHeight, swatchGridHeight))

        val placeable = measurable.measure(constraints.copy(maxHeight = height))
        return layout(width, constraints.constrainHeight(swatchGridHeight)) {
            placeable.place(0, 0)
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(dynamicCount, staticCount)
    }

    override fun equals(other: Any?): Boolean {
        return other is PagerHeightModifier
                && dynamicCount == other.dynamicCount
                && staticCount == other.staticCount
    }
}
