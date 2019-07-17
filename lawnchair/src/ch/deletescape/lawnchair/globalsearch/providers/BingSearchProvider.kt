package ch.deletescape.lawnchair.globalsearch.providers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

@Keep
class BingSearchProvider(context: Context) : SearchProvider(context) {

    private val cortanaInstalled: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE_CORTANA, 0)
    private val alexaInstalled: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE_ALEXA, 0)

    override val name: String = context.getString(R.string.search_provider_bing)
    override val supportsVoiceSearch: Boolean
        get() = true
    override val supportsAssistant: Boolean
        get() = cortanaInstalled || alexaInstalled
    override val supportsFeed = false

    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "com.microsoft.clients.bing.widget.WidgetSearchActivity").setPackage(PACKAGE))
    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_SEARCH_LONG_PRESS).setPackage(PACKAGE))
    override fun startAssistant(callback: (intent: Intent) -> Unit) = callback(if (cortanaInstalled) {
        Intent().setClassName(PACKAGE_CORTANA, "com.microsoft.bing.dss.assist.AssistProxyActivity").setPackage(PACKAGE_CORTANA)
    } else {
        Intent(Intent.ACTION_ASSIST).setPackage(PACKAGE_ALEXA)
    })

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_bing)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_mic_color)!!.mutate().apply {
        setTint(Color.parseColor("#00897B"))
    }

    override fun getAssistantIcon(): Drawable = context.getDrawable(if (cortanaInstalled) {
        R.drawable.ic_cortana
    } else {
        R.drawable.ic_alexa
    })!!

    override fun getShadowAssistantIcon(): Drawable? {
        if (cortanaInstalled) {
            return wrapInShadowDrawable(context.getDrawable(R.drawable.ic_cortana_shadow)!!)
        }
        return super.getShadowAssistantIcon()
    }

    companion object {
        private const val PACKAGE = "com.microsoft.bing"
        private const val PACKAGE_CORTANA = "com.microsoft.cortana"
        private const val PACKAGE_ALEXA = "com.amazon.dee.app"
    }
}
