package ch.deletescape.lawnchair.globalsearch

import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

@Keep
class BingSearchProvider(context: Context) : SearchProvider(context) {

    private val PACKAGE = "com.microsoft.bing"

    override val name = context.getString(R.string.search_provider_bing)!!
    override val supportsVoiceSearch: Boolean
        get() = true
    override val supportsAssistant: Boolean
        get() = false

    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "com.microsoft.clients.bing.activities.WidgetSearchActivity").setPackage(PACKAGE))
    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent().setClassName(PACKAGE, "com.microsoft.clients.bing.activities.VoiceActivity").setPackage(PACKAGE))

    override fun getIcon(colored: Boolean): Drawable = context.getDrawable(R.drawable.ic_bing).apply {
             if (!colored) { setTint(Color.WHITE) }
         }

    override fun getVoiceIcon(colored: Boolean): Drawable = context.getDrawable(R.drawable.ic_mic_color).mutate().apply {
         setTint(if (colored) Color.parseColor("#00897B") else Color.WHITE)
    }
}