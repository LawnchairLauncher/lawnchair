package app.lawnchair.backup

import android.content.SharedPreferences
import java.io.File
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Restores through the cached preferences instance; replacing its XML file alone is unsafe. */
internal object BackupSharedPreferences {
    fun read(file: File): Map<String, Any?> = file.inputStream().use { input ->
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, "map")
        buildMap {
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                val type = parser.name
                val name = requireNotNull(parser.getAttributeValue(null, "name")) { "Preference has no name" }
                val value = when (type) {
                    "string" -> parser.nextText()

                    "set" -> buildSet {
                        while (parser.nextTag() == XmlPullParser.START_TAG) {
                            parser.require(XmlPullParser.START_TAG, null, "string")
                            add(parser.nextText())
                        }
                    }

                    else -> {
                        val attribute = parser.getAttributeValue(null, "value")
                        val scalar = when (type) {
                            "boolean" -> requireNotNull(attribute).toBooleanStrict()
                            "int" -> requireNotNull(attribute).toInt()
                            "long" -> requireNotNull(attribute).toLong()
                            "float" -> requireNotNull(attribute).toFloat()
                            "null" -> null
                            else -> error("Unsupported preference type: $type")
                        }
                        parser.nextTag()
                        scalar
                    }
                }
                parser.require(XmlPullParser.END_TAG, null, type)
                put(name, value)
            }
            parser.require(XmlPullParser.END_TAG, null, "map")
        }
    }

    fun apply(preferences: SharedPreferences, values: Map<String, Any?>) {
        // Keep the live database and its grid selected until the layout installer replaces them.
        // Archive filenames may not exist here; even a failed copy must retain the live selection.
        val selectionKeys = BackupLayoutDatabase.selectionKeys
        val restoredValues = values - selectionKeys + preferences.all.filterKeys { it in selectionKeys }
        val editor = preferences.edit().clear()
        restoredValues.forEach { (name, value) ->
            when (value) {
                null -> Unit
                is Boolean -> editor.putBoolean(name, value)
                is Int -> editor.putInt(name, value)
                is Long -> editor.putLong(name, value)
                is Float -> editor.putFloat(name, value)
                is String -> editor.putString(name, value)
                is Set<*> -> editor.putStringSet(name, value.map { it as String }.toSet())
                else -> error("Unsupported preference value")
            }
        }
        check(editor.commit()) { "Unable to restore launcher preferences" }
    }
}
