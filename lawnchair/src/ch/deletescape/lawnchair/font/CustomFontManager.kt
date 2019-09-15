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
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.font.settingsui.FontPreference
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.R
import com.android.launcher3.util.TraceHelper
import java.lang.Exception

class CustomFontManager(private val context: Context) {

    private val loaderManager by lazy { FontCache.getInstance(context) }
    private val specMap by lazy { createFontMap() }

    private val prefs = context.lawnchairPrefs
    private val prefsMap = HashMap<String, FontPref>()

    val fontPrefs: Map<String, FontPref> get() = prefsMap

    private val fontName = context.lawnchairPrefs.customFontName

    private val uiRegular = FontCache.GoogleFont(context, fontName)
    private val uiMedium = FontCache.GoogleFont(context, fontName, VARIANT_MEDIUM)

    private val launcherRegular = FontCache.SystemFont("sans-serif")
    private val launcherMedium = FontCache.SystemFont("sans-serif-medium")
    private val launcherCondensed = FontCache.SystemFont("sans-serif-condensed")

    var enableGlobalFont by prefs.BooleanPref("enable_global_font", false, prefs.recreate)
    private val globalFont = FontPref("pref_font_global", launcherRegular)

    private val workspaceFont = FontPref("pref_font_workspace", launcherCondensed)
    private val folderFont = workspaceFont
    private val smartspaceTextFont = FontPref("pref_font_smartspaceText", uiRegular)

    private val deepShortcutFont = FontPref("pref_font_deepShortcut", launcherRegular)
    private val systemShortcutFont = deepShortcutFont
    private val taskOptionFont = deepShortcutFont

    private val allAppsFont = FontPref("pref_font_allApps", launcherCondensed)
    private val drawerFolderFont = allAppsFont
    private val actionFont = FontPref("pref_font_drawerAppActions", launcherCondensed)
    private val drawerTab = FontPref("pref_font_drawerTab", uiMedium)

    private fun createFontMap(): Map<Int, FontSpec> {
        TraceHelper.beginSection("createFontMap")

        val sansSerif = Typeface.SANS_SERIF
        val sansSerifMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val sansSerifCondensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

        val map = HashMap<Int, FontSpec>()
        map[FONT_TITLE] = FontSpec(uiRegular, sansSerif)
        map[FONT_PREFERENCE_TITLE] = FontSpec(uiRegular, sansSerif)
        map[FONT_BUTTON] = FontSpec(uiMedium, sansSerifMedium)
        map[FONT_CATEGORY_TITLE] = FontSpec(uiMedium, sansSerifMedium)
        map[FONT_DIALOG_TITLE] = FontSpec(uiMedium, sansSerifMedium)
        map[FONT_SETTINGS_TAB] = FontSpec(uiMedium, sansSerifMedium)
        map[FONT_TEXT] = FontSpec(uiRegular, sansSerif)
        map[FONT_SETTINGS_TILE_TITLE] = FontSpec(uiMedium, sansSerifMedium)
        map[FONT_SMARTSPACE_TEXT] = FontSpec(smartspaceTextFont, sansSerif)
        map[FONT_BASE_ICON] = FontSpec(FontCache.DummyFont(), sansSerifCondensed)
        map[FONT_WORKSPACE_ICON] = FontSpec(workspaceFont, sansSerifCondensed)
        map[FONT_ALL_APPS_ICON] = FontSpec(allAppsFont, sansSerifCondensed)
        map[FONT_FOLDER_ICON] = FontSpec(folderFont, sansSerifCondensed)
        map[FONT_ACTION_VIEW] = FontSpec(actionFont, sansSerifCondensed)
        map[FONT_DEEP_SHORTCUT] = FontSpec(deepShortcutFont, sansSerif)
        map[FONT_SYSTEM_SHORTCUT] = FontSpec(systemShortcutFont, sansSerif)
        map[FONT_TASK_OPTION] = FontSpec(taskOptionFont, sansSerif)
        map[FONT_DRAWER_TAB] = FontSpec(drawerTab, sansSerifMedium)
        map[FONT_DRAWER_FOLDER] = FontSpec(drawerFolderFont, sansSerifCondensed)

        TraceHelper.endSection("createFontMap")
        return map
    }

