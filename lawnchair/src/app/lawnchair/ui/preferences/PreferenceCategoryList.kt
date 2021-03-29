package app.lawnchair.ui.preferences

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.navigate
import app.lawnchair.util.preferences.getMajorVersion
import com.android.launcher3.R

@Composable
fun PreferenceCategoryList(navController: NavController) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxHeight()
    ) {
        items(getPreferenceCategories(context)) { item ->
            PreferenceCategoryListItem(
                label = item.label,
                description = item.description,
                iconResource = item.iconResource,
                onClick = { navController.navigate(item.route) })
        }
    }
}