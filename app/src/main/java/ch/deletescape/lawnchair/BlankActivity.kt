package ch.deletescape.lawnchair

import android.app.Activity
import android.os.Bundle


class BlankActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null || !intent.hasExtra("action")) {
            finish()
            return
        }

        when (intent.getStringExtra("action")) {
            "setDefaultLauncher" -> Utilities.setDefaultLauncher(this)
        }

        finish()
    }
}
