package app.lawnchair.ui.preferences.components

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import com.android.launcher3.R

@ExperimentalMaterialApi
@Composable
fun IconShapePreference() {
    val context = LocalContext.current
    val entries = remember {
        val systemShape = IconShapeManager.getSystemIconShape(context)
        listOf<ListPreferenceEntry<IconShape>>(
            ListPreferenceEntry(systemShape) { stringResource(id = R.string.icon_shape_system) },
            ListPreferenceEntry(IconShape.Circle) { stringResource(id = R.string.icon_shape_circle) },
            ListPreferenceEntry(IconShape.Cupertino) { stringResource(id = R.string.icon_shape_rounded_square) },
            ListPreferenceEntry(IconShape.Squircle) { stringResource(id = R.string.icon_shape_squircle) },
        )
    }

    val prefs = preferenceManager()
    ListPreference(
        adapter = prefs.iconShape.getAdapter(),
        entries = entries,
        label = stringResource(id = R.string.icon_shape_label)
    )
}
