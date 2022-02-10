package app.lawnchair.qsb

import android.content.Intent
import androidx.annotation.DrawableRes
import com.android.launcher3.R

sealed class QsbSearchProvider(
    val name: String,
    @DrawableRes val icon: Int = R.drawable.ic_qsb_search,
    @DrawableRes val themedIcon: Int = icon,
    val themingMethod: ThemingMethod = ThemingMethod.TINT,
    open val packageName: String,
    open val action: String? = null,
    open val supportVoiceIntent: Boolean = false
) {

    fun createSearchIntent() = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setPackage(packageName)

    fun createVoiceIntent(): Intent = if (supportVoiceIntent) {
        handleCreateVoiceIntent()
    } else {
        error("supportVoiceIntent is false but createVoiceIntent() was called for $name")
    }

    open fun handleCreateVoiceIntent(): Intent =
        Intent(Intent.ACTION_VOICE_COMMAND)
            .addFlags(INTENT_FLAGS)
            .setPackage(packageName)

    /**
     * Index should only be used on known providers, otherwise it returns -1
     *
     * @see [QsbSearchProvider.values]
     */
    val index
        get() = values().indexOf(this)

    object None : QsbSearchProvider(name = "", packageName = "")

    data class UnknownProvider(
        override val packageName: String,
        override val action: String? = null
    ) : QsbSearchProvider(name = "", packageName = packageName, action = action)

    object Google : QsbSearchProvider(
        name = "Google",
        icon = R.drawable.ic_super_g_color,
        themingMethod = ThemingMethod.THEME_BY_NAME,
        packageName = "com.google.android.googlequicksearchbox",
        action = "android.search.action.GLOBAL_SEARCH",
        supportVoiceIntent = true
    )

    object GoogleGo : QsbSearchProvider(
        name = "Google Go",
        icon = R.drawable.ic_super_g_color,
        themingMethod = ThemingMethod.THEME_BY_NAME,
        packageName = "com.google.android.apps.searchlite",
        action = "android.search.action.GLOBAL_SEARCH",
        supportVoiceIntent = true
    ) {

        override fun handleCreateVoiceIntent(): Intent =
            createSearchIntent().putExtra("openMic", true)
    }

    object Duck : QsbSearchProvider(
        name = "DuckDuckGo",
        icon = R.drawable.ic_duckduckgo,
        themedIcon = R.drawable.ic_duckduckgo_tinted,
        themingMethod = ThemingMethod.TINT,
        packageName = "com.duckduckgo.mobile.android",
        action = "com.duckduckgo.mobile.android.NEW_SEARCH"
    )

    companion object {

        internal const val INTENT_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        fun values() = listOf(
            Google,
            GoogleGo,
            Duck
        )

        /**
         * Resolve the search provider using its index, or use Google as a fallback.
         */
        fun resolve(value: Int): QsbSearchProvider =
            values().getOrElse(value) { Google }

        fun resolve(packageName: String): QsbSearchProvider =
            values().firstOrNull { it.packageName == packageName } ?: UnknownProvider(packageName)
    }
}