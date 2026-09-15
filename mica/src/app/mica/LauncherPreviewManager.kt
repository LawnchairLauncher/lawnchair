package app.mica

import android.content.Context
import androidx.compose.runtime.RememberObserver
import app.mica.views.LauncherPreviewView
import com.android.launcher3.InvariantDeviceProfile

class LauncherPreviewManager(private val context: Context) : RememberObserver {

    private var activePreview: LauncherPreviewView? = null

    fun createPreviewView(idp: InvariantDeviceProfile): LauncherPreviewView {
        destroyActivePreview()
        activePreview = LauncherPreviewView(context, idp)
        return activePreview!!
    }

    private fun destroyActivePreview() {
        activePreview?.destroy()
    }

    override fun onRemembered() {
    }

    override fun onForgotten() {
        destroyActivePreview()
    }

    override fun onAbandoned() {
        destroyActivePreview()
    }
}
