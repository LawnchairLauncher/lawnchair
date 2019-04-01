package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.settings.ui.SettingsSearchActivity
import com.android.launcher3.R
import com.android.launcher3.graphics.ShadowDrawable

abstract class SearchProvider(protected val context: Context) {
    abstract val name: String
    abstract val supportsVoiceSearch: Boolean
    abstract val supportsAssistant: Boolean
    abstract val supportsFeed: Boolean
    open val settingsIntent get() = Intent().setClass(context, SettingsSearchActivity::class.java)
    /**
     * Whether the settings intent needs to be sent as broadcast
     */
    open val isBroadcast = false
    open val isAvailable: Boolean = true

    abstract fun startSearch(callback: (intent: Intent) -> Unit = {})
    open fun startVoiceSearch(callback: (intent: Intent) -> Unit = {}) {
        if (supportsVoiceSearch) throw RuntimeException("Voice search supported but not implemented")
    }
    open fun startAssistant(callback: (intent: Intent) -> Unit = {}) {
        if (supportsAssistant) throw RuntimeException("Assistant supported but not implemented")
    }

    open fun startFeed(callback: (intent: Intent) -> Unit = {}) {
        if (supportsFeed) throw RuntimeException("Feed supported but not implemented")
    }

    protected fun wrapInShadowDrawable(d: Drawable): Drawable {
        return ShadowDrawable.wrap(context, d, R.color.qsb_icon_shadow_color,
                4f, R.color.qsb_dark_icon_tint).apply { applyTheme(context.theme) }
    }

    fun getIcon(colored: Boolean) = if (colored) getIcon() else getShadowIcon()

    abstract fun getIcon(): Drawable
    open fun getShadowIcon(): Drawable {
        return wrapInShadowDrawable(getIcon())
    }

    fun getVoiceIcon(colored: Boolean) = if (colored) getVoiceIcon() else getShadowVoiceIcon()

    open fun getVoiceIcon(): Drawable? = if (supportsVoiceSearch)
        throw RuntimeException("Voice search supported but not implemented")
        else null
    open fun getShadowVoiceIcon() = getVoiceIcon()?.let { wrapInShadowDrawable(it) }

    fun getAssistantIcon(colored: Boolean) = if (colored) getAssistantIcon() else getShadowAssistantIcon()

    open fun getAssistantIcon(): Drawable? = if (supportsAssistant)
        throw RuntimeException("Assistant supported but not implemented")
        else null
    open fun getShadowAssistantIcon() = getAssistantIcon()?.let { wrapInShadowDrawable(it) }

    override fun toString() = this::class.java.name!!
}
