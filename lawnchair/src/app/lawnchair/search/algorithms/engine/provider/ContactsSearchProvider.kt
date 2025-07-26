package app.lawnchair.search.algorithms.engine.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.engine.SearchPermission
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ContactsSearchProvider : SearchProvider, SearchPermission {
    override val id: String = "contacts"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        val permissionsGranted = checkPermission(context)
        if (query.isBlank() || !prefs.searchResultPeople.get() || !permissionsGranted) {
            emit(emptyList())
            return@flow
        }

        val maxResults = prefs2.maxPeopleResultCount.firstBlocking()

        // Call the newly encapsulated, private function.
        val contactInfoList = findContactsByName(context, query, maxResults)

        val searchResults = contactInfoList.map { contactInfo ->
            SearchResult.Contact(data = contactInfo)
        }
        emit(searchResults)
    }

    override fun checkPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun findContactsByName(context: Context, query: String, max: Int): List<ContactInfo> {
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

    private const val CONTACT_ACCOUNT_ID = "contact.id"
    private const val CONTACT_ACCOUNT_MIME = "contact.mime"
    private const val CONTACT_ACCOUNT_NAME = "contact.account.name"
    private const val CONTACT_ACCOUNT_TITLE = "contact.title"
    private const val CONTACT_ACCOUNT_TYPE = "contact.account.type"

    private val EXCLUDED_MIME_TYPES = arrayOf(
        "vnd.android.cursor.item/name",
        "vnd.android.cursor.item/nickname",
        "vnd.android.cursor.item/note",
        "vnd.android.cursor.item/photo",
        "vnd.com.google.cursor.item/contact_misc",
        "vnd.android.cursor.item/identity",
        "vnd.android.cursor.item/website",
    )
}
