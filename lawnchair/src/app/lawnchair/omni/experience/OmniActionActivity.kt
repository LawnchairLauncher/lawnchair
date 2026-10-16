package app.lawnchair.omni.experience

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Explicit local dispatcher. Text in search never executes an action. */
class OmniActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                val action = intent.getStringExtra("action").orEmpty()
                if (action.startsWith("scene:")) {
                    SceneController.apply(this@OmniActionActivity, action.removePrefix("scene:"))
                } else {
                    startActivity(ActionSearchProvider.destination(this@OmniActionActivity, action))
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(this@OmniActionActivity, com.android.launcher3.R.string.omni_action_unavailable, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
    companion object {
        fun intent(context: Context, action: String): Intent = Intent(context, OmniActionActivity::class.java).putExtra("action", action)
    }
}
