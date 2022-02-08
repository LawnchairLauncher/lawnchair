package app.lawnchair.qsb

import android.content.Intent

sealed class QsbSearchProvider(
    val name: String,
    open val packageName: String,
    open val action: String? = null
) {

    fun createIntent() = Intent(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .setPackage(packageName)

    /**
     * Index should only be used on known providers, otherwise it returns -1
     *
     * @see [QsbSearchProvider.values]
     */
    val index
        get() = values().indexOf(this)

    object None: QsbSearchProvider("", "", "")

    data class UnknownProvider(
        override val packageName: String,
        override val action: String? = null
    ) : QsbSearchProvider("", packageName, action)

    object Google : QsbSearchProvider(
        "Google",
        packageName = "com.google.android.googlequicksearchbox",
        action = "android.search.action.GLOBAL_SEARCH",
    )

    object GoogleGo : QsbSearchProvider(
        "Google Go",
        packageName = "com.google.android.apps.searchlite",
        action = "android.search.action.GLOBAL_SEARCH",
    )

    object Duck : QsbSearchProvider(
        name = "DuckDuckGo",
        packageName = "com.duckduckgo.mobile.android",
        action = "com.duckduckgo.mobile.android.NEW_SEARCH"
    )

    companion object {

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