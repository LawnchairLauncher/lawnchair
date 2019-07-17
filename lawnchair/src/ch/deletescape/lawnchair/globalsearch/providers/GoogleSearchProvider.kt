package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R

@Keep
class GoogleSearchProvider(context: Context) : SearchProvider(context) {

    override val name: String = context.getString(R.string.google)
    override val supportsVoiceSearch = true
    override val supportsAssistant = true
    override val supportsFeed = true
    override val settingsIntent: Intent
        get() = Intent("com.google.android.apps.gsa.nowoverlayservice.PIXEL_DOODLE_QSB_SETTINGS")
                .setPackage(PACKAGE).addFlags(268435456)
    override val isBroadcast: Boolean
        get() = true


    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "$PACKAGE.SearchActivity"))

    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent("android.intent.action.VOICE_ASSIST").setPackage(PACKAGE))

    override fun startAssistant(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(PACKAGE))

    override fun startFeed(callback: (intent: Intent) -> Unit) {
        val launcher = LawnchairLauncher.getLauncher(context)
        if (launcher.googleNow != null) {
            launcher.googleNow?.showOverlay(true)
        } else {
            callback(Intent(Intent.ACTION_MAIN).setClassName(PACKAGE, "$PACKAGE.SearchActivity"))
        }
    }

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_super_g_color)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_mic_color)!!

    override fun getAssistantIcon(): Drawable = context.getDrawable(R.drawable.opa_assistant_logo)!!

    companion object {
        internal const val PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
