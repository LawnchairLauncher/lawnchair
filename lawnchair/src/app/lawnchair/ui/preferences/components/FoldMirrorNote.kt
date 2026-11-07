package app.lawnchair.ui.preferences.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import com.android.launcher3.R

/**
 * Whether the large screen of a foldable uses "Same as cover": the "when unfolded" grid settings
 * (dock icons, drawer columns) don't apply then, the large screen uses the cover grid.
 */
@Composable
fun isFoldHomeMirrored(): Boolean {
    val homeLayout by preferenceManager2().foldHomeLayout.asState()
    return homeLayout == FoldHomeLayout.MIRROR
}

/** Explains why the "when unfolded" grid settings are disabled while "Same as cover" is selected. */
@Composable
fun FoldMirrorNote(modifier: Modifier = Modifier) {
    FoldNote(text = stringResource(id = R.string.fold_unfolded_grid_mirror_note), modifier = modifier)
}

/**
 * Whether the large screen of a foldable has its own layout ("Independent"): the dock doesn't merge
 * and each screen reopens on its own page, so those settings don't apply.
 */
@Composable
fun isFoldHomeIndependent(): Boolean {
    val homeLayout by preferenceManager2().foldHomeLayout.asState()
    return homeLayout == FoldHomeLayout.INDEPENDENT
}

/** A note under a foldable setting that doesn't apply with the current home layout. */
@Composable
fun FoldNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    WarningPreference(
        text = text,
        modifier = modifier,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
