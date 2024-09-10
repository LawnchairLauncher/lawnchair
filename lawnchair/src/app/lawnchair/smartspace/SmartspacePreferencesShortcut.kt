package app.lawnchair.smartspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Routes

class SmartspacePreferencesShortcut : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(PreferenceActivity.createIntent(this, Routes.SMARTSPACE_WIDGET))
        finish()
    }
}
