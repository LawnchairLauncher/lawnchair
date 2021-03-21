package ch.deletescape.lawnchair.ui.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import ch.deletescape.lawnchair.ui.theme.LawnchairTheme

class PreferenceActivity : ComponentActivity() {
    @ExperimentalAnimationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SystemUi(windows = this.window)
            LawnchairTheme {
                Preferences()
            }
        }
    }
}