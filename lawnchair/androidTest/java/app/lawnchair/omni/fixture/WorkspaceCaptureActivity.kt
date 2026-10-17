package app.lawnchair.omni.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.omnilink.sdk.OmniLinkConstants
import org.json.JSONObject

/** Separate-process public receiver: no service, signer grant, agent run or network request. */
class WorkspaceCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = JSONObject(intent.getStringExtra(OmniLinkConstants.EXTRA_PUBLIC_REQUEST_JSON) ?: "{}")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (value in listOf("omni-capture-active", payload.optString("kind"), payload.optString("text", ""))) {
            root.addView(
                TextView(this).apply {
                    text = value
                    textSize = 24f
                },
            )
        }
        setContentView(root)
    }
}
