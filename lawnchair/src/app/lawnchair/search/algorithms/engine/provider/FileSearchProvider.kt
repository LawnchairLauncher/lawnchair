package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.data.FileInfo
import app.lawnchair.search.algorithms.data.FolderInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.util.exists
import app.lawnchair.util.isDirectory
import app.lawnchair.util.isHidden
import app.lawnchair.util.isRegularFile
import app.lawnchair.util.mimeType2Extension
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

object FileSearchProvider : SearchProvider {
    override val id = "Files"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        val permissionsGranted = true // TODO: Replace with real permission check, to be added later
        if (query.isBlank() || !prefs.searchResultFiles.get() || !permissionsGranted) {
            emit(emptyList())
            return@flow
        }

        val maxResults = prefs2.maxFileResultCount.firstBlocking()

        val fileInfoList = queryFilesInMediaStore(
            context = context,
            keyword = query,
            maxResult = maxResults,
        ).toList()

        val searchResults = fileInfoList.map { fileInfo ->
            SearchResult.File(data = fileInfo)
        }

        emit(searchResults)
    }

    private suspend fun queryFilesInMediaStore(
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
            if (filePath.isDirectory()) createFolderInfoFromCursor(cursor) else createFileInfoFromCursor(cursor)
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

    private fun createFileInfoFromCursor(cursor: Cursor): IFileInfo? = cursor.run {
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

    private fun createFolderInfoFromCursor(cursor: Cursor): FolderInfo? = cursor.run {
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
}
