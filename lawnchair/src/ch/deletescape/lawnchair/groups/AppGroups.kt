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

package ch.deletescape.lawnchair.groups

import android.content.Context
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.asMap
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.LawnchairAccentResolver
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.util.ComponentKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

typealias GroupCreator<T> = (Context) -> T?

abstract class AppGroups<T : AppGroups.Group>(prefs: LawnchairPreferences, key: String) {

    private val context = prefs.context

    private var groupsDataJson by prefs.StringPref(key, "[]", prefs.withChangeCallback {
        it.launcher.allAppsController.appsView.reloadTabs()
    })
    private val groups = ArrayList<T>()

    init {
        loadGroups()
    }

    private fun loadGroupsArray(): JSONArray {
        try {
            val obj = JSONObject(groupsDataJson)
            val version = if (obj.has(KEY_VERSION)) obj.getInt(KEY_VERSION) else 0
            if (version > currentVersion) return JSONArray()
            return obj.getJSONArray(KEY_GROUPS)
        } catch (ignored: JSONException) {
        }

        try {
            return JSONArray(groupsDataJson)
        } catch (ignored: JSONException) {
        }

        return JSONArray()
    }

    private fun loadGroups() {
        groups.clear()
        val arr = loadGroupsArray()
        val used = mutableSetOf<GroupCreator<T>>()
        (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .mapNotNullTo(groups) { group ->
                    val type = if (group.has(KEY_TYPE)) group.getInt(KEY_TYPE) else TYPE_UNDEFINED
                    val creator = getGroupCreator(type)
                    used.add(creator)
                    creator(context)?.apply { loadCustomizations(context, group.asMap()) }
                }
        getDefaultGroups().asReversed().forEach { creator ->
            if (creator !in used) {
                creator(context)?.let { groups.add(0, it) }
            }
        }
    }

    abstract fun getDefaultGroups(): List<GroupCreator<T>>

    abstract fun getGroupCreator(type: Int): GroupCreator<T>

    @Suppress("UNUSED_PARAMETER")
    protected fun createNull(context: Context) = null

    fun getGroups(): List<T> {
        return groups
    }

    fun setGroups(groups: List<T>) {
        this.groups.clear()
        this.groups.addAll(groups)
    }

    fun saveToJson() {
        val arr = JSONArray()
        groups.forEach { group ->
            arr.put(JSONObject(group.saveCustomizationsInternal()))
        }

        val obj = JSONObject()
        obj.put(KEY_VERSION, currentVersion)
        obj.put(KEY_GROUPS, arr)
        groupsDataJson = obj.toString()
    }

    companion object {

        const val currentVersion = 1

        const val KEY_VERSION = "version"
        const val KEY_GROUPS = "tabs"

        const val KEY_TYPE = "type"
        const val KEY_COLOR = "color"
        const val KEY_TITLE = "title"
        const val KEY_ITEMS = "items"
        const val KEY_HIDE_FROM_ALL_APPS = "hideFromAllApps"

        const val TYPE_UNDEFINED = -1
    }

    open class Group(private val type: Int) {

        open val title: String = "null"

        private val customizations: MutableMap<String, Customization<*, *>> = HashMap()

        fun addCustomization(key: String, customization: Customization<*, *>) {
            customizations[key] = customization
        }

        open fun loadCustomizations(context: Context, obj: Map<String, Any>) {
            customizations.entries.forEach { it.value.loadFromJsonInternal(context, obj[it.key]) }
        }

        fun saveCustomizationsInternal(): Map<String, Any> {
            val obj = HashMap<String, Any>()
            saveCustomizations(obj)
            return obj
        }

        open fun saveCustomizations(obj: MutableMap<String, Any>) {
            obj[KEY_TYPE] = type
            customizations.entries.forEach { entry ->
                entry.value.saveToJson()?.let { obj[entry.key] = it }
            }
        }

        fun cloneCustomizations(): Map<String, Customization<*, *>> {
            return customizations.mapValues { it.value.clone() }
        }

        abstract class Customization<T: Any, S: Any>(protected val default: T) {

            var value: T? = null

            fun value() = value ?: default

            @Suppress("UNCHECKED_CAST")
            fun loadFromJsonInternal(context: Context, obj: Any?) {
                loadFromJson(context, obj as S?)
            }

            abstract fun loadFromJson(context: Context, obj: S?)

            abstract fun saveToJson(): S?

            abstract fun clone(): Customization<T, S>
        }

        class StringCustomization(default: String) : Customization<String, String>(default) {

            override fun loadFromJson(context: Context, obj: String?) {
                value = obj
            }

            override fun saveToJson(): String? {
                return value
            }

            override fun clone(): Customization<String, String> {
                return StringCustomization(default).also { it.value = value }
            }
        }

        class BooleanCustomization(default: Boolean) : Customization<Boolean, Boolean>(default) {

            override fun loadFromJson(context: Context, obj: Boolean?) {
                value = obj
            }

            override fun saveToJson(): Boolean? {
                return value
            }

            override fun clone(): Customization<Boolean, Boolean> {
                return BooleanCustomization(default).also { it.value = value }
            }
        }

        class ColorCustomization(default: ColorEngine.ColorResolver): Customization<ColorEngine.ColorResolver, String>(default) {

            override fun loadFromJson(context: Context, obj: String?) {
                value = obj?.let { AppGroupsUtils.getInstance(context).createColorResolver(it) }
            }

            override fun saveToJson(): String? {
                return if (value is LawnchairAccentResolver) null else value.toString()
            }

            override fun clone(): Customization<ColorEngine.ColorResolver, String> {
                return ColorCustomization(default).also { it.value = value }
            }
        }

        abstract class SetCustomization<T: Any, S: Any>(default: MutableSet<T>) : Customization<MutableSet<T>, JSONArray>(default) {

            @Suppress("UNCHECKED_CAST")
            override fun loadFromJson(context: Context, obj: JSONArray?) {
                if (obj == null) {
                    value = null
                } else {
                    val set = HashSet<T>()
                    for (i in (0 until obj.length())) {
                        set.add(unflatten(context, obj.get(i) as S))
                    }
                    value = set
                }
            }

            override fun saveToJson(): JSONArray? {
                val list = value ?: return null
                val array = JSONArray()
                list.forEach { array.put(flatten(it)) }
                return array
            }

            abstract fun unflatten(context: Context, value: S): T

            abstract fun flatten(value: T): S
        }

        class ItemsCustomization(default: MutableSet<ComponentKey>) : SetCustomization<ComponentKey, String>(default) {

            override fun loadFromJson(context: Context, obj: JSONArray?) {
                super.loadFromJson(context, obj)
                if (value == null) {
                    value = HashSet(default)
                }
            }

            override fun unflatten(context: Context, value: String): ComponentKey {
                return ComponentKey(context, value)
            }

            override fun flatten(value: ComponentKey): String {
                return value.toString()
            }

            override fun clone(): Customization<MutableSet<ComponentKey>, JSONArray> {
                return ItemsCustomization(default).also { newInstance ->
                    value?.let { newInstance.value = it }
                }
            }
        }
    }
}

class AppGroupsUtils(context: Context) {

    private val colorEngine = ColorEngine.getInstance(context)
    val defaultColorResolver = LawnchairAccentResolver(
            ColorEngine.ColorResolver.Config("groups", colorEngine))

    fun createColorResolver(resolver: String?): ColorEngine.ColorResolver {
        return colorEngine.createColorResolverNullable("group", resolver ?: "")
                ?: defaultColorResolver
    }

    companion object : SingletonHolder<AppGroupsUtils, Context>(
            ensureOnMainThread(useApplicationContext(::AppGroupsUtils)))
}
