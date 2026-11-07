package app.lawnchair.qsb.providers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.resolveIntent
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.patrykmichalik.opto.core.first

sealed class QsbSearchProvider(
    val id: String,
    @StringRes val name: Int,
    @DrawableRes val icon: Int = R.drawable.ic_qsb_search,
    @DrawableRes val themedIcon: Int = icon,
    val themingMethod: ThemingMethod = ThemingMethod.TINT,
    val packageName: String,
    val className: String? = null,
    val action: String? = null,
    val privateAction: String? = null,
    val supportVoiceIntent: Boolean = false,
    val website: String,
    val type: QsbSearchProviderType = QsbSearchProviderType.APP_AND_WEBSITE,
    val sponsored: Boolean = false,
) {

    suspend fun launch(launcher: Launcher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val forceWebsite = prefs.hotseatQsbForceWebsite.first()
        launch(launcher, forceWebsite)
    }

    /**
     * Launches the provider. If [privateAction] is set and the private tab preference is on,
     * the browser is opened on a private tab instead of a regular one.
     */
    open suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        val privateTab = privateAction != null &&
            PreferenceManager2.getInstance(launcher).hotseatQsbPrivateTab.first()
        createLaunchIntent(launcher, forceWebsite, privateTab)
            ?.let { launcher.startActivity(it) }
    }

    /**
     * Picks the intent to launch. The private search intent is preferred when [privateTab] is set,
     * then the regular search intent, and finally the website. Each one is only used if it resolves.
     */
    private fun createLaunchIntent(context: Context, forceWebsite: Boolean, privateTab: Boolean): Intent? {
        if (!forceWebsite) {
            val intent = (if (privateTab) createSearchIntent(privateTab = true) else null)
                ?.takeIf { context.resolveIntent(it) != null }
                ?: createSearchIntent().takeIf { context.resolveIntent(it) != null }
            if (intent != null) {
                return intent
            }
        }
        return createWebsiteIntent().takeIf { context.resolveIntent(it) != null }
    }

    /**
     * Creates the intent that opens the provider's app. With [privateTab], it uses [privateAction]
     * instead of [action].
     */
    fun createSearchIntent(privateTab: Boolean = false) = Intent(if (privateTab) privateAction else action)
        .addFlags(INTENT_FLAGS)
        .apply {
            if (className != null) {
                setClassName(packageName, className)
            } else {
                setPackage(packageName)
            }
        }

    fun createVoiceIntent(): Intent = if (supportVoiceIntent) {
        handleCreateVoiceIntent()
    } else {
        error("supportVoiceIntent is false but createVoiceIntent() was called for $name")
    }

    fun createWebsiteIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(website))
        .addFlags(INTENT_FLAGS)

    open fun handleCreateVoiceIntent(): Intent = Intent(Intent.ACTION_VOICE_COMMAND)
        .addFlags(INTENT_FLAGS)
        .setPackage(packageName)

    /**
     * Checks if the search provider is downloaded.
     * Should only be used for [QsbSearchProviderType]s that are downloadable.
     */
    fun isDownloaded(context: Context) = context.packageManager.isPackageInstalled(packageName)

    /**
     * Launches the search provider's page on an app market.
     * If no app market is installed, it tries to launch the provider's page on Google Play Store.
     *
     * Should only be used for [QsbSearchProviderType]s that are downloadable.
     */
    fun launchOnAppMarket(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$packageName"),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_market_or_browser_installed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    companion object {

        internal const val INTENT_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        fun values() = listOf(
            AppSearch,
            Google,
            GoogleGo,
            Youtube,
            PixelSearch,
            Sesame,
            Wikipedia,
            GitHub,
            DuckDuckGo,
            Ecosia,
            Presearch,
            Bing,
            Brave,
            Yandex,
            Fennec,
            Firefox,
            FirefoxBeta,
            FirefoxNightly,
            Iceraven,
            Startpage,
            StartpageEU,
            IronFox,
            Waterfox,
            Kagi,
            Cromite,
            Vivaldi,
        )

        /**
         * Resolve the search provider using its ID, or use Google as a fallback.
         */
        fun fromId(id: String): QsbSearchProvider = values().firstOrNull { it.id == id } ?: AppSearch

        /**
         * Resolve the default search provider.
         */
        fun resolveDefault(context: Context): QsbSearchProvider {
            val defaultProviderId =
                context.getString(R.string.config_default_qsb_search_provider_id)
            val defaultProvider = fromId(defaultProviderId)
            val isDefaultProviderIntentResolved =
                LawnQsbLayout.resolveIntent(context, defaultProvider.createSearchIntent())

            // Return the default value from config.xml if the value is valid
            if (isDefaultProviderIntentResolved || defaultProvider.type == QsbSearchProviderType.WEBSITE) {
                if (defaultProvider != AppSearch ||
                    (defaultProvider == AppSearch && defaultProviderId == AppSearch.id)
                ) {
                    return defaultProvider
                }
            }

            // Return the best default option if the config.xml value is invalid
            return values()
                .filterNot { it == AppSearch }
                .firstOrNull { LawnQsbLayout.resolveIntent(context, it.createSearchIntent()) }
                ?: AppSearch
        }
    }
}

enum class QsbSearchProviderType(val downloadable: Boolean) {
    APP_AND_WEBSITE(downloadable = true),
    WEBSITE(downloadable = false),
    APP(downloadable = true),
    LOCAL(downloadable = false),
}
