package ch.deletescape.lawnchair.ui.preferences

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.navigate

@Composable
fun PreferenceCategoryList(navController: NavController) {
    LazyColumn {
        items(screens) { screen ->
            PreferenceCategoryLink(
                titleResId = screen.labelResId,
                subtitleResId = screen.descriptionResId,
                iconResId = screen.iconResId,
                onClick = { navController.navigate(screen.route) })
        }
    }
}