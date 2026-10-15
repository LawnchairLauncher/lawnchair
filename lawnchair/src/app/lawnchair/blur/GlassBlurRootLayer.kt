package app.lawnchair.blur

import android.content.Context
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import androidx.annotation.UiThread
import app.lawnchair.root.RootHelperBackend
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * Hands out effect layers created by the root helper. SurfaceFlinger never ignores the blur of
 * layers created by privileged processes, so their blur regions are exempt from the limit of 10
 * blur requests that applies to app layers.
 */
internal object GlassBlurRootLayer {
    private const val TAG = "LC-GlassBlur"

    private val scope = MainScope() + CoroutineName("GlassBlurRootLayer")

    /**
     * Calls back on the main thread with a new layer. Never calls back without root.
     */
    @UiThread
    fun request(context: Context, callback: (SurfaceControl) -> Unit) {
        val rootHelperManager = RootHelperManager.INSTANCE.get(context)
        scope.launch {
            try {
                if (!rootHelperManager.isAvailable()) {
                    Log.i(TAG, "Root not available, blur limited to 10 regions on screen")
                    return@launch
                }
                val rootHelper = rootHelperManager.getService()
                val layer = withContext(Dispatchers.IO) {
                    rootHelper.createGlassBlurLayer().getParcelable(
                        RootHelperBackend.GLASS_BLUR_LAYER_KEY,
                        SurfaceControl::class.java,
                    )
                }
                if (layer != null) {
                    callback(layer)
                }
            } catch (_: RootNotAvailableException) {
            } catch (e: RemoteException) {
                Log.w(TAG, "Unable to create the blur layer", e)
            }
        }
    }
}
