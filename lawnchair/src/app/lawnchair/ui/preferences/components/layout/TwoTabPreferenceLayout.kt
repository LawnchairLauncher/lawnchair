package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.theme.preferenceGroupColor
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun TwoTabPreferenceLayout(
    label: String,
    firstPageLabel: String,
    firstPageContent: @Composable ColumnScope.() -> Unit,
    secondPageLabel: String,
    secondPageContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    defaultPage: Int = 0,
) {
    PreferenceLayout(
        label = label,
        modifier = modifier,
        backArrowVisible = backArrowVisible,
        isExpandedScreen = isExpandedScreen,
    ) {
        val pagerState = rememberPagerState(
            initialPage = defaultPage,
            pageCount = { 2 },
        )

        val scope = rememberCoroutineScope()
        val scrollToPage =
            { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(CircleShape)
                .background(preferenceGroupColor()),
        ) {
            Tab(
                label = firstPageLabel,
                onClick = { scrollToPage(0) },
                currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                page = 0,
            )
            Tab(
                label = secondPageLabel,
                onClick = { scrollToPage(1) },
                currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                page = 1,
            )
        }

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.animateContentSize(),
        ) { page ->
            when (page) {
                0 -> {
                    Column {
                        firstPageContent()
                    }
                }

                1 -> {
                    Column {
                        secondPageContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tab(
    label: String,
    onClick: () -> Unit,
    currentOffset: Float,
    page: Int,
    modifier: Modifier = Modifier,
) {
    val selectedProgress = 1f - abs(currentOffset - page).coerceIn(0f, 1f)
    val shape = CircleShape
    val textColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onPrimaryContainer,
        selectedProgress,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = selectedProgress))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
            .weight(1f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
