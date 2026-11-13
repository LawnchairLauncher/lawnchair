package app.lawnchair.fold

import android.content.Intent
import androidx.compose.runtime.Composable
import app.lawnchair.LawnchairLauncher

/** Release builds have no fold transition replay: the debug one is in `lawnchair/src_debug`. */
object FoldReplay {

    /** No intent is ours. */
    fun onNewIntent(launcher: LawnchairLauncher, intent: Intent): Boolean = false

    /** No debug menu entries. */
    @Composable
    internal fun Preferences() = Unit
}
