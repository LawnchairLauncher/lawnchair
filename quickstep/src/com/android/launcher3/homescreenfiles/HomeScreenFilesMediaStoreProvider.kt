/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.homescreenfiles

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.database.getStringOrNull
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.FileChange
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** MediaStore-based implementation of [HomeScreenFilesProvider]. */
class HomeScreenFilesMediaStoreProvider(
    private val context: Context,
    private val executorService: ExecutorService,
    lifecycle: DaggerSingletonTracker,
) : HomeScreenFilesProvider {
    override val fileChanges = MutableListenableStream<FileChange>()

    init {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                    if (!selfChange && uri != null && uri.hasIdSegment()) {
                        fileChanges.dispatchValue(
                            FileChange(uri, flags, query(uri), Process.myUserHandle())
                        )
                    }
                }
            }
        context.contentResolver.registerContentObserver(uri, true, observer)

        lifecycle.addCloseable { context.contentResolver.unregisterContentObserver(observer) }
    }

    override fun canMoveToHomeScreen(uriList: List<Uri>?): Boolean =
        uriList?.run { isNotEmpty() && all { uri -> canMoveToHomeScreen(uri) } } == true

    /** NOTE: Currently only URIs which can be resolved by the media store are supported. */
    private fun canMoveToHomeScreen(uri: Uri): Boolean =
        isExternalStorageProviderUri(uri) || isMediaStoreUri(uri)

    override fun moveToHomeScreen(uriList: List<Uri>): List<CompletableFuture<Boolean>> =
        uriList.map { uri: Uri -> supplyAsync({ moveToHomeScreen(uri) }, executorService) }

    @WorkerThread
    private fun moveToHomeScreen(uri: Uri): Boolean {
        try {
            // NOTE: The selection criteria below prevents moving a URI to a path it already
            // occupies. The media provider has additional protections to prevent recursive moves.
            return context.contentResolver.update(
                if (isMediaStoreUri(uri)) uri else MediaStore.getMediaUri(context, uri)!!,
                ContentValues().apply { put(RELATIVE_PATH, HOME_SCREEN_FOLDER_RELATIVE_PATH) },
                /*where=*/ "$RELATIVE_PATH != ?",
                /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
            ) == 1
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to move URI to '$HOME_SCREEN_FOLDER_RELATIVE_PATH'", e)
            return false
        }
    }

    /** Returns all file items presented in [HOME_SCREEN_FOLDER_RELATIVE_PATH]. */
    override fun query(): Lazy<Map<Uri, HomeScreenFile>> {
        val query: Callable<Map<Uri, HomeScreenFile>> = Callable {
            val result = mutableMapOf<Uri, HomeScreenFile>()
            context.contentResolver
                .query(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    arrayOf(MediaStore.Files.FileColumns._ID).plus(QUERY_DEFAULT_PROJECTION),
                    QUERY_DEFAULT_SELECTION,
                    QUERY_DEFAULT_SELECTION_ARGS,
                    null,
                    null,
                )
                ?.use {
                    val idColumnIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val displayNameColumnIndex =
                        it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeTypeColumnIndex =
                        it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val dataColumnIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumnIndex)
                        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
                        val displayName = it.getString(displayNameColumnIndex)
                        val mimeType = it.getStringOrNull(mimeTypeColumnIndex)
                        val isDirectory = File(it.getString(dataColumnIndex)).isDirectory
                        result[uri] = HomeScreenFile(displayName, mimeType, isDirectory)
                    }
                }
            return@Callable result
        }
        val future = executorService.submit(query)
        return lazy { future.get() }
    }

    /** Queries a single file from MediaStore by its URI. */
    private fun query(uri: Uri): Future<HomeScreenFile?> {
        if (!isMediaStoreUri(uri)) {
            return CompletableFuture.completedFuture(null)
        }
        val query: Callable<HomeScreenFile?> = Callable {
            context.contentResolver
                .query(
                    uri,
                    QUERY_DEFAULT_PROJECTION,
                    QUERY_DEFAULT_SELECTION,
                    QUERY_DEFAULT_SELECTION_ARGS,
                    null,
                    null,
                )
                ?.use {
                    if (it.count == 1) {
                        it.moveToFirst()
                        val displayNameColumnIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val mimeTypeColumnIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                        val dataColumnIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                        HomeScreenFile(
                            it.getString(displayNameColumnIndex),
                            it.getStringOrNull(mimeTypeColumnIndex),
                            File(it.getString(dataColumnIndex)).isDirectory,
                        )
                    } else {
                        null
                    }
                }
        }
        return executorService.submit(query)
    }

    companion object {
        private val QUERY_DEFAULT_PROJECTION =
            arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA,
            )
        private const val QUERY_DEFAULT_SELECTION =
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        private val QUERY_DEFAULT_SELECTION_ARGS = arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH)
        private const val TAG = "HomeScreenFilesMediaStoreProvider"

        private fun isExternalStorageProviderUri(uri: Uri?) =
            uri?.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY

        private fun isMediaStoreUri(uri: Uri) =
            uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY

        private fun Uri.hasIdSegment(): Boolean =
            kotlin.runCatching { ContentUris.parseId(this) != -1L }.getOrDefault(false)
    }
}
