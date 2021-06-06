package app.lawnchair.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation

inline fun NavGraphBuilder.preferenceGraph(
    route: String,
    crossinline root: @Composable () -> Unit,
    crossinline block: NavGraphBuilder.(subRoute: (String) -> String) -> Unit = { }
) {
    val subRoute: (String) -> String = { name -> "$route/$name" }
    val rootRoute = subRoute("root")
    navigation(startDestination = rootRoute, route) {
        composable(route = rootRoute) {
            CompositionLocalProvider(LocalRoute provides route) {
                root()
            }
        }
        block(subRoute)
    }
}

inline fun NavGraphBuilder.root(crossinline content: @Composable () -> Unit) {
    composable(route = "") {
        content()
    }
}

val LocalRoute = compositionLocalOf { "" }

@Composable
fun subRoute(name: String): String {
    return "${LocalRoute.current}/$name"
}
