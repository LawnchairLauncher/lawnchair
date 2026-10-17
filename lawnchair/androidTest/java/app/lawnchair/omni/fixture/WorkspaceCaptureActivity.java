package app.lawnchair.omni.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.omnilink.sdk.OmniLinkConstants;
import org.json.JSONException;
import org.json.JSONObject;

/** Platform-only receiver: its separate process cannot borrow the target APK's runtime classes. */
public final class WorkspaceCaptureActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            JSONObject payload = new JSONObject(
                    getIntent().getStringExtra(OmniLinkConstants.EXTRA_PUBLIC_REQUEST_JSON));
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            for (String value : new String[] {
                    "omni-capture-active", payload.optString("kind"), payload.optString("text", "")}) {
                TextView text = new TextView(this);
                text.setText(value);
                text.setTextSize(24f);
                root.addView(text);
            }
            setContentView(root);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid public request", error);
        }
    }
}
