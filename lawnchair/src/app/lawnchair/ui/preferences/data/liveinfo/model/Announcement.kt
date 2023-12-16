package app.lawnchair.ui.preferences.data.liveinfo.model

import android.util.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray

data class Announcement(
    val text: String,
    val url: String,
    val active: Boolean,
    val test: Boolean,
) {

    companion object {

        fun fromJsonArray(value: JSONArray): ImmutableList<Announcement> =
            try {
                (0 until value.length())
                    .map { value.getJSONObject(it) }
                    .map {
                        Announcement(
                            text = it.optString("text", ""),
                            url = it.optString("url", ""),
                            active = it.optBoolean("active", true),
                            test = it.optBoolean("test", false),
                        )
                    }
                    .toImmutableList()
            } catch (e: Exception) {
                Log.e("Announcement", "fromJsonArray: Failed to parse: ${e.message}")
                persistentListOf()
            }

        fun fromString(value: String): ImmutableList<Announcement> =
            try {
                fromJsonArray(JSONArray(value))
            } catch (e: Exception) {
                Log.e("Announcement", "fromString: Failed to parse: ${e.message}")
                persistentListOf()
            }
    }

    override fun toString(): String = """
        {
            "text": "$text",
            "url": "$url",
            "active": $active,
            "test": $test
        }
    """.trimIndent()
}
