package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.sesame.Sesame
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper
import ninja.sesame.lib.bridge.v1_1.LookFeelKeys

class SesameSearchProvider(context: Context) : SearchProvider(context), LawnchairPreferences.OnPreferenceChangeListener {

    override val name: String = context.getString(R.string.sesame)
    override val supportsVoiceSearch: Boolean
        get() = true
    override val supportsAssistant: Boolean
        get() = true
    override val supportsFeed = false
    override val settingsIntent get () = Intent(Sesame.ACTION_OPEN_SETTINGS).setPackage(Sesame.PACKAGE)

    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, Sesame.PACKAGE, 0)

    private val prefs = context.lawnchairPrefs

    init {
        prefs.addOnPreferenceChangeListener("pref_sesameIconColor", this)
    }

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent("ninja.sesame.app.action.OPEN_SEARCH").setPackage(Sesame.PACKAGE))

    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = startAssistant(callback)

    override fun startAssistant(callback: (intent: Intent) -> Unit) = if (PackageManagerHelper.isAppEnabled(context.packageManager, GoogleSearchProvider.PACKAGE, 0)) {
        callback(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GoogleSearchProvider.PACKAGE))
    } else callback(Intent(Intent.ACTION_ASSIST))

    private var iconCache: Drawable? = null

    override fun getIcon(): Drawable = iconCache ?: context.getDrawable(R.drawable.ic_sesame_large)!!.mutate().apply {
        setTint(getTint(context))
        iconCache = this
    }

    override fun getVoiceIcon(): Drawable? = getAssistantIcon()

    private var assistantCache: Drawable? = null

    override fun getAssistantIcon(): Drawable? = assistantCache ?: context.getDrawable(R.drawable.opa_assistant_logo)!!.mutate().apply {
        setTint(getTint(context))
        assistantCache = this
    }

    private fun getTint(context: Context): Int {
        if (Sesame.isAvailable(context) && prefs.syncLookNFeelWithSesame) {
            (Sesame.LookAndFeel[LookFeelKeys.SEARCH_ICON_COLOR] as? Int)?.let {
                return it
            }
        }
        return ColorEngine.getInstance(context).accent
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        val tint = getTint(context)
        iconCache?.setTint(tint)
        iconCache?.invalidateSelf()
        assistantCache?.setTint(tint)
        assistantCache?.invalidateSelf()
    }
}
