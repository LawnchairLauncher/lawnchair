package ch.deletescape.lawnchair.settings.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ch.deletescape.lawnchair.settings.ui.theme.LawnchairTheme

private enum class TabPage {
    HomeScreen, Dock, AppDrawer
}

class GridSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                Surface(
                    color = MaterialTheme.colors.background,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Root()
                }
            }
        }
    }

    @Composable
    fun Root() {
        var tabPage by remember { mutableStateOf(TabPage.HomeScreen) }

        Column {
            TopAppBar(
                title = { Text(text = "Grid") },
                backgroundColor = MaterialTheme.colors.background,
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .height(40.dp)
                            .width(40.dp)
                            .clip(shape = RoundedCornerShape(20.dp))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            "Back",
                            modifier = Modifier,
                            MaterialTheme.colors.onBackground
                        )
                    }
                }
            )
            TabBar(tabPage = tabPage, onTabSelected = { tabPage = it })
            Crossfade(targetState = tabPage) { tabPage ->
                when (tabPage) {
                    TabPage.HomeScreen -> HomeScreenGridSettings()
                    TabPage.Dock -> Text("tab 2")
                    TabPage.AppDrawer -> Text("tab 3")
                }
            }
        }
    }

    @Composable
    fun HomeScreenGridSettings() {
        SliderSetting()
    }

    @Composable
    private fun TabBar(
        tabPage: TabPage,
        onTabSelected: (tabPage: TabPage) -> Unit
    ) {
        Box(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(36.dp).zIndex(1f)) {
            TabRow(
                selectedTabIndex = tabPage.ordinal,
                modifier = Modifier
                    .fillMaxSize().zIndex(2f),
                divider = {},
                backgroundColor = Color.Transparent,
                indicator = { tabPositions -> TabIndicator(tabPositions, tabPage) }) {
                Tab(
                    title = "Home",
                    onClick = { onTabSelected(TabPage.HomeScreen) }
                )
                Tab(
                    title = "Dock",
                    onClick = { onTabSelected(TabPage.Dock) }
                )
                Tab(
                    title = "Drawer",
                    onClick = { onTabSelected(TabPage.AppDrawer) }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
            )
        }
    }

    @Composable
    private fun TabIndicator(
        tabPositions: List<TabPosition>,
        tabPage: TabPage
    ) {
        val transition = updateTransition(tabPage)
        val indicatorLeft by transition.animateDp { page -> tabPositions[page.ordinal].left }
        val indicatorRight by transition.animateDp { page -> tabPositions[page.ordinal].right }
        Box(
            Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.BottomStart)
                .offset(x = indicatorLeft)
                .width(indicatorRight - indicatorLeft)
                .fillMaxSize()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colors.primary),
                    RoundedCornerShape(8.dp)
                )
        )
    }

    @Composable
    private fun Tab(
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title)
        }
    }

    @Preview
    @Composable
    fun SliderSetting() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Text(text = "Test")
            Slider(value = 50F, onValueChange = {}, modifier = Modifier.fillMaxWidth())
        }
    }

    @Preview
    @Composable
    fun Preview() {
        LawnchairTheme {
            Surface(
                color = MaterialTheme.colors.background,
                modifier = Modifier.fillMaxHeight()
            ) {
                Root()
            }
        }
    }

}