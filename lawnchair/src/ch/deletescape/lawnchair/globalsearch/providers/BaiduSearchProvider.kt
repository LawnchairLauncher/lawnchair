package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

@Keep
class BaiduSearchProvider(context: Context) : SearchProvider(context) {


    override val name: String = context.getString(R.string.search_provider_baidu)
    override val supportsVoiceSearch = true
    override val supportsAssistant = false
    override val supportsFeed = true

    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(PACKAGE))
    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_SEARCH_LONG_PRESS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(PACKAGE))
    override fun startFeed(callback: (intent: Intent) -> Unit) = callback(Intent("$PACKAGE.action.HOME").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(PACKAGE))

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_baidu)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_mic_color)!!.mutate().apply {
        setTint(Color.parseColor("#2d03e4"))
    }

    companion object {
        const val PACKAGE = "com.baidu.searchbox"
    }
}
