package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

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
    abstract fun getIcon(colored: Boolean): Drawable
    open fun getVoiceIcon(colored: Boolean): Drawable? = if (supportsVoiceSearch)
        throw RuntimeException("Voice search supported but not implemented")
        else null
    open fun getAssistantIcon(colored: Boolean): Drawable? = if (supportsAssistant)
        throw RuntimeException("Assistant supported but not implemented")
        else null

    override fun toString() = this::class.java.name!!
}