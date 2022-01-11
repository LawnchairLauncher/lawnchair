package app.lawnchair.ui.preferences

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.icons.IconPack
import app.lawnchair.icons.IconPackProvider
import app.lawnchair.theme.surfaceColorAtElevation
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.value
import com.google.accompanist.insets.ui.LocalScaffoldPadding
import com.google.accompanist.navigation.animation.composable
import java.util.*

@ExperimentalAnimationApi
fun NavGraphBuilder.iconPickerGraph(route: String) {
    preferenceGraph(route, { }) { subRoute ->
        composable(
            route = subRoute("{packageName}"),
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val packageName = args.getString("packageName")!!
            IconPickerPreference(packageName)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalAnimationApi
@Composable
fun IconPickerPreference(packageName: String) {
    val optionalIconPack = loadIconPack(packPackageName = packageName)
    if (optionalIconPack?.isPresent == false) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        SideEffect {
            backDispatcher?.onBackPressed()
        }
        return
    }

    val iconPack = optionalIconPack?.value
    var searchQuery by remember { mutableStateOf("") }

    PreferenceSearchScaffold(
        searchInput = {
            SearchTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text(iconPack?.label ?: "") },
                singleLine = true
            )
        }
    ) {
        val scaffoldPadding = LocalScaffoldPadding.current
        val innerPadding = remember { MutablePaddingValues() }
        val layoutDirection = LocalLayoutDirection.current
        innerPadding.left = scaffoldPadding.calculateLeftPadding(layoutDirection)
        innerPadding.right = scaffoldPadding.calculateRightPadding(layoutDirection)
        innerPadding.bottom = scaffoldPadding.calculateBottomPadding()

        val topPadding = scaffoldPadding.calculateTopPadding()

        CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
            PreferenceLazyColumn(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = topPadding)
            ) {
                for (i in (0 until 4)) {
                    stickyHeader {
                        Text(
                            text = "Category $i",
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = 16.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    verticalGridItems(
                        count = 18,
                        numColumns = 5,
                        gap = 16.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(shape = CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                        )
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .height(topPadding)
        )
    }
}

@Composable
fun loadIconPack(packPackageName: String): Optional<IconPack>? {
    val context = LocalContext.current
    return produceState<Optional<IconPack>?>(initialValue = null) {
        val iconPack = IconPackProvider.INSTANCE.get(context).getIconPack(packPackageName)
        iconPack?.load()
        value = Optional.ofNullable(iconPack)
    }.value
}
