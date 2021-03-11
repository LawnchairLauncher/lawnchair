package ch.deletescape.lawnchair.compose.ui.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import ch.deletescape.lawnchair.compose.ui.theme.LawnchairTheme

class PreferenceActivity : ComponentActivity() {
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