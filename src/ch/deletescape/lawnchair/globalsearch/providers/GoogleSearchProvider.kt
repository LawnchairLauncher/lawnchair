package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.globalsearch.SearchProvider
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

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_super_g_color)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_mic_color)!!

    override fun getAssistantIcon(): Drawable = context.getDrawable(R.drawable.opa_assistant_logo)!!
}