    fun loadCustomFont(textView: TextView, attrs: AttributeSet?) {
        val context = textView.context
        var a = context.obtainStyledAttributes(attrs, R.styleable.CustomFont)
        var fontType = a.getInt(R.styleable.CustomFont_customFontType, -1)
        var fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
        val ap = a.getResourceId(R.styleable.CustomFont_android_textAppearance, -1)
        a.recycle()

        if (ap != -1) {
            a = context.obtainStyledAttributes(ap, R.styleable.CustomFont)
            if (fontType == -1) {
                fontType = a.getInt(R.styleable.CustomFont_customFontType, -1)
            }
            if (fontWeight == -1) {
                fontWeight = a.getInt(R.styleable.CustomFont_customFontWeight, -1)
            }
            a.recycle()
        }

        if (fontType != -1) {
            setCustomFont(textView, fontType, fontWeight)
        }
    }

    @JvmOverloads
    fun setCustomFont(textView: TextView, type: Int, style: Int = -1) {
        val spec = specMap[type] ?: return
        loaderManager.loadFont(spec.font.createWithWeight(style)).into(textView, spec.fallback)
    }

    @JvmOverloads
    fun setCustomFont(receiver: FontLoader.FontReceiver, type: Int, style: Int = -1) {
        val spec = specMap[type] ?: return
        loaderManager.loadFont(spec.font.createWithWeight(style)).into(receiver, spec.fallback)
    }

    class FontSpec(val loader: () -> FontCache.Font, val fallback: Typeface) {

        constructor(font: FontCache.Font, fallback: Typeface) : this({ font }, fallback)

        constructor(pref: FontPref, fallback: Typeface) : this({ pref.font }, fallback)

        val font get() = loader()
    }

    inner class FontPref(key: String, private val default: FontCache.Font) {

        private var prefValue by prefs.NullableStringPref(key, null, ::onChange)
        var preferenceUi: FontPreference? = null

        private var fontCache: FontCache.Font? = null
        val font: FontCache.Font
            get() {
                if (enableGlobalFont) {
                    return fontOrDefault(globalFont.actualFont, default)
                }
                return fontOrDefault(actualFont, default)
            }
        val actualFont: FontCache.Font
            get() {
                if (fontCache == null) {
                    fontCache = loadFont()
                }
                return fontCache!!
            }

        init {
            prefsMap[key] = this
        }

        private fun loadFont(): FontCache.Font {
            val value = prefValue ?: return default
            try {
                return FontCache.Font.fromJsonString(context, value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load font $prefValue", e)
            }
            return default
        }

        fun set(font: FontCache.Font) {
            prefValue = font.toJsonString()
        }

        fun reset() {
            prefValue = null
        }

        private fun onChange() {
            fontCache = null
            preferenceUi?.reloadFont()
            prefs.recreate()
        }

        private fun fontOrDefault(font: FontCache.Font, default: FontCache.Font): FontCache.Font {
            return if (font.isAvailable) font else default
        }
    }

    companion object : SingletonHolder<CustomFontManager, Context>(ensureOnMainThread(
            useApplicationContext(::CustomFontManager))) {

        private const val TAG = "CustomFontManager"

        const val FONT_TITLE = 0
        const val FONT_PREFERENCE_TITLE = 1
        const val FONT_BUTTON = 2
        const val FONT_CATEGORY_TITLE = 3
        const val FONT_DIALOG_TITLE = 4
        const val FONT_SETTINGS_TAB = 5
        const val FONT_TEXT = 6
        const val FONT_SETTINGS_TILE_TITLE = 7
        const val FONT_SMARTSPACE_TEXT = 8
        const val FONT_BASE_ICON = 9
        const val FONT_WORKSPACE_ICON = 10
        const val FONT_ALL_APPS_ICON = 11
        const val FONT_FOLDER_ICON = 12
        const val FONT_ACTION_VIEW = 13
        const val FONT_DEEP_SHORTCUT = 14
        const val FONT_SYSTEM_SHORTCUT = 15
        const val FONT_TASK_OPTION = 16
        const val FONT_DRAWER_TAB = 17
        const val FONT_DRAWER_FOLDER = 18

        const val VARIANT_MEDIUM = "500"
    }
}
