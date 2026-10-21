package app.lawnchair.root;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

import androidx.core.content.ContextCompat;

public class RootHelperBackend extends IRootHelper.Stub {
    public static final String GLASS_BLUR_LAYER_KEY = "layer";

    // The client holds its own reference to the layer once the reply is parceled
    private static final long GLASS_BLUR_LAYER_RELEASE_DELAY_MS = 10_000;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public RootHelperBackend(Context context) {
        mContext = context;
    }

    private PowerManager getPowerManager() {
        return ContextCompat.getSystemService(mContext, PowerManager.class);
    }

    @Override
    public void goToSleep() {
        getPowerManager().goToSleep(SystemClock.uptimeMillis());
    }

    @Override
    public Bundle createGlassBlurLayer() {
        // SurfaceFlinger records the permissions of the creating process on the layer
        SurfaceControl layer = new SurfaceControl.Builder(new SurfaceSession())
                .setName("LawnchairGlassBlur")
                .setEffectLayer()
                .build();
        Bundle result = new Bundle();
        result.putParcelable(GLASS_BLUR_LAYER_KEY, layer);
        mMainHandler.postDelayed(layer::release, GLASS_BLUR_LAYER_RELEASE_DELAY_MS);
        return result;
    }
}
