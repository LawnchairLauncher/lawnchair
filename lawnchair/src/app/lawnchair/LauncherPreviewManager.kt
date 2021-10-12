package app.lawnchair

import android.content.Context
import androidx.compose.runtime.RememberObserver
import app.lawnchair.views.LauncherPreviewView

class LauncherPreviewManager(private val context: Context) : RememberObserver {

    private var activePreview: LauncherPreviewView? = null

    fun createPreviewView(options: DeviceProfileOverrides.Options): LauncherPreviewView {
        destroyActivePreview()
        activePreview = LauncherPreviewView(context, options)
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
