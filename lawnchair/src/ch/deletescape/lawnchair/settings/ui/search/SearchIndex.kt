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
import android.content.res.TypedArray

import android.support.v7.preference.PreferenceGroup
import android.util.AttributeSet
import android.util.Xml
import android.view.View
import ch.deletescape.lawnchair.settings.ui.PreferenceController
import ch.deletescape.lawnchair.settings.ui.SubPreference
import ch.deletescape.lawnchair.settings.ui.SwitchSubPreference
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.R
import com.android.launcher3.Utilities
import org.xmlpull.v1.XmlPullParser

class SearchIndex(private val context: Context) {

    companion object {
        private const val TAG = "SearchIndex"
    }

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
            val cls = try {
                Class.forName(parser.name)
            } catch (e: ClassNotFoundException) {
                null
            }
            val attrs = Xml.asAttributeSet(parser)
            val ta = context.obtainStyledAttributes(attrs, R.styleable.IndexablePreference)
            val indexable = ta.getBoolean(R.styleable.IndexablePreference_indexable, true)
            when {
                cls != null && SubPreference::class.java.isAssignableFrom(cls) -> {
                    val controller = createController(ta)
                    if (controller?.isVisible != false) {
                        val iconRes = ta.getResourceId(R.styleable.IndexablePreference_android_icon, 0)
                        val title = controller?.title ?: ta.getString(R.styleable.IndexablePreference_android_title)
                        val content = ta.getResourceId(R.styleable.IndexablePreference_content, 0)
                        val hasPreview = ta.getBoolean(R.styleable.IndexablePreference_hasPreview, false)
                        var canIndex = indexable
                        if (SwitchSubPreference::class.java.isAssignableFrom(cls)) {
                            val key = ta.getString(R.styleable.IndexablePreference_android_key)
                            val defaultValue = ta.getBoolean(R.styleable.IndexablePreference_android_defaultValue, false)
                            val summary = controller?.summary ?: ta.getString(R.styleable.IndexablePreference_android_summary)
                            if (parent != null && key != null) {
                                if (addedKeys.add(key)) {
                                    entries.add(SettingsEntry(iconRes, key, title, summary, parent))
                                }
                            }
                            canIndex = Utilities.getPrefs(context).getBoolean(key, defaultValue)
                        }
                        if (canIndex) {
                            indexScreen(content, SettingsScreen(title, title, findScreen(parent), content, hasPreview))
                        }
                    }
                    skip(parser)
                }
                cls != null && PreferenceGroup::class.java.isAssignableFrom(cls) -> {
                    val controller = createController(ta)
                    if (controller?.isVisible != false && indexable) {
                        val title = controller?.title ?: ta.getString(R.styleable.IndexablePreference_android_title)
                        if (parent != null) {
                            indexSection(parser, SettingsCategory(parent.title, title,
                                    parent, parent.contentRes, parent.hasPreview))
                        } else {
                            indexSection(parser, null)
                        }
                    } else {
                        skip(parser)
                    }
                }
                else -> {
                    val controller = createController(ta)
                    if (controller?.isVisible != false && indexable) {
                        val iconRes = ta.getResourceId(R.styleable.IndexablePreference_android_icon, 0)
                        val key = ta.getString(R.styleable.IndexablePreference_android_key)
                        val title = controller?.title ?: ta.getString(R.styleable.IndexablePreference_android_title)
                        val summary = controller?.summary ?: ta.getString(R.styleable.IndexablePreference_android_summary)
                        if (parent != null && key != null && title != null) {
                            if (addedKeys.add(key)) {
                                entries.add(SettingsEntry(iconRes, key, title, summary, parent, getSlice(cls, attrs)))
                            }
                        }
                    }

                    skip(parser)
                }
            }

            ta.recycle()
        }
    }

    private fun getSlice(cls: Class<*>?, attrs: AttributeSet): Slice? {
        if (cls == null) return null
        val provider: SliceProvider
        try {
            val providerField = cls.getDeclaredField("sliceProvider")
            providerField.isAccessible = true
            provider = providerField.get(null) as? SliceProvider ?: return null
        } catch (t: Throwable) {
            d("Couldn't get slice provider", t)
            return null
        }
        return provider.getSlice(context, attrs)
    }

    private fun createController(ta: TypedArray): PreferenceController? {
        val controllerClass = ta.getString(R.styleable.IndexablePreference_controllerClass)
        return PreferenceController.create(context, controllerClass)
    }

    private tailrec fun findScreen(screen: SettingsScreen?): SettingsScreen? {
        return if (screen is SettingsCategory)
            findScreen(screen.parent)
        else
            screen
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

    inner class SettingsCategory(title: String, categoryTitle: String?,
                                 parent: SettingsScreen?, contentRes: Int,
                                 hasPreview: Boolean)
        : SettingsScreen(title, categoryTitle, parent, contentRes, hasPreview)

    open inner class SettingsScreen(val title: String, private val categoryTitle: String?,
                                    val parent: SettingsScreen?,
                                    val contentRes: Int, val hasPreview: Boolean) {

        val breadcrumbs: String
            get() = when {
                parent == null -> categoryTitle ?: ""
                categoryTitle != null -> context.getString(R.string.search_breadcrumb_connector,
                        parent.breadcrumbs, categoryTitle)
                else -> parent.breadcrumbs
            }
    }

    inner class SettingsEntry(
            val iconRes: Int,
            val key: String,
            val title: String,
            val summary: String?,
            val parent: SettingsScreen?,
            private val slice: Slice? = null) {

        val breadcrumbs get() = parent?.breadcrumbs ?: ""

        fun getId(): Long {
            var id = title.hashCode().toLong() shl 32
            id += breadcrumbs.hashCode()
            return id
        }

        val sliceIsHorizontal by lazy { slice?.isHorizontal == true }

        fun getSliceView(): View? {
            return slice?.sliceView
        }
    }

    interface SliceProvider {
        fun getSlice(context: Context, attrs: AttributeSet): Slice?

        companion object {
            fun fromLambda(create: (Context, AttributeSet) -> Slice?): SliceProvider {
                return object : SliceProvider {
                    override fun getSlice(context: Context, attrs: AttributeSet): Slice? {
                        return create(context, attrs)
                    }
                }
            }
        }
    }

    abstract class Slice(val context: Context, attrs: AttributeSet) {

        val key: String

        // Adds the slice to a larger horizontal space instead of the usual small square
        // TODO: Use this to implement the very complex seekbar preferences.
        open val isHorizontal: Boolean = false

        val sliceView: View? by lazy { createSliceView() }

        init {
            val ta = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.key))
            key = ta.getString(0)!!
            ta.recycle()
        }

        abstract fun createSliceView(): View?
    }
}
