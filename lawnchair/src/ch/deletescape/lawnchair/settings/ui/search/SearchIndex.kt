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

package ch.deletescape.lawnchair.settings.ui.search

import android.content.Context
import ch.deletescape.lawnchair.preferences.StyledPreferenceCategory
import ch.deletescape.lawnchair.settings.ui.PreferenceController
import ch.deletescape.lawnchair.settings.ui.SubPreference
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.xmlpull.v1.XmlPullParser

class SearchIndex(private val context: Context) {

    private val TAG = "SearchIndex"

    private val nsAndroid = "http://schemas.android.com/apk/res/android"
    private val nsApp = "http://schemas.android.com/apk/res-auto"

    private val attrKey = "key"
    private val attrTitle = "title"
    private val attrSummary = "summary"
    private val attrContent = "content"
    private val attrHasPreview = "hasPreview"
    private val attrControllerClass = "controllerClass"
    private val attrSearchTitle = "searchTitle"

    val entries = ArrayList<SettingsEntry>()
    val addedKeys = HashSet<String>()

    init {
        indexScreen(R.xml.lawnchair_preferences, null)
    }

    private fun indexScreen(resourceId: Int, parent: SettingsScreen?) {
        val resources = context.resources
        val parser = resources.getXml(resourceId)
        parser.require(XmlPullParser.START_DOCUMENT, null, null)
        parser.next()
        parser.next()
        indexSection(parser, parent)
    }

    private fun indexSection(parser: XmlPullParser, parent: SettingsScreen?) {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when {
                parser.name == SubPreference::class.java.name -> {
                    val controller = createController(parser)
                    if (controller?.isVisible != false) {
                        val title = getTitle(parser, controller)
                        val content = parseIdentifier(parser.getAttributeValue(nsApp, attrContent))
                        val hasPreview = java.lang.Boolean.parseBoolean(parser.getAttributeValue(nsApp, attrHasPreview))
                        indexScreen(content, SettingsScreen(title!!, title, findScreen(parent), content, hasPreview))
                    }
                    skip(parser)
                }
                parser.name == StyledPreferenceCategory::class.java.name -> {
                    val title = parseString(parser.getAttributeValue(nsAndroid, attrTitle))
                    if (parent != null) {
                        indexSection(parser, SettingsCategory(parent.title, title!!,
                                parent, parent.contentRes, parent.hasPreview))
                    } else {
                        indexSection(parser, null)
                    }
                }
                else -> {
                    val controller = createController(parser)
                    if (controller?.isVisible != false) {
                        val key = parser.getAttributeValue(nsAndroid, attrKey)
                        val title = getTitle(parser, controller)
                        val summary = getSummary(parser, controller)
                        if (parent != null && key != null && title != null) {
                            if (addedKeys.add(key)) {
                                entries.add(SettingsEntry(key, title, summary, parent))
                            }
                        }
                    }

                    skip(parser)
                }
            }
        }
    }

    private fun getTitle(parser: XmlPullParser, controller: PreferenceController?): String? {
        return controller?.title ?:
            parseString(parser.getAttributeValue(nsApp, attrSearchTitle)) ?:
            parseString(parser.getAttributeValue(nsAndroid, attrTitle))
    }

    private fun getSummary(parser: XmlPullParser, controller: PreferenceController?): String? {
        return controller?.summary ?:
            parseString(parser.getAttributeValue(nsAndroid, attrSummary))
    }

    private fun createController(parser: XmlPullParser): PreferenceController? {
        val controllerClass = parseString(parser.getAttributeValue(nsApp, attrControllerClass))
        return PreferenceController.create(context, controllerClass)
    }

    private tailrec fun findScreen(screen: SettingsScreen?): SettingsScreen? {
        return if (screen is SettingsCategory)
            findScreen(screen.parent)
        else
            screen
    }

    private fun parseIdentifier(identifier: String): Int {
        return Utilities.parseResourceIdentifier(context.resources, identifier, context.packageName)
    }

    private fun parseString(identifier: String?): String? {
        if (identifier == null) return null

        val id = parseIdentifier(identifier)
        if (id == 0) return identifier
        return context.getString(id)
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    inner class SettingsCategory(title: String, categoryTitle: String,
                                 parent: SettingsScreen?, contentRes: Int,
                                 hasPreview: Boolean)
        : SettingsScreen(title, categoryTitle, parent, contentRes, hasPreview)

    open inner class SettingsScreen(val title: String, val categoryTitle: String,
                                    val parent: SettingsScreen?,
                                    val contentRes: Int, val hasPreview: Boolean) {

        val breadcrumbs: String get() {
            return if (parent == null) {
                categoryTitle
            } else {
                context.getString(R.string.search_breadcrumb_connector, parent.breadcrumbs, categoryTitle)
            }
        }
    }

    inner class SettingsEntry(val key: String, val title: String, val summary: String?, val parent: SettingsScreen?) {

        val breadcrumbs get() = parent?.breadcrumbs ?: ""

        fun getId(): Long {
            var id = title.hashCode().toLong() shl 32
            id += breadcrumbs.hashCode()
            return id
        }
    }
}
