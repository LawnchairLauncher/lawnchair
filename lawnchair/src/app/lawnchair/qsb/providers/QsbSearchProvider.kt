package app.lawnchair.qsb.providers

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

open class QsbSearchProvider(
    val id: String,
    @StringRes val name: Int,
    @DrawableRes val icon: Int = R.drawable.ic_qsb_search,
    @DrawableRes val themedIcon: Int = icon,
    val themingMethod: ThemingMethod = ThemingMethod.TINT,
    open val packageName: String,
    open val action: String? = null,
    open val supportVoiceIntent: Boolean = false,
    open val website: String
) {

    fun createSearchIntent() = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setPackage(packageName)

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

    object None : QsbSearchProvider(id = "", name = -1, packageName = "", website = "")

    data class UnknownProvider(
        override val packageName: String,
        override val action: String? = null
    ) : QsbSearchProvider(
        id = "",
        name = -1,
        packageName = packageName,
        action = action,
        website = ""
    )

    companion object {

        internal const val INTENT_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        fun values() = listOf(
            AppSearch,
            Google,
            GoogleGo,
            DuckDuckGo
        )

        /**
         * Resolve the search provider using its ID, or use Google as a fallback.
         */
        fun fromId(id: String): QsbSearchProvider =
            values().firstOrNull { it.id == id } ?: Google

        fun resolve(packageName: String): QsbSearchProvider =
            values().firstOrNull { it.packageName == packageName } ?: UnknownProvider(packageName)
    }
}