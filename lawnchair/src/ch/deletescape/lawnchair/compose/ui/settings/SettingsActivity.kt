package ch.deletescape.lawnchair.compose.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import ch.deletescape.lawnchair.settings.ui.theme.LawnchairTheme

class SettingsActivity : ComponentActivity() {
    @ExperimentalAnimationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                Settings()
            }
        }
    }
}