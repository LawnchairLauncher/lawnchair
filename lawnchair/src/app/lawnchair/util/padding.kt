package app.lawnchair.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues

inline fun Modifier.navigationBarsOrDisplayCutoutPadding(
    top: Boolean = false,
    bottom: Boolean = true,
    start: Boolean = true,
    end: Boolean = true,
): Modifier = composed {
    val navigationBars = rememberInsetsPaddingValues(
        insets = LocalWindowInsets.current.navigationBars,
        applyTop = top,
        applyStart = start,
        applyEnd = end,
        applyBottom = bottom
    )
    val displayCutout = rememberInsetsPaddingValues(
        insets = LocalWindowInsets.current.displayCutout,
        applyTop = top,
        applyStart = start,
        applyEnd = end,
        applyBottom = bottom
    )
    padding(max(navigationBars, displayCutout))
}

@Composable
fun max(a: PaddingValues, b: PaddingValues) = remember(a, b) {
    object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
            return max(
                a.calculateLeftPadding(layoutDirection),
                b.calculateLeftPadding(layoutDirection)
            )
        }

        override fun calculateTopPadding(): Dp {
            return max(a.calculateTopPadding(), b.calculateTopPadding())
        }

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
            return max(
                a.calculateRightPadding(layoutDirection),
                b.calculateRightPadding(layoutDirection)
            )
        }

        override fun calculateBottomPadding(): Dp {
            return max(a.calculateBottomPadding(), b.calculateBottomPadding())
        }
    }
}

@Composable
operator fun PaddingValues.minus(b: PaddingValues): PaddingValues {
    val a = this
    return remember(a, b) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
                val aLeft = a.calculateLeftPadding(layoutDirection)
                val bLeft = b.calculateRightPadding(layoutDirection)
                return (aLeft - bLeft).coerceAtLeast(0.dp)
            }

            override fun calculateTopPadding(): Dp {
                val aTop = a.calculateTopPadding()
                val bTop = b.calculateTopPadding()
                return (aTop - bTop).coerceAtLeast(0.dp)
            }

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
                val aRight = a.calculateRightPadding(layoutDirection)
                val bRight = b.calculateRightPadding(layoutDirection)
                return (aRight - bRight).coerceAtLeast(0.dp)
            }

            override fun calculateBottomPadding(): Dp {
                val aBottom = a.calculateBottomPadding()
                val bBottom = b.calculateBottomPadding()
                return (aBottom - bBottom).coerceAtLeast(0.dp)
            }
        }
    }
}
