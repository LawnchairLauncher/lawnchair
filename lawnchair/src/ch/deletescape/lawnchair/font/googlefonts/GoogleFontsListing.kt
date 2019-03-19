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

package ch.deletescape.lawnchair.font.googlefonts

import android.content.Context
import android.content.res.Resources
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.util.SingletonHolder
import org.json.JSONObject
import java.util.*

class GoogleFontsListing(private val context: Context) {

    private val dataProvider = MockDataProvider(context.resources)
    private val fonts = ArrayList<GoogleFontInfo>()
    private var fontsLoading = false
    private var fontsLoaded = false

    private val callbacks = ArrayList<(List<GoogleFontInfo>) -> Unit>()

    init {
        loadFontListing()
    }

    private fun loadFontListing(): Boolean {
        if (fontsLoading || fontsLoaded) return !fontsLoading
        fontsLoading = true
        fontsLoaded = false
        dataProvider.getFontListing { runOnUiWorkerThread { parseFontListing(it) } }
        return false
    }

    private fun parseFontListing(json: JSONObject) {
        val fonts = ArrayList<GoogleFontInfo>()
        val items = json.getJSONArray(KEY_ITEMS)
        for (i in (0 until items.length())) {
            val font = items.getJSONObject(i)
            val family = font.getString(KEY_FAMILY)
            val variants = font.getJSONArray(KEY_VARIANTS).toArrayList<String>()
            fonts.add(GoogleFontInfo(family, variants))
        }
        fonts.add(GoogleFontInfo("Google Sans", listOf("regular", "italic", "500", "500italic", "700", "700italic")))
        fonts.sort()
        runOnMainThread { onParseFinished(fonts) }
    }

    private fun onParseFinished(results: Collection<GoogleFontInfo>) {
        fonts.addAll(results)
        fontsLoading = false
        fontsLoaded = true
        callbacks.forEach { it(fonts) }
        callbacks.clear()
    }

    fun getFonts(callback: (List<GoogleFontInfo>) -> Unit) {
        if (loadFontListing()) {
            callback(fonts)
        } else {
            callbacks.add(callback)
        }
    }

    interface DataProvider {

        fun getFontListing(callback: (JSONObject) -> Unit)
    }

    class MockDataProvider(private val res: Resources) : DataProvider {

        override fun getFontListing(callback: (JSONObject) -> Unit) {
            val json = res.assets.open("google_fonts.json").bufferedReader().use { it.readText() }
            callback(JSONObject(json))
        }
    }

    class GoogleFontInfo(val family: String, val variants: List<String>) : Comparable<GoogleFontInfo> {

        override fun compareTo(other: GoogleFontInfo): Int {
            return family.compareTo(other.family)
        }
    }

    companion object : SingletonHolder<GoogleFontsListing, Context>(ensureOnMainThread(
            useApplicationContext(::GoogleFontsListing))) {

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
