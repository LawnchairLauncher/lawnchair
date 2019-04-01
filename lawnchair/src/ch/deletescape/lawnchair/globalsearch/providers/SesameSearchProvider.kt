package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import ch.deletescape.lawnchair.sesame.Sesame
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

class SesameSearchProvider(context: Context) : SearchProvider(context) {

    override val name = context.getString(R.string.sesame)!!
    override val supportsVoiceSearch: Boolean
        get() = false
    override val supportsAssistant: Boolean
        get() = false
    override val supportsFeed = false
    override val settingsIntent get () = Intent(Sesame.ACTION_OPEN_SETTINGS).setPackage(Sesame.PACKAGE)

    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, Sesame.PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent("ninja.sesame.app.action.OPEN_SEARCH").setPackage(Sesame.PACKAGE))

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_sesame_large)!!.mutate().apply {
             setTint(ColorEngine.getInstance(context).accent)
         }

}
