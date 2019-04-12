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

package ch.deletescape.lawnchair.font

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.net.Uri
import android.support.annotation.Keep
import android.support.v4.provider.FontRequest
import android.support.v4.provider.FontsContractCompat
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.font.googlefonts.GoogleFontsListing
import ch.deletescape.lawnchair.uiWorkerHandler
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.R
import org.json.JSONObject
import java.io.File

class FontCache(private val context: Context) {

    private val fontLoaders = HashMap<Font, FontLoader>()
    private val weightNameMap: Map<String, String> = mapOf(
            Pair("100", R.string.font_weight_thin),
            Pair("200", R.string.font_weight_extra_light),
            Pair("300", R.string.font_weight_light),
            Pair("400", R.string.font_weight_regular),
            Pair("500", R.string.font_weight_medium),
            Pair("600", R.string.font_weight_semi_bold),
            Pair("700", R.string.font_weight_bold),
            Pair("800", R.string.font_weight_extra_bold),
            Pair("900", R.string.font_weight_extra_black)
    ).mapValues { context.getString(it.value) }

    fun loadFont(font: Font): FontLoader {
        return fontLoaders.getOrPut(font) { FontLoader(font) }
    }

    class Family(val displayName: String, val variants: Map<String, Font>) {

        constructor(font: Font) : this(font.displayName, mapOf(Pair("regular", font)))

        val default = variants.getOrElse("regular") { variants.values.first() }
    }

    abstract class Font {

        abstract val fullDisplayName: String
        abstract val displayName: String

        abstract fun load(callback: LoadCallback)

        open fun saveToJson(obj: JSONObject) {
            obj.put(KEY_CLASS_NAME, this::class.java.name)
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

        override fun load(callback: LoadCallback) {
            callback.onFontLoaded(typeface)
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
            TypefaceFont(Typeface.createFromFile(file)) {

        override val fullDisplayName: String = if (typeface === Typeface.DEFAULT)
            context.getString(R.string.pref_fonts_missing_font) else Uri.decode(file.name)

        fun delete() = file.delete()

        override fun saveToJson(obj: JSONObject) {
            super.saveToJson(obj)
            obj.put(KEY_FONT_NAME, fullDisplayName)
        }

        override fun equals(other: Any?): Boolean {
            return other is TTFFont && fullDisplayName == other.fullDisplayName
        }

        override fun hashCode() = fullDisplayName.hashCode()

        companion object {

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

    class GoogleFont(
            private val context: Context,
            private val family: String,
            private val variant: String = "regular") : Font() {

        private val hashCode = "GoogleFont|$family|$variant".hashCode()

        override val displayName = createVariantName()
        override val fullDisplayName = "$family $displayName"

        private fun createVariantName(): String {
            if (variant == "italic") return context.getString(R.string.font_variant_italic)
            val weight = GoogleFontsListing.getWeight(variant)
            val weightString = FontCache.getInstance(context).weightNameMap[weight] ?: weight
            val italicString = if (GoogleFontsListing.isItalic(variant))
                " " + context.getString(R.string.font_variant_italic) else ""
            return "$weightString$italicString"
        }

        override fun load(callback: LoadCallback) {
            val request = FontRequest(
                    "com.google.android.gms.fonts", // ProviderAuthority
                    "com.google.android.gms",  // ProviderPackage
                    GoogleFontsListing.buildQuery(family, variant),  // Query
                    R.array.com_google_android_gms_fonts_certs)

            // retrieve font in the background
            FontsContractCompat.requestFont(context, request, object : FontsContractCompat.FontRequestCallback() {
                override fun onTypefaceRetrieved(typeface: Typeface) {
                    callback.onFontLoaded(typeface)
                }

                override fun onTypefaceRequestFailed(reason: Int) {
                    callback.onFontLoaded(null)
                }
            }, uiWorkerHandler)
        }

        override fun saveToJson(obj: JSONObject) {
            super.saveToJson(obj)
            obj.put(KEY_FAMILY_NAME, family)
            obj.put(KEY_VARIANT, variant)
        }

        override fun equals(other: Any?): Boolean {
            return other is GoogleFont && family == other.family && variant == other.variant
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {

            @Keep
            @JvmStatic
            fun fromJson(context: Context, obj: JSONObject): Font {
                val family = obj.getString(KEY_FAMILY_NAME)
                val variant = obj.getString(KEY_VARIANT)
                return GoogleFont(context, family, variant)
            }
        }
    }

    companion object : SingletonHolder<FontCache, Context>(ensureOnMainThread(
            useApplicationContext(::FontCache))) {

        private const val KEY_CLASS_NAME = "className"
        private const val KEY_FAMILY_NAME = "family"
        private const val KEY_STYLE = "style"
        private const val KEY_VARIANT = "variant"
        private const val KEY_FONT_NAME = "font"
    }
}
