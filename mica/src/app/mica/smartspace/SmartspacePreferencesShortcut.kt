package app.mica.smartspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.mica.ui.preferences.PreferenceActivity
import app.mica.ui.preferences.navigation.SmartspaceWidget

class SmartspacePreferencesShortcut : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(PreferenceActivity.createIntent(this, SmartspaceWidget))
        finish()
    }
}
