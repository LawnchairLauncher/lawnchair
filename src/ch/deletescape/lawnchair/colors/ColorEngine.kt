package ch.deletescape.lawnchair.colors

import android.content.Context
import android.support.v7.graphics.Palette
import android.text.TextUtils
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.Utilities
import java.util.HashSet

class ColorEngine private constructor(val context: Context) : LawnchairPreferences.OnPreferenceChangeListener {

    private val KEY_ACCENT_RESOLVER = "pref_accentColorResolver"
    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val accentListeners = HashSet<OnAccentChangeListener>()

    private val defaultColorResolver = PixelAccentResolver(ColorResolver.Config(this))

    var accentResolver by createResolverPref(KEY_ACCENT_RESOLVER)
    val accent get() = accentResolver.resolveColor()
    val accentForeground get() = accentResolver.computeForegroundColor()

    init {
        prefs.addOnPreferenceChangeListener(this, KEY_ACCENT_RESOLVER)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        when(key) {
            KEY_ACCENT_RESOLVER -> {
                accentResolver.startListening()
                notifyAccentChanged()
            }
        }
    }

    private fun onColorChanged(colorResolver: ColorResolver) {
        notifyAccentChanged()
    }

    private fun notifyAccentChanged() {
        runOnMainThread { accentListeners.forEach { it.onAccentChange(accent, accentForeground) } }
    }

    fun addAccentChangeListener(listener: OnAccentChangeListener) {
        accentListeners.add(listener)
        listener.onAccentChange(accent, accentForeground)
    }

    fun removeAccentChangeListener(listener: OnAccentChangeListener) = accentListeners.remove(listener)

    private fun createResolverPref(key: String, defaultValue: ColorResolver = defaultColorResolver) =
            prefs.StringBasedPref(key, defaultValue, prefs.doNothing, ::createColorResolver,
                    ColorResolver::toString, ColorResolver::onDestroy)

    private fun createColorResolver(string: String): ColorResolver {
        try {
            val parts = string.split("|")
            val className = parts[0]
            val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(ColorResolver.Config::class.java)
            return constructor.newInstance(ColorResolver.Config(this, ::onColorChanged, args)) as ColorResolver
        } catch (e: IllegalStateException) {
        } catch (e: ClassNotFoundException) {
        } catch (e: InstantiationException) {
        }
        return defaultColorResolver
    }

    companion object : SingletonHolder<ColorEngine, Context>(ensureOnMainThread(useApplicationContext(::ColorEngine)))

    interface OnAccentChangeListener {
        fun onAccentChange(color: Int, foregroundColor: Int)
    }

    abstract class ColorResolver(val config: Config) {

        private var listening = false
        val engine get() = config.engine
        val args get() = config.args

        abstract fun resolveColor(): Int

        abstract fun getDisplayName(): String

        override fun toString() = TextUtils.join("|", listOf(this::class.java.name) + args) as String

        open fun computeForegroundColor(): Int {
            return Palette.Swatch(resolveColor(), 1).bodyTextColor
        }

        open fun startListening() {
            listening = true
        }

        open fun stopListening() {
            listening = false
        }

        fun notifyChanged() {
            config.listener?.invoke(this)
        }

        fun onDestroy() {
            if (listening) {
                stopListening()
            }
        }

        class Config(
                val engine: ColorEngine,
                val listener: ((ColorResolver) -> Unit)? = null,
                val args: List<String> = emptyList())
    }
}
