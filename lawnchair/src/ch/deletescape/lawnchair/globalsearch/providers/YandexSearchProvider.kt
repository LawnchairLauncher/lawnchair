package ch.deletescape.lawnchair.globalsearch.providers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

@Keep
class YandexSearchProvider(context: Context) : SearchProvider(context) {

    override val name: String = context.getString(R.string.search_provider_yandex)
    override val supportsVoiceSearch = true
    override val supportsAssistant = true
    override val supportsFeed = true
    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_WEB_SEARCH).setPackage(PACKAGE))
    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_ASSIST).setPackage(PACKAGE))
    override fun startAssistant(callback: (intent: Intent) -> Unit) = startVoiceSearch(callback)
    override fun startFeed(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity"))

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_yandex)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_alisa_yandex)!!

    override fun getAssistantIcon(): Drawable = getVoiceIcon()

    companion object {
        const val PACKAGE = "ru.yandex.searchplugin"
    }
}
