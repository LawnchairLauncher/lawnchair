package ch.deletescape.lawnchair.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.launcher3.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()
    }

    // TODO: Migrate missing code from ch.deletescape.settings.ui.SettingsActivity.
}