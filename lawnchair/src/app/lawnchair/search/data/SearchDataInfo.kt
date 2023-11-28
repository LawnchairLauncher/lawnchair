package app.lawnchair.search.data

import app.lawnchair.search.data.MimeTypes.MIME_APP
import app.lawnchair.search.data.MimeTypes.MIME_AUDIO
import app.lawnchair.search.data.MimeTypes.MIME_CSV
import app.lawnchair.search.data.MimeTypes.MIME_EXCEL
import app.lawnchair.search.data.MimeTypes.MIME_IMAGES
import app.lawnchair.search.data.MimeTypes.MIME_PDF
import app.lawnchair.search.data.MimeTypes.MIME_PPT1
import app.lawnchair.search.data.MimeTypes.MIME_PPT2
import app.lawnchair.search.data.MimeTypes.MIME_SRT
import app.lawnchair.search.data.MimeTypes.MIME_TEXT
import app.lawnchair.search.data.MimeTypes.MIME_VIDEO
import app.lawnchair.search.data.MimeTypes.MIME_WORD
import app.lawnchair.search.data.MimeTypes.MIME_ZIP
import com.android.launcher3.R

object MimeTypes {
    const val MIME_APP = "application/vnd.android.package-archive"
    const val MIME_AUDIO = "audio/"
    const val MIME_CSV = "text/comma-separated-values"
    const val MIME_EXCEL = "application/vnd.ms-excel"
    const val MIME_IMAGES = "image/"
    const val MIME_PDF = "application/pdf"
    const val MIME_PPT1 = "application/vnd.ms-powerpoint"
    const val MIME_PPT2 = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val MIME_SRT = "application/x-subrip"
    const val MIME_TEXT = "text/"
    const val MIME_VIDEO = "video/"
    const val MIME_WORD = "application/msword"
    const val MIME_ZIP = "application/zip"
}

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

data class FileInfo(
    val fileId: Long,
    val name: String,
    val path: String,
    val mime: String,
    val type: Int,
    val selected: Boolean = false,
) {
    fun getIcon(): Int {
        return when {
            mime.contains(MIME_IMAGES) -> R.drawable.ic_file_image
            mime.contains(MIME_VIDEO) -> R.drawable.ic_file_video
            mime.contains(MIME_EXCEL) || mime.contains(MIME_CSV) -> R.drawable.ic_file_excel
            mime.contains(MIME_TEXT) -> R.drawable.ic_file_text
            mime.contains(MIME_AUDIO) -> R.drawable.ic_file_music
            mime.contains(MIME_APP) -> R.drawable.ic_file_app
            mime.contains(MIME_PDF) -> R.drawable.ic_file_pdf
            mime.contains(MIME_ZIP) -> R.drawable.ic_file_zip
            mime.contains(MIME_WORD) -> R.drawable.ic_file_word
            mime.contains(MIME_PPT1) || mime.contains(MIME_PPT2) -> R.drawable.ic_file_powerpoint
            mime.contains(MIME_SRT) -> R.drawable.ic_file_subtitle
            else -> R.drawable.ic_file_unknown
        }
    }

    fun isMediaFile(): Boolean {
        return mime.run {
            contains(MIME_IMAGES) || contains(MIME_VIDEO)
        }
    }
    fun isFileUnknown(): Boolean {
        return mime.run {
            !(
                contains(MIME_IMAGES) || contains(MIME_VIDEO) || contains(MIME_EXCEL) || contains(MIME_CSV) ||
                    contains(MIME_TEXT) || contains(MIME_AUDIO) || contains(MIME_APP) || contains(MIME_PDF) ||
                    contains(MIME_ZIP) || contains(MIME_WORD) || contains(MIME_PPT1) || contains(MIME_PPT2) ||
                    !contains(MIME_SRT)
                )
        }
    }
}

data class SearchResult(
    val resultType: String,
    val resultData: Any,
)
