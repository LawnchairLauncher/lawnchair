package app.lawnchair.search.adapter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.algorithms.data.Calculation
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.data.FileInfo
import app.lawnchair.search.algorithms.data.FileInfo.Companion.isImageType
import app.lawnchair.search.algorithms.data.FolderInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.data.RecentKeyword
import app.lawnchair.search.algorithms.data.SettingInfo
import app.lawnchair.search.algorithms.engine.provider.web.WebSearchProvider
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.util.createTextBitmap
import app.lawnchair.util.file2Uri
import app.lawnchair.util.mimeCompat
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import okio.ByteString

class SearchTargetFactory(
    private val context: Context,
) {
    fun createAppSearchTarget(appInfo: AppInfo, asRow: Boolean = false): SearchTargetCompat {
        val componentName = appInfo.componentName
        val user = appInfo.user
        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_APPLICATION,
            if (asRow) LayoutType.SMALL_ICON_HORIZONTAL_TEXT else LayoutType.ICON_SINGLE_VERTICAL_TEXT,
            generateHashKey(ComponentKey(componentName, user).toString()),
        ).apply {
            setPackageName(componentName?.packageName ?: "")
            setUserHandle(user)
            setExtras(bundleOf("class" to (componentName?.className ?: "")))
        }.build()
    }

    fun createShortcutTarget(shortcutInfo: ShortcutInfo): SearchTargetCompat {
        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_SHORTCUT,
            LayoutType.SMALL_ICON_HORIZONTAL_TEXT,
            "shortcut_" + generateHashKey("${shortcutInfo.`package`}|${shortcutInfo.userHandle}|${shortcutInfo.id}"),
        ).apply {
            setShortcutInfo(shortcutInfo)
            setUserHandle(shortcutInfo.userHandle)
            setExtras(Bundle())
        }.build()
    }

    fun createWebSuggestionsTarget(suggestion: String, suggestionProvider: String): SearchTargetCompat {
        val webSearchProvider = WebSearchProvider.fromString(suggestionProvider)
        val url = webSearchProvider.getSearchUrl(suggestion)
        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        val id = suggestion + url
        val action = SearchActionCompat.Builder(id, suggestion).apply {
            setIcon(
                Icon.createWithResource(context, R.drawable.ic_allapps_search)
                    .setTint(ColorTokens.TextColorSecondary.resolveColor(context)),
            )
            setIntent(browserIntent)
        }.build()
        return createSearchTarget(
            id,
            action,
            LayoutType.HORIZONTAL_MEDIUM_TEXT,
            SearchTargetCompat.RESULT_TYPE_SUGGESTIONS,
            WEB_SUGGESTION,
        )
    }

    internal fun createCalculatorTarget(
        calculation: Calculation,
    ): SearchTargetCompat {
        val result = calculation.result
        val equation = calculation.equation
        val uuid = UUID.randomUUID().toString()
        val id = "calculator:$uuid"
        val action = SearchActionCompat.Builder(id, result)
            .setIcon(
                Icon.createWithResource(context, R.drawable.calculator)
                    .setTint(ColorTokens.TextColorSecondary.resolveColor(context)),
            )
            .setSubtitle(equation)
            .setIntent(Intent())
            .build()

        val extras = bundleOf(
            "result" to result,
        )

        return createSearchTarget(
            id,
            action,
            LayoutType.CALCULATOR,
            SearchTargetCompat.RESULT_TYPE_CALCULATOR,
            CALCULATOR,
            extras,
        )
    }

    fun createHeaderTarget(header: String, pkg: String = HEADER): SearchTargetCompat {
        val id = "header_$header"
        val action = SearchActionCompat.Builder(id, header)
            .setIcon(
                Icon.createWithResource(context, R.drawable.ic_allapps_search)
                    .setTint(ColorTokens.TextColorPrimary.resolveColor(context)),
            )
            .setIntent(Intent())
            .build()
        return createSearchTarget(
            id,
            action,
            LayoutType.TEXT_HEADER,
            SearchTargetCompat.RESULT_TYPE_SECTION_HEADER,
            pkg,
        )
    }

    fun createSearchHistoryTarget(recentKeyword: RecentKeyword, searchUrl: (String) -> String): SearchTargetCompat {
        val value = recentKeyword.getValueByKey("display1") ?: ""
        val browserIntent = Intent(Intent.ACTION_VIEW, searchUrl(value).toUri())
        val id = recentKeyword.data.toString() + searchUrl(value)
        val action = SearchActionCompat.Builder(id, value)
            .setIcon(
                Icon.createWithResource(context, R.drawable.ic_recent)
                    .setTint(ColorTokens.TextColorSecondary.resolveColor(context)),
            )
            .setIntent(browserIntent)
            .build()
        return createSearchTarget(
            id,
            action,
            LayoutType.WIDGET_LIVE,
            SearchTargetCompat.RESULT_TYPE_SUGGESTIONS,
            HISTORY,
        )
    }

    fun createSettingsTarget(info: SettingInfo): SearchTargetCompat? {
        val id = "_${info.id}"

        val intent = Intent(info.action)
        if (info.requiresUri) {
            intent.data = Uri.fromParts("package", context.packageName, null)
        }

        if (intent.resolveActivity(context.packageManager) == null) {
            Log.w("SettingSearch", "No activity found to handle intent: $intent")
            return null
        }

        if (!SettingsTarget.hasRequiredPermissions(context, intent)) {
            Log.w("SettingSearch", "App does not have required permissions for intent: $intent")
            return null
        }

        val actionBuilder = SearchActionCompat.Builder(id, SettingsTarget.formatSettingTitle(info.name))
            .setIcon(
                Icon.createWithResource(context, R.drawable.ic_setting)
                    .setTint(ColorTokens.Accent1_600.resolveColor(context)),
            )
            .setIntent(intent)
            .build()

        return createSearchTarget(
            id,
            actionBuilder,
            LayoutType.ICON_SLICE,
            SearchTargetCompat.RESULT_TYPE_SETTING_TILE,
            SETTINGS,
        )
    }

    private fun createSearchLinksTarget(
        id: String,
        action: SearchActionCompat,
        pkg: String,
        extras: Bundle = Bundle(),
    ): SearchTargetCompat {
        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_REDIRECTION,
            LayoutType.ICON_HORIZONTAL_TEXT,
            generateHashKey(id),
        )
            .setPackageName(pkg)
            .setUserHandle(Process.myUserHandle())
            .setSearchAction(action)
            .setExtras(extras)
            .build()
    }

    internal fun createMarketSearchTarget(query: String): SearchTargetCompat? {
        val marketSearchComponent = SearchLinksTarget.resolveMarketSearchActivity(context) ?: return null
        val id = "marketSearch:$query"
        val action = SearchActionCompat.Builder(
            id,
            context.getString(R.string.all_apps_search_market_message),
        )
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_home))
            .setIntent(PackageManagerHelper.getMarketSearchIntent(context, query))
            .build()
        val extras = bundleOf(
            SearchResultView.EXTRA_ICON_COMPONENT_KEY to marketSearchComponent.toString(),
            SearchResultView.EXTRA_HIDE_SUBTITLE to true,
        )
        return createSearchLinksTarget(
            id,
            action,
            MARKET_STORE,
            extras,
        )
    }

    fun createWebSearchActionTarget(
        query: String,
        providerName: String,
        searchUrl: String,
        @DrawableRes providerIconRes: Int,
        tintIcon: Boolean = false,
    ): SearchTargetCompat {
        val id = "browser:$query:$providerName"
        val browserIntent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
        val title = context.getString(R.string.all_apps_search_on_web_message, providerName)

        val icon = Icon.createWithResource(context, providerIconRes).apply {
            if (tintIcon) {
                setTint(ColorTokens.TextColorSecondary.resolveColor(context))
            }
        }

        val action = SearchActionCompat.Builder(id, title)
            .setIcon(icon)
            .setIntent(browserIntent)
            .build()

        val extras = bundleOf(SearchResultView.EXTRA_HIDE_SUBTITLE to true)

        // Assuming START_PAGE is a generic package key for this type
        return createSearchLinksTarget(id, action, START_PAGE, extras)
    }

    fun createContactsTarget(info: ContactInfo): SearchTargetCompat {
        val id = "contact:${info.contactId}${info.number}"
        val contactUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI,
            info.contactId,
        )

        val contactIntent = Intent(Intent.ACTION_VIEW, contactUri)
        val action = SearchActionCompat.Builder(id, info.name)
            .setIcon(ContactsTarget.displayContactPhoto(context, info.name, info.uri.toUri()))
            .setContentDescription(info.contactId)
            .setSubtitle(info.number)
            .setIntent(contactIntent)
            .build()

        return createSearchTarget(
            id,
            action,
            LayoutType.PEOPLE_TILE,
            SearchTargetCompat.RESULT_TYPE_CONTACT_TILE,
            CONTACT,
        )
    }

    fun createFilesTarget(info: IFileInfo): SearchTargetCompat {
        val fileUri = when (info) {
            is FileInfo -> Uri.withAppendedPath(
                MediaStore.Files.getContentUri("external"),
                info.fileId,
            )

            is FolderInfo -> File(info.path).file2Uri()
        }

        val mimeType = when (info) {
            is FileInfo -> info.mimeType.mimeCompat
            is FolderInfo -> "resource/folder"
        }

        val fileIntent = Intent(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .setDataAndType(fileUri, mimeType)

        val action = SearchActionCompat.Builder(info.path, info.name)
            .setIcon(FilesTarget.getPreviewIcon(context, info))
            .setIntent(fileIntent)
            .build()

        return createSearchTarget(
            info.path,
            action,
            LayoutType.THUMBNAIL,
            SearchTargetCompat.RESULT_TYPE_FILE_TILE,
            FILES,
        )
    }

    fun createEmptyStateTarget(
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
    ): SearchTargetCompat {
        val id = "empty_state:$titleRes"
        // The action doesn't do anything, it's just for display.
        val action = SearchActionCompat.Builder(id, "")
            .setIntent(Intent())
            .build()

        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_EMPTY_RESULT,
            LayoutType.EMPTY_STATE,
            id,
        ).apply {
            setPackageName(EMPTY_STATE)
            setUserHandle(Process.myUserHandle())
            setSearchAction(action)
            setExtras(
                bundleOf(
                    "titleRes" to titleRes,
                    "subtitleRes" to subtitleRes,
                ),
            )
        }.build()
    }

    fun createSearchSettingsTarget(): SearchTargetCompat {
        val id = "action:search_settings"

        // The action doesn't do anything, it's just for display.
        val action = SearchActionCompat.Builder(id, "")
            .setIntent(Intent())
            .build()

        return SearchTargetCompat.Builder(
            SearchTargetCompat.RESULT_TYPE_SEARCH_SETTINGS,
            LayoutType.SEARCH_SETTINGS,
            id,
        ).apply {
            setPackageName(SEARCH_SETTINGS)
            setUserHandle(Process.myUserHandle())
            setSearchAction(action)
            setExtras(Bundle())
        }.build()
    }

    companion object {
        private const val HASH_ALGORITHM = "SHA-256"

        // TODO find a way to properly provide tag/provide ids to search target
        private val messageDigest by lazy { MessageDigest.getInstance(HASH_ALGORITHM) }

        private fun generateHashKey(input: String): String = ByteString.of(*messageDigest.digest(input.toByteArray())).hex()

        fun createSearchTarget(
            id: String,
            action: SearchActionCompat,
            layoutType: String,
            targetCompat: Int,
            pkg: String,
            extras: Bundle = Bundle(),
        ): SearchTargetCompat {
            return SearchTargetCompat.Builder(
                targetCompat,
                layoutType,
                generateHashKey(id),
            ).apply {
                setPackageName(pkg)
                setUserHandle(Process.myUserHandle())
                setSearchAction(action)
                setExtras(extras)
            }.build()
        }
    }
}

