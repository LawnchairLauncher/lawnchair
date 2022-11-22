package app.lawnchair.qsb.providers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.QsbLayout
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
    val supportVoiceIntent: Boolean = false,
    val website: String,
    val type: QsbSearchProviderType = QsbSearchProviderType.APP_AND_WEBSITE,
) {

    suspend fun launch(launcher: Launcher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val forceWebsite = prefs.hotseatQsbForceWebsite.first()
        launch(launcher, forceWebsite)
    }

    open suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        createLaunchIntent(launcher, forceWebsite)
            ?.let { launcher.startActivity(it) }
    }

    private fun createLaunchIntent(context: Context, forceWebsite: Boolean): Intent? {
        if (!forceWebsite) {
            val intent = createSearchIntent().takeIf { context.resolveIntent(it) != null }
            if (intent != null) {
                return intent
            }
        }
        return createWebsiteIntent().takeIf { context.resolveIntent(it) != null }
    }

    fun createSearchIntent() = Intent(action)
        .addFlags(INTENT_FLAGS)
        .apply {
            if (className != null) setClassName(packageName, className)
            else setPackage(packageName)
        }

    fun createVoiceIntent(): Intent = if (supportVoiceIntent) {
        handleCreateVoiceIntent()
    } else {
        error("supportVoiceIntent is false but createVoiceIntent() was called for $name")
    }

    fun createWebsiteIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(website))
        .addFlags(INTENT_FLAGS)

    open fun handleCreateVoiceIntent(): Intent =
        Intent(Intent.ACTION_VOICE_COMMAND)
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
                    Uri.parse("market://details?id=$packageName")
                )
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_market_or_browser_installed),
                    Toast.LENGTH_SHORT
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
            DuckDuckGo,
            Presearch,
            Wikipedia,
            Bing,
            Sesame,
            Brave,
            GitHub,
        )

        /**
         * Resolve the search provider using its ID, or use Google as a fallback.
         */
        fun fromId(id: String): QsbSearchProvider =
            values().firstOrNull { it.id == id } ?: AppSearch

        /**
         * Resolve the default search provider.
         */
        fun resolveDefault(context: Context): QsbSearchProvider {
            val defaultProviderId =
                context.getString(R.string.config_default_qsb_search_provider_id)
            val defaultProvider = fromId(defaultProviderId)
            val isDefaultProviderIntentResolved =
                QsbLayout.resolveIntent(context, defaultProvider.createSearchIntent())

            // Return the default value from config.xml if the value is valid
            if (isDefaultProviderIntentResolved) {
                if (defaultProvider != AppSearch ||
                    (defaultProvider == AppSearch && defaultProviderId == AppSearch.id)
                ) {
                    return defaultProvider
                }
            }

            // Return the best default option if the config.xml value is invalid
            return values()
                .filterNot { it == AppSearch }
                .firstOrNull { QsbLayout.resolveIntent(context, it.createSearchIntent()) }
                ?: AppSearch

        }

    }
}

enum class QsbSearchProviderType(val downloadable: Boolean) {
    APP_AND_WEBSITE(downloadable = true),
    WEBSITE(downloadable = false),
    APP(downloadable = true),
    LOCAL(downloadable = false)
}
