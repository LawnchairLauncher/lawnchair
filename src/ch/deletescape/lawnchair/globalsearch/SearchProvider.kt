package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.android.launcher3.R
import com.android.launcher3.graphics.ShadowDrawable

abstract class SearchProvider(protected val context: Context) {
    abstract val name: String
    abstract val supportsVoiceSearch: Boolean
    abstract val supportsAssistant: Boolean
    open val isAvailable: Boolean = true

    abstract fun startSearch(callback: (intent: Intent) -> Unit = {})
    open fun startVoiceSearch(callback: (intent: Intent) -> Unit = {}) {
        if (supportsVoiceSearch) throw RuntimeException("Voice search supported but not implemented")
    }
    open fun startAssistant(callback: (intent: Intent) -> Unit = {}) {
        if (supportsAssistant) throw RuntimeException("Assistant supported but not implemented")
    }

    protected fun wrapInShadowDrawable(d: Drawable): Drawable {
        return (context.getDrawable(R.drawable.ic_super_g_shadow) as ShadowDrawable).setChild(d)
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
