package app.lawnchair.search.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.data.suggestion.StartPageService
import app.lawnchair.util.exists
import app.lawnchair.util.isDirectory
import app.lawnchair.util.isHidden
import app.lawnchair.util.isRegularFile
import app.lawnchair.util.kotlinxJson
import app.lawnchair.util.mimeType2Extension
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Path.Companion.toPath
import org.json.JSONArray
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private val retrofit = Retrofit.Builder()
    .baseUrl("https://www.startpage.com/")
    .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
    .build()

val startPageService: StartPageService = retrofit.create()

suspend fun getStartPageSuggestions(query: String, max: Int): List<String> = withContext(Dispatchers.IO) {
    if (query.isEmpty() || query.isBlank() || max <= 0) {
        return@withContext emptyList()
    }

    try {
        val response: Response<ResponseBody> = startPageService.getStartPageSuggestions(
            query = query,
            segment = "startpage.lawnchair",
            partner = "lawnchair",
            format = "opensearch",
        )

        if (response.isSuccessful) {
            val responseBody = response.body()?.string()
            return@withContext JSONArray(responseBody).optJSONArray(1)?.let { array ->
                (0 until array.length()).take(max).map { array.getString(it) }
            } ?: emptyList()
        } else {
            Log.d("Failed to retrieve suggestions", ": ${response.code()}")
            return@withContext emptyList()
        }
    } catch (e: Exception) {
        Log.e("Exception", "Error during suggestion retrieval: ${e.message}")
        return@withContext emptyList()
    }
}

suspend fun getRecentKeyword(context: Context, query: String, max: Int, callback: SearchCallback) {
    try {
        if (query.isEmpty() || query.isBlank() || max <= 0) {
            callback.onSearchLoaded(emptyList())
            return
        }

        callback.onLoading()

        withContext(Dispatchers.IO) {
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = Uri.parse("content://${LawnchairRecentSuggestionProvider.AUTHORITY}/suggestions")
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            val recentKeywords = mutableListOf<RecentKeyword>()

            cursor?.use {
                val columnCount = it.columnCount

                while (it.moveToNext()) {
                    val recentKeywordData = mutableMapOf<String, String>()

                    for (i in 0 until columnCount) {
                        val columnName = it.getColumnName(i)
                        val columnValue = it.getString(i) ?: ""
                        recentKeywordData[columnName] = columnValue
                    }

                    recentKeywords.add(RecentKeyword(recentKeywordData))
                }
            }
            callback.onSearchLoaded(recentKeywords.asReversed().take(max))
        }
    } catch (e: Exception) {
        Log.e("Exception", "Error during recent keyword retrieval: ${e.message}")
        callback.onSearchFailed("Error during recent keyword retrieval: ${e.message}")
    }
}

suspend fun findSettingsByNameAndAction(query: String, max: Int): List<SettingInfo> = try {
    if (query.isBlank() || max <= 0) {
        emptyList()
    } else {
        withContext(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e("SettingSearch", "Something went wrong ", e)
            },
        ) {
            Settings::class.java.fields
                .asSequence()
                .filter { it.type == String::class.java && Modifier.isStatic(it.modifiers) && it.name.startsWith("ACTION_") }
                .map { it.name to it.get(null) as String }
                .filter { (name, action) ->
                    name.contains(query, ignoreCase = true) &&
                        !action.contains("REQUEST", ignoreCase = true) &&
                        !name.contains("REQUEST", ignoreCase = true) &&
                        !action.contains("PERMISSION", ignoreCase = true) &&
                        !name.contains("DETAIL", ignoreCase = true) &&
                        !name.contains("REMOTE", ignoreCase = true)
                }
                .map { (name, action) ->
                    val id = name + action
                    val requiresUri = action.contains("URI")
                    SettingInfo(id, name, action, requiresUri)
                }
                .toList().take(max)
        }
    }
} catch (e: Exception) {
    Log.e("SettingSearch", "Something went wrong ", e)
    emptyList()
}

suspend fun findContactsByName(context: Context, query: String, max: Int): List<ContactInfo> {
    try {
        if (query.isEmpty() || query.isBlank() || max <= 0) return emptyList()
        val exceptionHandler = CoroutineExceptionHandler { _, e ->
            Log.e("ContactSearch", "Something went wrong ", e)
        }
        return withContext(Dispatchers.IO + exceptionHandler) {
            val contactMap = HashMap<String, ContactInfo>()

            val projection = arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA5,
                "phonebook_label",
                "account_type",
                "account_name",
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.PHOTO_URI,
            )

            val selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use {
                while (it.moveToNext() && contactMap.size < max) {
                    val contactIdIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val displayNameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val data1Index = it.getColumnIndex(ContactsContract.Data.DATA1)
                    val data3Index = it.getColumnIndex(ContactsContract.Data.DATA3)
                    val data5Index = it.getColumnIndex(ContactsContract.Data.DATA5)
                    val phonebookLabelIndex = it.getColumnIndex("phonebook_label")
                    val accountTypeIndex = it.getColumnIndex("account_type")
                    val accountNameIndex = it.getColumnIndex("account_name")
                    val mimeTypeIndex = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val photoUriIndex = it.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                    val contactId = it.getString(contactIdIndex)
                    val displayName = it.getString(displayNameIndex)
                    val data1 = it.getString(data1Index)
                    val data3 = it.getString(data3Index)
                    val data5 = it.getString(data5Index)
                    val phonebookLabel = it.getString(phonebookLabelIndex)
                    val accountType = it.getString(accountTypeIndex)
                    val accountName = it.getString(accountNameIndex)
                    val mimeType = it.getString(mimeTypeIndex)
                    val photoUri = it.getString(photoUriIndex)
                    val phoneNumber = data3 ?: data5 ?: data1
                    val key = contactId ?: phoneNumber
                    val imageUri = photoUri ?: ""
                    val phonebookLabel2 = phonebookLabel ?: ""
                    val pkg = contactId + displayName + phoneNumber
                    if (key != null && !EXCLUDED_MIME_TYPES.contains(mimeType)) {
                        contactMap[key] = ContactInfo(
                            contactId,
                            displayName,
                            phoneNumber,
                            phonebookLabel2,
                            imageUri,
                            pkg,
                        )
                    } else {
                        if (contactMap.containsKey(contactId)) {
                            val existingContact = contactMap[contactId]
                            val jsonArray = buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put(CONTACT_ACCOUNT_ID, key)
                                        put(CONTACT_ACCOUNT_TITLE, data5)
                                        put(CONTACT_ACCOUNT_NAME, accountName)
                                        put(CONTACT_ACCOUNT_TYPE, accountType)
                                        put(CONTACT_ACCOUNT_MIME, mimeType)
                                    },
                                )
                            }
                            existingContact?.packages = jsonArray.toString()
                        }
                    }
                }
            }
            contactMap.values.toList()
        }
    } catch (e: Exception) {
        Log.e("ContactSearch", "Something went wrong ", e)
        return emptyList()
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