object FilesTarget {
    fun getPreviewIcon(
        context: Context,
        info: IFileInfo,
    ): Icon {
        val fileInfo = info as? FileInfo
        return if (fileInfo?.isImageType == true) {
            Icon.createWithFilePath(fileInfo.path)
        } else {
            Icon.createWithResource(context, fileInfo?.iconRes ?: R.drawable.ic_folder)
        }
    }
}

object ContactsTarget {
    fun displayContactPhoto(context: Context, name: String, contactUri: Uri): Icon {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(contactUri)
            inputStream?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                return Icon.createWithAdaptiveBitmap(bitmap)
            }
        } catch (e: IOException) {
            // ignore
        } finally {
            inputStream?.close()
        }

        // If contact photo is not available, create an icon with the first letter of the contact's name
        val initial = if (name.isNotEmpty()) name[0].uppercaseChar().toString() else "U"
        val textBitmap = createTextBitmap(context, initial)
        return Icon.createWithBitmap(textBitmap)
    }
}

object SearchLinksTarget {
    fun resolveMarketSearchActivity(context: Context): ComponentKey? {
        val intent = PackageManagerHelper.getMarketSearchIntent(context, "")
        val resolveInfo = context.packageManager.resolveActivity(intent, 0) ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return ComponentKey(launchIntent.component, Process.myUserHandle())
    }
}

object SettingsTarget {
    fun hasRequiredPermissions(context: Context, intent: Intent): Boolean {
        val packageManager = context.packageManager
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.exported == true
    }

    fun formatSettingTitle(rawTitle: String?): String {
        return rawTitle?.replace('_', ' ')
            ?.replace("ACTION", "")
            ?.lowercase()
            ?.split(' ')
            ?.joinToString(" ") {
                it.replaceFirstChar { char -> char.uppercase(Locale.ROOT) }
            }.orEmpty()
    }
}

// keys used in `pkg` param
const val START_PAGE = "startpage"
const val MARKET_STORE = "marketstore"
const val WEB_SUGGESTION = "suggestion"
const val HEADER = "header"
const val CONTACT = "contact"
const val FILES = "files"
const val SPACE = "space"
const val SPACE_MINI = "space_mini"
const val LOADING = "loading"
const val ERROR = "error"
const val SETTINGS = "setting"
const val SHORTCUT = "shortcut"
const val HISTORY = "recent_keyword"
const val CALCULATOR = "calculator"
const val EMPTY_STATE = "empty_state"
const val SEARCH_SETTINGS = "search_settings"
