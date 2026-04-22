package app.lawnchair.search.algorithms.data

import android.annotation.DrawableRes
import app.lawnchair.util.androidPkgTypes
import app.lawnchair.util.archiveFileTypes
import app.lawnchair.util.audioFileTypes
import app.lawnchair.util.documentFileTypes
import app.lawnchair.util.imageFileTypes
import app.lawnchair.util.videoFileTypes
import com.android.launcher3.R

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
