package app.lawnchair.search

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.os.bundleOf
import app.lawnchair.allapps.SearchResultView
import app.lawnchair.search.data.ContactInfo
import app.lawnchair.search.data.FileInfo
import app.lawnchair.search.data.FileInfo.Companion.isMediaType
import app.lawnchair.search.data.FileInfo.Companion.isUnknownType
import app.lawnchair.search.data.FolderInfo
import app.lawnchair.search.data.IFileInfo
import app.lawnchair.theme.color.ColorTokens
import app.lawnchair.util.createTextBitmap
import app.lawnchair.util.file2Uri
import app.lawnchair.util.mimeCompat
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.PackageManagerHelper
import java.io.File
import java.io.IOException
import java.io.InputStream

class GenerateSearchTarget(private val context: Context) {

    private val marketSearchComponent = resolveMarketSearchActivity()

    internal fun getSuggestionTarget(suggestion: String): SearchTargetCompat {
        val url = getStartPageUrl(suggestion)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val id = suggestion + url
        val action = SearchActionCompat.Builder(id, suggestion)
            .setIcon(
                Icon.createWithResource(context, R.drawable.ic_allapps_search)
                    .setTint(ColorTokens.TextColorPrimary.resolveColor(context)),
            )
            .setIntent(browserIntent)
            .build()
        return createSearchTarget(
            id,
            action,
            LayoutType.HORIZONTAL_MEDIUM_TEXT,
            SearchTargetCompat.RESULT_TYPE_SUGGESTIONS,
            SUGGESTION,
        )
    }

    internal fun getHeaderTarget(header: String): SearchTargetCompat {
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
            HEADER,
        )
    }

    internal fun getMarketSearchItem(query: String): SearchTargetCompat? {
        if (marketSearchComponent == null) return null
        val id = "marketSearch:$query"
        val action = SearchActionCompat.Builder(id, context.getString(R.string.all_apps_search_market_message))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_home))
            .setIntent(PackageManagerHelper.getMarketSearchIntent(context, query))
            .build()
        val extras = bundleOf(
            SearchResultView.EXTRA_ICON_COMPONENT_KEY to marketSearchComponent.toString(),
            SearchResultView.EXTRA_HIDE_SUBTITLE to true,
        )
        return createSearchTarget(id, action, MARKET_STORE, extras)
    }

    internal fun getStartPageSearchItem(query: String): SearchTargetCompat {
        val url = getStartPageUrl(query)
        val id = "browser:$query"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val action = SearchActionCompat.Builder(id, context.getString(R.string.all_apps_search_startpage_message))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_startpage))
            .setIntent(browserIntent)
            .build()
        val extras = bundleOf(
            SearchResultView.EXTRA_HIDE_SUBTITLE to true,
        )
        return createSearchTarget(id, action, START_PAGE, extras)
    }

    internal fun getContactSearchItem(info: ContactInfo): SearchTargetCompat {
        val id = "contact:${info.contactId}${info.number}"
        val contactUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI,
            info.contactId,
        )

        val contactIntent = Intent(Intent.ACTION_VIEW, contactUri)
        val action = SearchActionCompat.Builder(id, info.name)
            .setIcon(displayContactPhoto(context, info.name, Uri.parse(info.uri)))
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
            Bundle(),
        )
    }

    internal fun getFileInfoSearchItem(info: IFileInfo): SearchTargetCompat {
        val fileUri = when (info) {
            is FileInfo -> Uri.withAppendedPath(
                MediaStore.Files.getContentUri("external"),
                info.fileId,
            )
            is FolderInfo -> File(info.path).file2Uri() ?: Uri.fromFile(File(info.path))
        }

        val mimeType = when (info) {
            is FileInfo -> info.mimeType.mimeCompat
            is FolderInfo -> "*/*"
        }

        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setDataAndType(fileUri, mimeType)
        }

        val action = SearchActionCompat.Builder(info.path, info.name)
            .setIcon(getPreviewIcon(info))
            .setIntent(fileIntent)
            .build()

        return createSearchTarget(
            info.path,
            action,
            LayoutType.THUMBNAIL,
            SearchTargetCompat.RESULT_TYPE_FILE_TILE,
            FILES,
            Bundle(),
        )
    }

    private fun displayContactPhoto(context: Context, name: String, contactUri: Uri): Icon {
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

        // If contact photo not available, create an icon with the first letter of the name
        val initial = if (name.isNotEmpty()) name[0].uppercaseChar().toString() else "U"
        val textBitmap = createTextBitmap(context, initial)
        return Icon.createWithBitmap(textBitmap)
    }

    private fun getStartPageUrl(query: String): String {
        return "https://www.startpage.com/do/search?segment=startpage.lawnchair&query=$query&cat=web"
    }

    private fun resolveMarketSearchActivity(): ComponentKey? {
        val intent = PackageManagerHelper.getMarketSearchIntent(context, "")
        val resolveInfo = context.packageManager.resolveActivity(intent, 0) ?: return null
        val packageName = resolveInfo.activityInfo.packageName
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return ComponentKey(launchIntent.component, Process.myUserHandle())
    }

    private fun getPreviewIcon(info: IFileInfo): Icon {
        val fileInfo = info as? FileInfo
        return when {
            fileInfo?.isMediaType == true -> {
                BitmapFactory.decodeFile(fileInfo.path)?.let { Icon.createWithBitmap(it) }
                    ?: if (Utilities.ATLEAST_R) {
                        MediaMetadataRetriever().run {
                            setDataSource(fileInfo.path)
                            val videoBitmap = frameAtTime
                            release()
                            videoBitmap?.let { Icon.createWithBitmap(it) }
                        }
                    } else {
                        null
                    } ?: Icon.createWithResource(context, fileInfo.iconRes)
            }
            fileInfo?.isUnknownType == true -> Icon.createWithBitmap(createTextBitmap(context, "U"))
            else -> Icon.createWithResource(context, fileInfo?.iconRes ?: R.drawable.ic_folder)
        }
    }
}
