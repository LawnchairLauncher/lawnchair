package app.lawnchair.search.data

import android.annotation.DrawableRes
import app.lawnchair.util.androidPkgTypes
import app.lawnchair.util.archiveFileTypes
import app.lawnchair.util.audioFileTypes
import app.lawnchair.util.documentFileTypes
import app.lawnchair.util.imageFileTypes
import app.lawnchair.util.videoFileTypes
import com.android.launcher3.R

data class ContactInfo(
    val contactId: String,
    val name: String,
    var number: String,
    val phoneBookLabel: String,
    val uri: String,
    var packages: String,
)

const val CONTACT_ACCOUNT_ID = "contact.id"
const val CONTACT_ACCOUNT_MIME = "contact.mime"
const val CONTACT_ACCOUNT_NAME = "contact.account.name"
const val CONTACT_ACCOUNT_TITLE = "contact.title"
const val CONTACT_ACCOUNT_TYPE = "contact.account.type"

val EXCLUDED_MIME_TYPES = arrayOf(
    "vnd.android.cursor.item/name",
    "vnd.android.cursor.item/nickname",
    "vnd.android.cursor.item/note",
    "vnd.android.cursor.item/photo",
    "vnd.com.google.cursor.item/contact_misc",
    "vnd.android.cursor.item/identity",
    "vnd.android.cursor.item/website",
)

data class SettingInfo(
    val id: String,
    val name: String,
    val action: String,
    val requiresUri: Boolean = false,
)

data class RecentKeyword(
    val data: Map<String, String>,
) {
    fun getValueByKey(key: String): String? {
        return data[key]
    }
}

sealed interface IFileInfo {
    val path: String
    val name: String
    val size: Long
    val dateModified: Long
}

data class FolderInfo(
    override val path: String,
    override val name: String,
    override val size: Long,
    override val dateModified: Long,
) : IFileInfo

data class FileInfo(
    val fileId: String,
    override val path: String,
    override val name: String,
    override val size: Long,
    override val dateModified: Long,
    val mimeType: String?,
) : IFileInfo {
    @get:DrawableRes
    val iconRes = when (val mime = mimeType.orEmpty()) {
        in imageFileTypes.values -> R.drawable.ic_file_image
        in videoFileTypes.values -> R.drawable.ic_file_video
        in audioFileTypes.values -> R.drawable.ic_file_music
        in androidPkgTypes.values -> R.drawable.ic_file_app
        in archiveFileTypes.values -> R.drawable.ic_file_zip
        in documentFileTypes.values -> when {
            mime.contains("excel") || mime.contains("csv") -> R.drawable.ic_file_excel
            mime.contains("word") -> R.drawable.ic_file_word
            mime.contains("powerpoint") -> R.drawable.ic_file_powerpoint
            mime.contains("pdf") -> R.drawable.ic_file_pdf
            mime.contains("srt") -> R.drawable.ic_file_subtitle
            else -> R.drawable.ic_file_text
        }
        else -> R.drawable.ic_file_unknown
    }

    companion object {
        val FileInfo.isMediaType: Boolean get() {
            return mimeType in videoFileTypes.values ||
                mimeType in audioFileTypes.values
        }

        val FileInfo.isImageType: Boolean get() = mimeType in imageFileTypes.values

        val FileInfo.isUnknownType: Boolean get() {
            return mimeType !in imageFileTypes.values &&
                mimeType !in videoFileTypes.values &&
                mimeType !in audioFileTypes.values &&
                mimeType !in androidPkgTypes.values &&
                mimeType !in archiveFileTypes.values &&
                mimeType !in documentFileTypes.values
        }
    }
}

data class SearchResult(
    val resultType: String,
    val resultData: Any,
)
