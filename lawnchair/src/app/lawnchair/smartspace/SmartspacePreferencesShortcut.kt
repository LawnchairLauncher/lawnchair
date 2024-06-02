package app.lawnchair.smartspace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Routes

class SmartspacePreferencesShortcut : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(PreferenceActivity.createIntent(this, Routes.SMARTSPACE_WIDGET))
        finish()
    }
}
