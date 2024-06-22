package app.lawnchair.search.algorithms.data

import android.annotation.DrawableRes
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import app.lawnchair.util.androidPkgTypes
import app.lawnchair.util.archiveFileTypes
import app.lawnchair.util.audioFileTypes
import app.lawnchair.util.documentFileTypes
import app.lawnchair.util.exists
import app.lawnchair.util.imageFileTypes
import app.lawnchair.util.isDirectory
import app.lawnchair.util.isHidden
import app.lawnchair.util.isRegularFile
import app.lawnchair.util.mimeType2Extension
import app.lawnchair.util.videoFileTypes
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

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

suspend fun queryFilesInMediaStore(
    context: Context,
    uri: Uri = MediaStore.Files.getContentUri("external"),
    path: String = "",
    keyword: String,
    maxResult: Int,
    mimes: Array<String>? = null,
): Sequence<IFileInfo> = withContext(Dispatchers.IO) {
    val selection = "${commonProjection[0]} like ? AND ${commonProjection[0]} like ? ".let {
        if (mimes == null) it else it + "AND ${commonProjection[4]} IN (${mimes.selectionArgsPlaceHolder})"
    }
    val selectionArgs = arrayOf("%$path%", "%$keyword%").let {
        if (mimes == null) it else it + mimes
    }
    getFileListFromMediaStore(
        context,
        uri,
        commonProjection,
        selection,
        selectionArgs,
        maxResult = maxResult,
    ) { cursor ->
        val filePath = cursor.getString(cursor.getColumnIndexOrThrow(commonProjection[0])).toPath()
        if (filePath.isDirectory()) getFolderBean(cursor) else getFileBean(cursor)
    }
}

private val Array<String>.selectionArgsPlaceHolder: String get() = Array(size) { "?" }.joinToString()
private val commonProjection = arrayOf(
    MediaStore.MediaColumns.DATA,
    MediaStore.MediaColumns.DISPLAY_NAME,
    MediaStore.MediaColumns.SIZE,
    MediaStore.MediaColumns.DATE_MODIFIED,
    MediaStore.MediaColumns.MIME_TYPE,
    MediaStore.MediaColumns.TITLE,
    MediaStore.MediaColumns._ID,
)

private suspend inline fun <T : Any> getFileListFromMediaStore(
    context: Context,
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String = "${commonProjection[3]} DESC",
    maxResult: Int,
    crossinline body: (Cursor) -> T?,
): Sequence<T> = withContext(Dispatchers.IO) {
    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        ?.use { cursor ->
            var count = 0
            buildList {
                while (cursor.moveToNext()) {
                    val bean = body(cursor) ?: continue
                    add(bean)
                    count++
                    if (count >= maxResult) break
                }
            }.asSequence()
        } ?: emptySequence()
}

private fun getFileBean(cursor: Cursor): IFileInfo? = cursor.run {
    val mimeType = getString(getColumnIndexOrThrow(commonProjection[4]))
    val title = getString(getColumnIndexOrThrow(commonProjection[1]))
        ?: getString(getColumnIndexOrThrow(commonProjection[5]))?.let {
            if (mimeType == null) it else "$it.${mimeType.mimeType2Extension()}"
        } ?: return null
    val path = getString(getColumnIndexOrThrow(commonProjection[0])).toPath()
    val fileId = getString(getColumnIndexOrThrow(commonProjection[6]))
    if (!path.isRegularFile() || path.isHidden) return null
    val dateModified = getLong(getColumnIndexOrThrow(commonProjection[3])) * 1000
    return FileInfo(
        fileId,
        path.toString(),
        title,
        getLong(getColumnIndexOrThrow(commonProjection[2])),
        dateModified,
        mimeType,
    )
}

private fun getFolderBean(cursor: Cursor): FolderInfo? = cursor.run {
    val title = getString(getColumnIndexOrThrow(commonProjection[1]))
        ?: getString(getColumnIndexOrThrow(commonProjection[5])) ?: return null
    val path = getString(getColumnIndexOrThrow(commonProjection[0])).toPath()
    if (!path.exists || path.isHidden) return null
    val dateModified = getLong(getColumnIndexOrThrow(commonProjection[3])) * 1000
    return FolderInfo(
        path.toString(),
        title,
        getLong(getColumnIndexOrThrow(commonProjection[2])),
        dateModified,
    )
}
