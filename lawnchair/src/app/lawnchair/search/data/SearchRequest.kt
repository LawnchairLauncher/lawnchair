package app.lawnchair.search.data

import android.content.Context
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import app.lawnchair.search.data.suggestion.StartPageService
import app.lawnchair.util.kotlinxJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://www.startpage.com/")
    .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
    .build()

val startPageService: StartPageService = retrofit.create(StartPageService::class.java)

suspend fun getStartPageSuggestions(query: String, max: Int): List<String> {
    if (query.isEmpty() || query.isBlank() || max <= 0) return emptyList()

    try {
        return withContext(Dispatchers.IO) {
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
            }

            return@withContext emptyList()
        }
    } catch (e: Exception) {
        Log.d("Failed to retrieve suggestions", ": $e")
        return emptyList()
    }
}

suspend fun findContactByName(context: Context, query: String, max: Int): List<ContactInfo> {
    if (query.isEmpty() || query.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val contactMap = HashMap<String, ContactInfo>()

            val projection = arrayOf(
                ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA3, ContactsContract.Data.DATA5,
                "phonebook_label", "account_type", "account_name", ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.PHOTO_URI,
            )

            val selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val queryCursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )

            queryCursor?.use {
                while (it.moveToNext() && contactMap.size <= max) {
                    try {
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

                        if (key != null && !EXCLUDED_MIME_TYPES.contains(mimeType)) {
                            contactMap[key] = ContactInfo(
                                contactId,
                                displayName,
                                phoneNumber,
                                phonebookLabel2,
                                imageUri,
                                JSONArray().toString(),
                            )
                        } else {
                            if (contactMap.containsKey(contactId)) {
                                val existingContact = contactMap[contactId]
                                val jsonArray = JSONArray(existingContact?.packages ?: "")
                                val jsonObject = JSONObject()
                                jsonObject.put(CONTACT_ACCOUNT_ID, key)
                                jsonObject.put(CONTACT_ACCOUNT_TITLE, data5)
                                jsonObject.put(CONTACT_ACCOUNT_NAME, accountName)
                                jsonObject.put(CONTACT_ACCOUNT_TYPE, accountType)
                                jsonObject.put(CONTACT_ACCOUNT_MIME, mimeType)
                                jsonArray.put(jsonObject)
                                existingContact?.packages = jsonArray.toString()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ContactSearch", "Something went wrong ", e)
                    }
                }
            }
            queryCursor?.close()
            continuation.resume(ArrayList(contactMap.values))
        }
    }
}

suspend fun findByFileName(context: Context, query: String, max: Int): List<FileInfo> {
    if (query.isEmpty() || query.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val fileList = HashMap<String, FileInfo>()

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
            )

            val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"

            val queryCursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )

            queryCursor?.use {
                while (it.moveToNext()) {
                    try {
                        val fileIdIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        val displayNameIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                        val mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val filePathIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                        val fileId = it.getLong(fileIdIndex)
                        val displayName = it.getString(displayNameIndex)
                        val mimeType = it.getString(mimeTypeIndex)
                        val mediaType = it.getInt(mediaTypeIndex)
                        val filePath = it.getString(filePathIndex)
                        val key = fileId.toString()
                        fileList[key] = FileInfo(fileId, displayName, filePath, mimeType, mediaType)

                        if (fileList.size >= max) {
                            it.moveToLast()
                        }
                    } catch (e: Exception) {
                        Log.e("FileSearch", "Something went wrong ", e)
                    }
                }
            }
            queryCursor?.close()
            continuation.resume(ArrayList(fileList.values))
        }
    }
}
