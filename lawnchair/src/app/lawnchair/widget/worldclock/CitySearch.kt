package app.lawnchair.widget.worldclock

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class Place(
    val name: String,
    val admin1: String?,
    val country: String?,
    val countryCode: String?,
    val zone: String,
    val population: Long,
) {
    /** "Nebraska, U.S.A.": first-level region and country, without repeating the name */
    val region: String
        get() = listOfNotNull(
            admin1?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
            countryLabel(countryCode, country),
        ).joinToString(", ")
}

/**
 * Finds any city or town through the Open-Meteo geocoder (GeoNames data), which returns the tz
 * database zone for each place. "Lincoln ne" is tried as typed, then as "Lincoln" qualified by
 * "ne", matched against state abbreviations, regions and countries.
 */
object CitySearch {

    private const val ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
    private const val TIMEOUT_MS = 8_000
    private const val MAX_RESULTS = 25
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length < 2) return@withContext emptyList()
        // Places matching a qualifier ("ne" in "Lincoln ne") rank above plain name matches
        val qualified = LinkedHashMap<String, Place>()
        val plain = LinkedHashMap<String, Place>()
        for ((name, qualifier) in attempts(q)) {
            if (name.length < 2) continue
            val places = fetch(name)
            if (qualifier.isNullOrBlank()) {
                places.forEach { plain.putIfAbsent(it.key, it) }
            } else {
                places.filter { matches(it, qualifier) }.forEach { qualified.putIfAbsent(it.key, it) }
            }
        }
        val ranked = qualified.values.sortedByDescending { it.population } +
            plain.values.filter { it.key !in qualified }.sortedByDescending { it.population }
        ranked.take(MAX_RESULTS)
    }

    private val Place.key get() = "$name|$admin1|$countryCode|$zone"

    private fun attempts(q: String): List<Pair<String, String?>> = buildList {
        if (',' in q) {
            add(q.substringBefore(',').trim() to q.substringAfter(',').trim())
        } else {
            add(q to null)
            val tokens = q.split(' ')
            for (k in tokens.size - 1 downTo 1) {
                add(tokens.take(k).joinToString(" ") to tokens.drop(k).joinToString(" "))
            }
        }
    }

    private fun matches(place: Place, rawQualifier: String): Boolean {
        val q = rawQualifier.lowercase(Locale.ROOT).replace(".", "").trim()
        val code = place.countryCode?.lowercase(Locale.ROOT)
        val admin1 = place.admin1?.lowercase(Locale.ROOT).orEmpty()
        val country = place.country?.lowercase(Locale.ROOT).orEmpty()
        return when {
            q == code -> true
            q in US_ALIASES -> code == "us"
            REGION_ABBREVIATIONS[q]?.equals(admin1, ignoreCase = true) == true -> true
            q.length >= 3 && (admin1.startsWith(q) || country.startsWith(q)) -> true
            else -> false
        }
    }

    private fun fetch(name: String): List<Place> {
        val url = URL(
            "$ENDPOINT?name=${URLEncoder.encode(name, "UTF-8")}&count=100&language=en&format=json",
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Geocoder returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
            results.mapNotNull { (it as? JsonObject)?.toPlace() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.toPlace(): Place? {
        fun str(key: String) = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val zone = str("timezone") ?: return null
        // Skip zones this device's tz database does not know yet
        if (TimeZone.getTimeZone(zone).id != zone) return null
        return Place(
            name = str("name") ?: return null,
            admin1 = str("admin1"),
            country = str("country"),
            countryCode = str("country_code"),
            zone = zone,
            population = this["population"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private val US_ALIASES = setOf("us", "usa", "united states", "united states of america", "america")

    private val REGION_ABBREVIATIONS = mapOf(
        "al" to "Alabama", "ak" to "Alaska", "az" to "Arizona", "ar" to "Arkansas", "ca" to "California",
        "co" to "Colorado", "ct" to "Connecticut", "de" to "Delaware", "dc" to "Washington, D.C.",
        "fl" to "Florida", "ga" to "Georgia", "hi" to "Hawaii", "id" to "Idaho", "il" to "Illinois",
        "in" to "Indiana", "ia" to "Iowa", "ks" to "Kansas", "ky" to "Kentucky", "la" to "Louisiana",
        "me" to "Maine", "md" to "Maryland", "ma" to "Massachusetts", "mi" to "Michigan",
        "mn" to "Minnesota", "ms" to "Mississippi", "mo" to "Missouri", "mt" to "Montana",
        "ne" to "Nebraska", "nv" to "Nevada", "nh" to "New Hampshire", "nj" to "New Jersey",
        "nm" to "New Mexico", "ny" to "New York", "nc" to "North Carolina", "nd" to "North Dakota",
        "oh" to "Ohio", "ok" to "Oklahoma", "or" to "Oregon", "pa" to "Pennsylvania",
        "ri" to "Rhode Island", "sc" to "South Carolina", "sd" to "South Dakota", "tn" to "Tennessee",
        "tx" to "Texas", "ut" to "Utah", "vt" to "Vermont", "va" to "Virginia", "wa" to "Washington",
        "wv" to "West Virginia", "wi" to "Wisconsin", "wy" to "Wyoming",
        "ab" to "Alberta", "bc" to "British Columbia", "mb" to "Manitoba", "nb" to "New Brunswick",
        "nl" to "Newfoundland and Labrador", "ns" to "Nova Scotia", "on" to "Ontario",
        "pe" to "Prince Edward Island", "qc" to "Quebec", "sk" to "Saskatchewan",
        "nsw" to "New South Wales", "vic" to "Victoria", "qld" to "Queensland", "wa au" to "Western Australia",
    )
}

internal fun countryLabel(code: String?, name: String?): String? = when (code?.uppercase(Locale.ROOT)) {
    "US" -> "U.S.A."
    "GB" -> "U.K."
    else -> name
}

/** Short zone name for display, e.g. CST or CDT for America/Chicago depending on the date */
internal fun zoneAbbreviation(zone: String, now: Long = System.currentTimeMillis()): String {
    val tz = TimeZone.getTimeZone(zone)
    return tz.getDisplayName(tz.inDaylightTime(java.util.Date(now)), TimeZone.SHORT, Locale.US)
}
