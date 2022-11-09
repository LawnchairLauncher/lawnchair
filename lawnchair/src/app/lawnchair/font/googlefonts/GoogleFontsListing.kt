/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.lawnchair.font.googlefonts

import android.content.Context
import android.content.res.Resources
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.toArrayList
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class GoogleFontsListing private constructor(private val context: Context) {
    private val scope = CoroutineScope(CoroutineName("GoogleFontsListing"))

    private val dataProvider = MockDataProvider(context.resources)
    private val fonts by lazy { scope.async(Dispatchers.IO) { loadFontListing() } }

    private suspend fun loadFontListing(): List<GoogleFontInfo> {
        val json = dataProvider.getFontListing()
        return parseFontListing(json)
    }

    private suspend fun getAdditionalFonts(): List<String> {
        val prefs = PreferenceManager2.getInstance(context)
        val userFontsString = prefs.additionalFonts.get().first()
        val userFonts = if (userFontsString.isEmpty()) emptyList() else userFontsString.split(",")
        return listOf("Inter") + userFonts
    }

    private suspend fun parseFontListing(json: JSONObject): List<GoogleFontInfo> {
        val fonts = ArrayList<GoogleFontInfo>()
        val items = json.getJSONArray(KEY_ITEMS)
        for (i in (0 until items.length())) {
            val font = items.getJSONObject(i)
            val family = font.getString(KEY_FAMILY)
            val variants = font.getJSONArray(KEY_VARIANTS).toArrayList<String>()
            fonts.add(GoogleFontInfo(family, variants))
        }
        getAdditionalFonts().forEach {
            fonts.add(GoogleFontInfo(it, listOf("regular", "italic", "500", "500italic", "700", "700italic")))
        }
        fonts.sort()
        return fonts
    }

    suspend fun getFonts(): List<GoogleFontInfo> {
        return fonts.await()
    }

    sealed interface DataProvider {

        fun getFontListing(): JSONObject
    }

    class MockDataProvider(private val res: Resources) : DataProvider {

        override fun getFontListing(): JSONObject {
            val json = res.assets.open("google_fonts.json").bufferedReader().use { it.readText() }
            return JSONObject(json)
        }
    }

    class GoogleFontInfo(val family: String, val variants: List<String>) : Comparable<GoogleFontInfo> {

        override fun compareTo(other: GoogleFontInfo): Int {
            return family.compareTo(other.family)
        }
    }

    companion object {

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::GoogleFontsListing)

        private const val KEY_ITEMS = "items"
        private const val KEY_FAMILY = "family"
        private const val KEY_VARIANTS = "variants"

        fun getWeight(variant: String): String {
            if (variant == "italic") return "400"
            return variant.replace("italic", "").replace("regular", "400")
        }

        fun isItalic(variant: String): Boolean {
            return variant.contains("italic")
        }

        fun buildQuery(family: String, variant: String): String {
            val weight = getWeight(variant)
            val italic = isItalic(variant)
            return "name=$family&weight=$weight&italic=${if (italic) 1 else 0}&besteffort=1"
        }
    }
}
