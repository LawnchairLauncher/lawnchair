package ch.deletescape.lawnchair.sesame

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.toUri
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder

class SesameDataProvider private constructor(private val context: Context) {
    private val defaultCount = 20

    fun recentlyUsedShortcuts(count: Int = defaultCount): List<SesameResult> {
        return query(SesameProvider.RECENTLY_USED, count)
    }

    fun queryShortcutsForPackage(packageName: String?): List<SesameResult> {
        return query(query = packageName)
    }

    fun query(provider: SesameProvider = SesameProvider.RECENTLY_USED, count: Int = defaultCount, types: Array<SesameType> = arrayOf(SesameType.SHORTCUT), query: String? = null): List<SesameResult> {
        val selectionArgs = if (query.isNullOrBlank()) null else arrayOf(query)
        val cursor = context.contentResolver.query(provider.toUri(count, types), null, null, selectionArgs, null)
        if (cursor == null || cursor.count < 1) {
            cursor?.close()
            return emptyList()
        }
        val list = mutableListOf<SesameResult>()
        while (cursor.moveToNext()) {
            with(cursor) {
                val cName = getString(getColumnIndex(SesameColumn.COMPONENT_NAME.toString()))
                val componentName = if (cName != null) ComponentName.unflattenFromString(cName) else null
                list.add(SesameResult(
                        getString(getColumnIndex(SesameColumn.URI.toString())),
                        getString(getColumnIndex(SesameColumn.PACKAGE_NAME.toString())),
                        componentName,
                        getString(getColumnIndex(SesameColumn.DISPLAY_LABEL.toString())),
                        getString(getColumnIndex(SesameColumn.SEARCH_RESULT_LABEL.toString())),
                        getString(getColumnIndex(SesameColumn.ICON_URI.toString()))?.toUri(),
                        getString(getColumnIndex(SesameColumn.INTENT.toString())).toUri(),
                        getString(getColumnIndex(SesameColumn.DIRECT_INTENT.toString())).toUri(),
                        SesameType.fromString(getString(getColumnIndex(SesameColumn.TYPE.toString()))),
                        getString(getColumnIndex(SesameColumn.TYPE_DATA.toString()))
                ))
            }
        }
        return list
    }

    enum class SesameProvider constructor(private val uri: String) {
        RECENTLY_USED("content://ninja.sesame.app.provider/links"),
        SEARCH("content://ninja.sesame.app.provider/search");

        override fun toString(): String {
            return uri
        }

        fun toUri(count: Int, types: Array<SesameType>): Uri{
            return "$uri?count=$count&types=${types.joinToString()}".toUri()
        }
    }

    enum class SesameColumn constructor(private val colName: String) {
        /**
         * A unique string ID for each shortcut, mainly used internally by Sesame
         */
        URI("uri"),
        /**
         * Name of the package the shortcut belongs to, if any
         */
        PACKAGE_NAME("packageName"),
        /**
         * ComponentName of the shortcut, if any
         */
        COMPONENT_NAME("componentName"),
        /**
         * Plain text display label
         */
        DISPLAY_LABEL("displayLabel"),
        /**
         * HTML formatted display label, used to highlight matches in search results
         */
        SEARCH_RESULT_LABEL("searchResultLabel"),
        /**
         * URI for related icon; could be an "https" resource; occasionally null;
         * file icons and contact icons will automatically be proxied through Sesame
         * so that the querying app doesn't need additional permissions
         */
        ICON_URI("iconUri"),
        /**
         * Intent that opens the shortcut by relaying it through Sesame;
         * Data from this is used to improve results
         */
        INTENT("intentUri"),
        /**
         * Direct intent that opens the shortcut without relaying it through Sesame
         */
        DIRECT_INTENT("directIntentUri"),
        /**
         * One of the types in SesameType
         */
        TYPE("type"),
        /**
         * JSON String that describes additional actions for this shortcut;
         * mainly used for SMS/call/E-Mail for contacts and for QuickSearch
         */
        TYPE_DATA("typeData");

        override fun toString(): String {
            return colName
        }
    }

    enum class SesameType constructor(private val queryName: String) {
        APP("app"),
        SHORTCUT("shortcut"),
        CONTACT("contact"),
        QUICKSEARCH("search");

        override fun toString(): String {
            return queryName
        }

        companion object {
            fun fromString(string: String): SesameType {
                return when (string) {
                    "app" -> APP
                    "shortcut" -> SHORTCUT
                    "contact" -> CONTACT
                    "search" -> QUICKSEARCH
                    else -> SHORTCUT
                }
            }
        }
    }

    class SesameResult(
            /**
             * A unique string ID for each shortcut, mainly used internally by Sesame
             */
            val uri: String,
            /**
             * Name of the package the shortcut belongs to, if any
             */
            val packageName: String?,
            /**
             * ComponentName of the shortcut, if any
             */
            val componentName: ComponentName?,
            /**
             * Plain text display label
             */
            val displayLabel: String,
            /**
             * HTML formatted display label, used to highlight matches in search results
             */
            val searchResultLabel: String?,
            /**
             * URI for related icon; could be an "https" resource; occasionally null;
             * file icons and contact icons will automatically be proxied through Sesame
             * so that the querying app doesn't need additional permissions
             */
            val iconUri: Uri?,
            /**
             * Intent that opens the shortcut by relaying it through Sesame;
             * Data from this is used to improve results
             */
            val intent: Uri,
            /**
             * Direct intent that opens the shortcut without relaying it through Sesame
             */
            val directIntent: Uri,
            val type: SesameType,
            val typeData: String?)

    companion object : SingletonHolder<SesameDataProvider, Context>(ensureOnMainThread(useApplicationContext(::SesameDataProvider)))
}
