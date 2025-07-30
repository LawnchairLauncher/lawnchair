package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.data.FileInfo
import app.lawnchair.search.algorithms.data.FolderInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import app.lawnchair.util.audioFileTypes
import app.lawnchair.util.exists
import app.lawnchair.util.imageFileTypes
import app.lawnchair.util.isDirectory
import app.lawnchair.util.isHidden
import app.lawnchair.util.isRegularFile
import app.lawnchair.util.mimeType2Extension
import app.lawnchair.util.videoFileTypes
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

        val searchAllFiles = prefs.searchResultAllFiles.get()
        val searchAudio = prefs.searchResultAudio.get()
        val searchVisualMedia = prefs.searchResultVisualMedia.get()

        val fileSearchEnabled = prefs.searchResultFilesToggle.get()
        val anyProviderEnabled = searchAllFiles || searchAudio || searchVisualMedia
        if (query.isBlank() || !fileSearchEnabled || !anyProviderEnabled) {
            // do nothing if query is empty, file search is disabled, or none of the providers are enabled
            emit(emptyList())
            return@flow
        }

        val prefs2 = PreferenceManager2.getInstance(context)
        val maxResults = prefs2.maxFileResultCount.firstBlocking()

        // check for permissions:
        val fileAccessManager = FileAccessManager.getInstance(context)
        val allFilesGranted = fileAccessManager.allFilesAccessState.value == FileAccessState.Full
        val audioGranted = fileAccessManager.audioAccessState.value == FileAccessState.Full
        val visualMediaGranted = fileAccessManager.visualMediaAccessState.value != FileAccessState.Denied

        val results = coroutineScope {
            val allFilesDeferred = if (allFilesGranted && searchAllFiles) {
                async { searchAllFiles(context, query, maxResults).first() }
            } else {
                null
            }

            // Only launch visual media and audio if allFiles search isn't happening
            val visualMediaDeferred = if (allFilesDeferred == null && visualMediaGranted && searchVisualMedia) {
                async { searchVisualMedia(context, query, maxResults).first() }
            } else {
                null
            }

            val audioDeferred = if (allFilesDeferred == null && audioGranted && searchAudio) {
                async { searchAudio(context, query, maxResults).first() }
            } else {
                null
            }

            // Await and combine
            val allFilesResults = allFilesDeferred?.await() ?: emptyList()

            if (allFilesDeferred != null) {
                allFilesResults
            } else {
                (visualMediaDeferred?.await() ?: emptyList()) +
                    (audioDeferred?.await() ?: emptyList())
            }
        }

        val uniqueResults = results.distinctBy { it.path }.take(maxResults)
        emit(uniqueResults.map { SearchResult.File(it) })
    }

    private val IMAGE_MIME_TYPES = imageFileTypes.values.toTypedArray()
    private val VIDEO_MIME_TYPES = videoFileTypes.values.toTypedArray()
    private val AUDIO_MIME_TYPES = audioFileTypes.values.toTypedArray()

    /**
     * Searches for photos and videos based on visual media access.
     */
    private fun searchVisualMedia(
        context: Context,
        query: String,
        maxResults: Int,
    ): Flow<List<IFileInfo>> = flow {
        val results = mutableListOf<IFileInfo>()

        results.addAll(
            queryMediaStoreByType(
                context = context,
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                keyword = query,
                mimeTypes = IMAGE_MIME_TYPES,
                maxResult = maxResults,
            ).toList(),
        )

        results.addAll(
            queryMediaStoreByType(
                context = context,
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                keyword = query,
                mimeTypes = VIDEO_MIME_TYPES,
                maxResult = maxResults,
            ).toList(),
        )

        val uniqueResults = results.distinctBy { it.path }.take(maxResults)

        emit(uniqueResults)
    }

    /**
     * Searches for audio files based on audio access.
     */
    private fun searchAudio(
        context: Context,
        query: String,
        maxResults: Int,
    ): Flow<List<IFileInfo>> = flow {
        val audioInfoList = queryMediaStoreByType(
            context = context,
            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            keyword = query,
            mimeTypes = AUDIO_MIME_TYPES,
            maxResult = maxResults,
        ).toList()

        emit(audioInfoList.take(maxResults))
    }

    /**
     * Searches for all file types that MediaStore can index,
     * relying on "All Files Access" (MANAGE_EXTERNAL_STORAGE or legacy READ_EXTERNAL_STORAGE).
     * This will also find folders.
     */
    private fun searchAllFiles(
        context: Context,
        query: String,
        maxResults: Int,
    ): Flow<List<IFileInfo>> = flow {
        val fileInfoList = queryGeneralFilesInMediaStore(
            context = context,
            keyword = query,
            maxResult = maxResults,
        ).toList()

        emit(fileInfoList)
    }

    private val commonProjection = arrayOf(
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.TITLE,
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.DOCUMENT_ID,
    )

    /**
     * Queries the MediaStore for specific media types (Images, Video, Audio) based on a keyword.
     * This function is optimized for finding files and does not return folders.
     *
     * @param context The application context.
     * @param uri The MediaStore URI to query (e.g., `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`).
     * @param keyword The search term to match against file display names or titles.
     * @param maxResult The maximum number of results to return.
     * @param mimeTypes An optional array of MIME types to filter the results.
     *                  If null or empty, no MIME type filtering is applied.
     * @return A [Sequence] of [FileInfo] objects matching the query criteria.
     *         The sequence is processed lazily.
     */
    private suspend fun queryMediaStoreByType(
        context: Context,
        uri: Uri,
        keyword: String,
        maxResult: Int,
        mimeTypes: Array<String>? = null,
    ): Sequence<FileInfo> = withContext(Dispatchers.IO) {
        val selectionClauses = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        selectionClauses.add("(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.TITLE} LIKE ?)")
        selectionArgsList.add("%$keyword%")
        selectionArgsList.add("%$keyword%")

        if (!mimeTypes.isNullOrEmpty()) {
            selectionClauses.add("${MediaStore.MediaColumns.MIME_TYPE} IN (${mimeTypes.joinToString { "?" }})")
            selectionArgsList.addAll(mimeTypes)
        }

        val selection = selectionClauses.joinToString(separator = " AND ")
        val selectionArgs = selectionArgsList.toTypedArray()

        contentResolverQuery(
            context = context,
            uri = uri,
            projection = commonProjection,
            selection = selection,
            selectionArgs = selectionArgs,
            maxResult = maxResult,
        ) { cursor ->
            createFileInfoFromCursor(cursor)
        }
    }

    /**
     * Queries MediaStore.Files.getContentUri("external") which can return any indexed file or folder.
     */
    private suspend fun queryGeneralFilesInMediaStore(
        context: Context,
        keyword: String,
        maxResult: Int,
        path: String = "",
        mimeTypes: Array<String>? = null,
    ): Sequence<IFileInfo> = withContext(Dispatchers.IO) {
        val selectionClauses = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        // Path filtering (if provided)
        if (path.isNotBlank()) {
            selectionClauses.add("${MediaStore.MediaColumns.DATA} LIKE ?")
            selectionArgsList.add("$path%")
        }

        selectionClauses.add("(${MediaStore.MediaColumns.DATA} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)")
        selectionArgsList.add("%$keyword%")
        selectionArgsList.add("%$keyword%")

        if (!mimeTypes.isNullOrEmpty()) {
            selectionClauses.add("${MediaStore.MediaColumns.MIME_TYPE} IN (${mimeTypes.joinToString { "?" }})")
            selectionArgsList.addAll(mimeTypes)
        }

        val selection = selectionClauses.joinToString(separator = " AND ")
        val selectionArgs = selectionArgsList.toTypedArray()

        contentResolverQuery(
            context,
            MediaStore.Files.getContentUri("external"),
            commonProjection,
            selection,
            selectionArgs,
            maxResult = maxResult,
        ) { cursor ->
            // Determine if it's a file or folder.
            // A simple check could be if MIME_TYPE is null for folders, or check file system.
            // However, it's more robust to check the file system attributes if possible,
            // or rely on MediaStore.Files.FileColumns.MEDIA_TYPE (equals MEDIA_TYPE_NONE for folders)
            // For now, let's use a filesystem check on the path.
            val filePath = cursor.getString(cursor.getColumnIndexOrThrow(commonProjection[0])).toPath()
            if (filePath.isDirectory() && !filePath.isRegularFile()) {
                createFolderInfoFromCursor(cursor)
            } else {
                createFileInfoFromCursor(cursor)
            }
        }
    }

    /**
     * Core ContentResolver query logic.
     */
    private suspend inline fun <T : IFileInfo> contentResolverQuery(
        context: Context,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        maxResult: Int,
        crossinline factory: (Cursor) -> T?,
    ): Sequence<T> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    var count = 0
                    buildList {
                        while (cursor.moveToNext() && count < maxResult) {
                            factory(cursor)?.let { item ->
                                add(item)
                                count++
                            }
                        }
                    }.asSequence()
                } ?: emptySequence()
        } catch (e: Exception) {
            // Log error (e.g., SecurityException if permissions are somehow still an issue)
            Log.e("FileSearchProvider", "Error querying MediaStore at $uri: ${e.message}")
            emptySequence()
        }
    }

    private fun createFileInfoFromCursor(cursor: Cursor): FileInfo? {
        val pathString = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: return null
        val path = pathString.toPath()

        if (!path.exists || !path.isRegularFile() || path.isHidden) return null

        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            ?: path.name
        if (name.isBlank()) return null

        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
        val fileId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))

        val finalName = if (mimeType != null && !name.contains(".") && name.isNotEmpty()) {
            mimeType.mimeType2Extension()?.let { "$name.$it" } ?: name
        } else {
            name
        }

        return FileInfo(fileId, pathString, finalName, size, dateModified, mimeType)
    }

    private fun createFolderInfoFromCursor(cursor: Cursor): FolderInfo? {
        val pathString = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: return null
        val path = pathString.toPath()

        if (!path.exists || !path.isDirectory() || path.isHidden) return null

        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            ?: path.name
        if (name.isBlank()) return null

        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)) // Often 0 for folders
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000

        return FolderInfo(pathString, name, size, dateModified)
    }
}
