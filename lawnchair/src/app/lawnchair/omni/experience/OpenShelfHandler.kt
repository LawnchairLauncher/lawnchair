package app.lawnchair.omni.experience

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.handlers.GestureHandler

class OpenShelfHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        launcher.startActivity(OmniHubActivity.intent(launcher))
    }
}
