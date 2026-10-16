package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.omni.experience.OmniHubActivity

class OpenShelfHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        launcher.startActivity(OmniHubActivity.intent(launcher))
    }
}
