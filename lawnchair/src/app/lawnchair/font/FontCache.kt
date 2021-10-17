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

package app.lawnchair.font

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.util.uiHelperHandler
import com.android.launcher3.R
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FontCache private constructor(private val context: Context) {

    private val scope = MainScope() + CoroutineName("FontCache")

    private val deferredFonts = mutableMapOf<Font, Deferred<LoadedFont?>>()

    val uiRegular = ResourceFont(context, R.font.inter_regular, "Inter")
    val uiMedium = ResourceFont(context, R.font.inter_medium, "Inter Medium")
    val uiText = ResourceFont(context, R.font.inter_regular, "Inter")
    val uiTextMedium = ResourceFont(context, R.font.inter_medium, "Inter Medium")

    suspend fun getTypeface(font: Font): Typeface? {
        return loadFontAsync(font).await()?.typeface
    }

    suspend fun getFontFamily(font: Font): FontFamily? {
        return loadFontAsync(font).await()?.fontFamily
    }

    fun preloadFont(font: Font) {
        @Suppress("DeferredResultUnused")
        loadFontAsync(font)
    }

    @ExperimentalCoroutinesApi
    fun getLoadedFont(font: Font): LoadedFont? {
        val deferredFont = deferredFonts[font] ?: return null
        if (!deferredFont.isCompleted) return null
        return deferredFont.getCompleted()
    }

    private fun loadFontAsync(font: Font): Deferred<LoadedFont?> {
        return deferredFonts.getOrPut(font) {
            scope.async {
                font.load()?.let { LoadedFont(it) }
            }
        }
    }

    class Family(val displayName: String, val variants: Map<String, Font>) {

        constructor(font: Font) : this(font.displayName, mapOf(Pair("regular", font)))

        val default = variants.getOrElse("regular") { variants.values.first() }
        val sortedVariants by lazy { variants.values.sortedBy { it.familySorter } }
    }

    class TypefaceFamily(val variants: Map<String, Typeface?>) {

        val default = variants.getOrElse("regular") { variants.values.firstOrNull() }
    }

    abstract class Font {

        abstract val fullDisplayName: String
        abstract val displayName: String
        open val familySorter get() = fullDisplayName
        open val isAvailable get() = true

        abstract suspend fun load(): Typeface?

        open fun saveToJson(obj: JSONObject) {
            obj.put(KEY_CLASS_NAME, this::class.java.name)
        }

        open fun createWithWeight(weight: Int): Font {
            return this
        }

        fun toJsonString(): String {
            val obj = JSONObject()
            saveToJson(obj)
            return obj.toString()
        }

        interface LoadCallback {

            fun onFontLoaded(typeface: Typeface?)
        }

        companion object {

            fun fromJsonString(context: Context, jsonString: String): Font {
                val obj = JSONObject(jsonString)
                val className = obj.getString(KEY_CLASS_NAME)
                val clazz = Class.forName(className)
                val constructor = clazz.getMethod("fromJson", Context::class.java, JSONObject::class.java)
                return constructor.invoke(null, context, obj) as Font
            }
        }
    }

    abstract class TypefaceFont(protected val typeface: Typeface?) : Font() {

        override val fullDisplayName = typeface.toString()
        override val displayName get() = fullDisplayName

        override suspend fun load(): Typeface? {
            return typeface
        }

        override fun equals(other: Any?): Boolean {
            return other is TypefaceFont && typeface == other.typeface
        }

        override fun hashCode(): Int {
            return fullDisplayName.hashCode()
        }
    }

    class DummyFont : TypefaceFont(null) {

        private val hashCode = "DummyFont".hashCode()

        override fun equals(other: Any?): Boolean {
            return other is DummyFont
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {

            @Keep
            @JvmStatic
            fun fromJson(context: Context, obj: JSONObject): Font {
                return DummyFont()
            }
        }
    }

    class TTFFont(context: Context, private val file: File) :
        TypefaceFont(createTypeface(file)) {

        private val actualName: String = Uri.decode(file.name)
        override val isAvailable = typeface != null
        override val fullDisplayName: String = if (typeface == null)
            context.getString(R.string.pref_fonts_missing_font) else actualName

        fun delete() = file.delete()

        override fun saveToJson(obj: JSONObject) {
            super.saveToJson(obj)
            obj.put(KEY_FONT_NAME, fullDisplayName)
        }

        override fun equals(other: Any?): Boolean {
            return other is TTFFont && actualName == other.actualName
        }

        override fun hashCode() = actualName.hashCode()

        companion object {

            fun createTypeface(file: File): Typeface? {
                return try {
                    Typeface.createFromFile(file)
                } catch (e: Exception) {
                    null
                }
            }

            fun getFontsDir(context: Context): File {
                return File(context.filesDir, "customFonts").apply { mkdirs() }
            }

            fun getFile(context: Context, name: String): File {
                return File(getFontsDir(context), Uri.encode(name))
            }

            @Keep
            @JvmStatic
            fun fromJson(context: Context, obj: JSONObject): Font {
                val fontName = obj.getString(KEY_FONT_NAME)
                return TTFFont(context, getFile(context, fontName))
            }
        }
    }

    class SystemFont(
        val family: String,
        val style: Int = Typeface.NORMAL) : TypefaceFont(Typeface.create(family, style)) {

        private val hashCode = "SystemFont|$family|$style".hashCode()

        override val fullDisplayName = family

        override fun saveToJson(obj: JSONObject) {
            super.saveToJson(obj)
            obj.put(KEY_FAMILY_NAME, family)
            obj.put(KEY_STYLE, style)
        }

        override fun equals(other: Any?): Boolean {
            return other is SystemFont && family == other.family && style == other.style
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun createWithWeight(weight: Int): Font {
            if (weight >= 700) {
                return SystemFont(family, Typeface.BOLD)
            }
            return super.createWithWeight(weight)
        }

        companion object {

            @Keep
            @JvmStatic
            fun fromJson(context: Context, obj: JSONObject): Font {
                val family = obj.getString(KEY_FAMILY_NAME)
                val style = obj.getInt(KEY_STYLE)
                return SystemFont(family, style)
            }
        }
    }

    class AssetFont(
        assets: AssetManager,
        private val name: String) : TypefaceFont(Typeface.createFromAsset(assets, "$name.ttf")) {

        private val hashCode = "AssetFont|$name".hashCode()

        override val fullDisplayName = name

        override fun equals(other: Any?): Boolean {
            return other is AssetFont && name == other.name
        }

        override fun hashCode(): Int {
            return hashCode
        }
    }

    class ResourceFont(
        context: Context,
        resId: Int,
        private val name: String
    ) : TypefaceFont(ResourcesCompat.getFont(context, resId)) {

        private val hashCode = "ResourceFont|$name".hashCode()

        override val fullDisplayName = name

        override fun equals(other: Any?): Boolean {
            return other is ResourceFont && name == other.name
        }

        override fun hashCode(): Int {
            return hashCode
        }
    }

    class GoogleFont(
        private val context: Context,
        private val family: String,
        private val variant: String = "regular",
        private val variants: Array<String> = emptyArray()) : Font() {

        private val hashCode = "GoogleFont|$family|$variant".hashCode()

        override val displayName = createVariantName()
        override val fullDisplayName = "$family $displayName"
        override val familySorter = "${GoogleFontsListing.getWeight(variant)}${GoogleFontsListing.isItalic(variant)}"

        private fun createVariantName(): String {
            if (variant == "italic") return context.getString(R.string.font_variant_italic)
            val weight = GoogleFontsListing.getWeight(variant)
            val weightString = weightNameMap[weight]?.let(context::getString) ?: weight
            val italicString = if (GoogleFontsListing.isItalic(variant))
                " " + context.getString(R.string.font_variant_italic) else ""
            return "$weightString$italicString"
        }

        override suspend fun load(): Typeface? {
            val request = FontRequest(
                "com.google.android.gms.fonts", // ProviderAuthority
                "com.google.android.gms",  // ProviderPackage
                GoogleFontsListing.buildQuery(family, variant),  // Query
                R.array.com_google_android_gms_fonts_certs)

            return suspendCoroutine {
                FontsContractCompat.requestFont(context, request, object: FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) {
                        it.resume(typeface)
                    }

                    override fun onTypefaceRequestFailed(reason: Int) {
                        it.resume(null)
                    }
                }, uiHelperHandler)
            }
        }

        override fun saveToJson(obj: JSONObject) {
            super.saveToJson(obj)
            obj.put(KEY_FAMILY_NAME, family)
            obj.put(KEY_VARIANT, variant)
            val variantsArray = JSONArray()
            variants.forEach { variantsArray.put(it) }
            obj.put(KEY_VARIANTS, variantsArray)
        }

        override fun equals(other: Any?): Boolean {
            return other is GoogleFont && family == other.family && variant == other.variant
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun createWithWeight(weight: Int): Font {
            if (weight == -1) return this
            val currentWeight = GoogleFontsListing.getWeight(variant).toInt()
            if (weight == currentWeight) return this
            val newVariant = if (weight > currentWeight)
                findHeavier(weight, currentWeight, GoogleFontsListing.isItalic(variant))
            else
                findLighter(weight, currentWeight, GoogleFontsListing.isItalic(variant))
            if (newVariant != null) {
                return GoogleFont(context, family, newVariant, variants)
            }
            return super.createWithWeight(weight)
        }

        private fun findHeavier(weight: Int, minWeight: Int, italic: Boolean): String? {
            val variants = variants.filter { it.contains("italic") == italic }
            return variants.lastOrNull {
                val variantWeight = GoogleFontsListing.getWeight(it).toInt()
                variantWeight in minWeight..weight
            } ?: variants.firstOrNull {
                GoogleFontsListing.getWeight(it).toInt() >= minWeight
            }
        }

        private fun findLighter(weight: Int, maxWeight: Int, italic: Boolean): String? {
            val variants = variants.filter { it.contains("italic") == italic }
            return variants.firstOrNull {
                val variantWeight = GoogleFontsListing.getWeight(it).toInt()
                variantWeight in weight..maxWeight
            } ?: variants.lastOrNull {
                GoogleFontsListing.getWeight(it).toInt() <= maxWeight
            }
        }

        companion object {

            @Keep
            @JvmStatic
            fun fromJson(context: Context, obj: JSONObject): Font {
                val family = obj.getString(KEY_FAMILY_NAME)
                val variant = obj.getString(KEY_VARIANT)
                val variantsArray = obj.optJSONArray(KEY_VARIANTS) ?: JSONArray()
                val variants = Array<String>(variantsArray.length()) { variantsArray.getString(it) }
                return GoogleFont(context, family, variant, variants)
            }
        }
    }

    class LoadedFont internal constructor(val typeface: Typeface) {
        val fontFamily = FontFamily(typeface)
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::FontCache)

        private const val KEY_CLASS_NAME = "className"
        private const val KEY_FAMILY_NAME = "family"
        private const val KEY_STYLE = "style"
        private const val KEY_VARIANT = "variant"
        private const val KEY_VARIANTS = "variants"
        private const val KEY_FONT_NAME = "font"

        private val weightNameMap: Map<String, Int> = mapOf(
            Pair("100", R.string.font_weight_thin),
            Pair("200", R.string.font_weight_extra_light),
            Pair("300", R.string.font_weight_light),
            Pair("400", R.string.font_weight_regular),
            Pair("500", R.string.font_weight_medium),
            Pair("600", R.string.font_weight_semi_bold),
            Pair("700", R.string.font_weight_bold),
            Pair("800", R.string.font_weight_extra_bold),
            Pair("900", R.string.font_weight_extra_black)
        )

        val emptyTypefaceFamily = TypefaceFamily(emptyMap())
    }
}

@Composable
fun FontCache.Font.toFontFamily(): Result<FontFamily?>? {
    val context = LocalContext.current
    val font = this
    @Suppress("EXPERIMENTAL_API_USAGE")
    val state = produceState<Result<FontFamily?>?>(initialValue = null, font) {
        val fontCache = FontCache.INSTANCE.get(context)
        val loadedFont = fontCache.getLoadedFont(font)
        if (loadedFont != null) {
            value = Result.success(loadedFont.fontFamily)
        } else {
            value = null
            value = Result.success(fontCache.getFontFamily(font))
        }
    }
    return state.value
}
