package app.lawnchair.ui.preferences.data.liveinfo.model

import android.util.Log
import org.json.JSONArray

data class Announcement(
    val text: String,
    val url: String,
    val active: Boolean,
) {

    companion object {

        fun fromJsonArray(value: JSONArray): List<Announcement> =
            try {
                (0 until value.length())
                    .map { value.getJSONObject(it) }
                    .map {
                        Announcement(
                            text = it.getString("text"),
                            url = it.getString("url"),
                            active = it.getBoolean("active"),
                        )
                    }
            } catch (e: Exception) {
                Log.e("Announcement", "fromJsonArray: Failed to parse: ${e.message}")
                emptyList()
            }

        fun fromString(value: String): List<Announcement> =
            try {
                fromJsonArray(JSONArray(value))
            } catch (e: Exception) {
                Log.e("Announcement", "fromString: Failed to parse: ${e.message}")
                emptyList()
            }
    }

    override fun toString(): String = """
        {
            "text": "$text",
            "url": "$url",
            "active": $active
        }
    """.trimIndent()
}
