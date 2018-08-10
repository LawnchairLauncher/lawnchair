package ch.deletescape.lawnchair.globalsearch

import android.content.*
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

@Keep
class GoogleSearchProvider(context: Context) : SearchProvider(context) {

    override val name = context.getString(R.string.search_provider_google)!!
    override val supportsVoiceSearch: Boolean
        get() = true
    override val supportsAssistant: Boolean
        get() = true

    override fun startSearch(callback: (intent: Intent) -> Unit) {
        throw RuntimeException("Google Search has to be handled locally")
//        LauncherAppState.getInstanceNoCreate().launcher.startGlobalSearch(null, false, null, null)
    }

    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = throw RuntimeException("Google Search has to be handled locally")

    override fun startAssistant(callback: (intent: Intent) -> Unit ) = throw RuntimeException("Google Search has to be handled locally")

    override fun getIcon(colored: Boolean): Drawable = context.getDrawable(if (colored) {
             R.drawable.ic_super_g_color
        } else {
            R.drawable.ic_super_g_shadow
        })

    override fun getVoiceIcon(colored: Boolean): Drawable =context.getDrawable(if (colored) {
             R.drawable.ic_mic_color
        } else {
            R.drawable.ic_mic_shadow
        })

    override fun getAssistantIcon(colored: Boolean): Drawable = context.getDrawable(if (colored) {
            R.drawable.opa_assistant_logo
        } else {
            R.drawable.opa_assistant_logo_shadow
        })
}
