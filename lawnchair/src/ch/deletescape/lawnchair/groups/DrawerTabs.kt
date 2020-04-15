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
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.LawnchairPreferencesChangeCallback
import ch.deletescape.lawnchair.groups.FlowerpotTabs.FlowerpotTab
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.preferences.SelectableAppsActivity
import ch.deletescape.lawnchair.tintDrawable
import ch.deletescape.lawnchair.util.extensions.e
import com.android.launcher3.R
import com.android.launcher3.Utilities.makeComponentKey
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.ItemInfoMatcher
import org.json.JSONObject

abstract class DrawerTabs(manager: AppGroupsManager, type: AppGroupsManager.CategorizationType)
    : AppGroups<DrawerTabs.Tab>(manager, type) {

    private val personalTabCreator = ProfileTabCreator(Profile(null))
    private val workTabCreator by lazy {
        val workUser = UserManagerCompat.getInstance(context).userProfiles.firstOrNull { it != Process.myUserHandle() }
        if (workUser != null) ProfileTabCreator(Profile(workUser)) else GroupCreator<Tab> { null }
    }
    private val allAppsTabCreator = ProfileTabCreator(Profile())
    private val customTabCreator = GroupCreator<Tab> { context -> CustomTab(context) }
    private val flowerpotTabCreator = GroupCreator<Tab> { context -> FlowerpotTab(context) }

    init {
        loadGroups()
    }

    override fun getDefaultCreators(): List<GroupCreator<Tab>> {
        return listOf(allAppsTabCreator) + personalTabCreator + UserManagerCompat.getInstance(context)
                .userProfiles.mapNotNull {
            if (it != Process.myUserHandle()) ProfileTabCreator(Profile(it)) else null
        }
    }

    override fun getGroupCreator(type: String): GroupCreator<Tab> {
        if (type.startsWith(TYPE_PROFILE_PREFIX)) {
            val profile = Profile.fromString(context, type.substring(TYPE_PROFILE_PREFIX.length))
                          ?: return GroupCreator { null }
            return ProfileTabCreator(profile)
        }
        return when (type) {
            TYPE_CUSTOM, TYPE_UNDEFINED -> customTabCreator
            TYPE_PERSONAL -> personalTabCreator
            TYPE_WORK -> workTabCreator
            TYPE_ALL_APPS -> allAppsTabCreator
            FlowerpotTabs.TYPE_FLOWERPOT -> flowerpotTabCreator
            else -> GroupCreator { null }
        }
    }

    override fun onGroupsChanged(changeCallback: LawnchairPreferencesChangeCallback) {
        changeCallback.launcher.allAppsController.appsView.reloadTabs()
    }

    abstract class Tab(context: Context, type: String, title: String) : Group(type, context, title) {

        val colorResolver = ColorRow(KEY_COLOR, AppGroupsUtils.getInstance(context).defaultColorResolver)

        init {
            addCustomization(colorResolver)
        }
    }

    class CustomTab(context: Context) : Tab(context, TYPE_CUSTOM, context.getString(R.string.default_tab_name)) {

        val hideFromAllApps = SwitchRow(R.drawable.tab_hide_from_main, R.string.tab_hide_from_main,
                KEY_HIDE_FROM_ALL_APPS, true)
        val contents = AppsRow(KEY_ITEMS, mutableSetOf())

        init {
            addCustomization(hideFromAllApps)
            addCustomization(contents)

            customizations.setOrder(KEY_TITLE, KEY_HIDE_FROM_ALL_APPS, KEY_COLOR, KEY_ITEMS)
        }

        override fun getSummary(context: Context): String? {
            val size = getFilter(context).size
            return context.resources.getQuantityString(R.plurals.tab_apps_count, size, size)
        }

        fun getFilter(context: Context): Filter<*> = CustomFilter(context, contents.value()) // IconPackFilter(context)
    }

    open class ProfileTab(context: Context, val profile: Profile) : Tab(context, "$TYPE_PROFILE_PREFIX$profile}", getTitle(context, profile)) {

        init {
            addCustomization(HiddenAppsRow(profile))
            customizations.setOrder(KEY_TITLE, KEY_COLOR, KEY_HIDDEN)
        }

        override fun getSummary(context: Context): String? {
            val hidden = context.lawnchairPrefs.hiddenAppSet
                    .map { makeComponentKey(context, it) }
                    .filter(getWorkFilter(profile))
            val size = hidden.size
            if (size == 0) {
                return null
            }
            return context.resources.getQuantityString(R.plurals.hidden_apps_count, size, size)
        }

        companion object {

            private fun getTitle(context: Context, profile: Profile): String {
                if (profile.matchesAll) return context.getString(R.string.apps_label)
                if (profile.isWork) return context.getString(R.string.all_apps_work_tab)
                return context.getString(R.string.all_apps_personal_tab)
            }
        }
    }

    class HiddenAppsRow(private val profile: Profile) :
            Group.Customization<Collection<ComponentKey>, Boolean>(KEY_HIDDEN, emptySet()) {

        private val predicate get() = getWorkFilter(profile)

        override fun createRow(context: Context, parent: ViewGroup, accent: Int): View? {
            val view = LayoutInflater.from(context).inflate(R.layout.drawer_tab_hidden_apps_row, parent, false)

            view.findViewById<ImageView>(R.id.manage_apps_icon).tintDrawable(accent)
            updateCount(view)

            view.setOnClickListener {
                SelectableAppsActivity.start(context, filteredValue(context), { newSelections ->
                    if (newSelections != null) {
                        value = HashSet(newSelections)
                        updateCount(view)
                    }
                }, profile)
            }

            return view
        }

        override fun loadFromJson(context: Context, obj: Boolean?) {

        }

        override fun saveToJson(context: Context): Boolean? {
            val value = value ?: return null
            setHiddenApps(context, value)
            this.value = null
            return null
        }

        private fun updateCount(view: View) {
            val count = (value ?: filteredValue(view.context)).size
            view.findViewById<TextView>(R.id.apps_count).text =
                    view.resources.getQuantityString(R.plurals.hidden_apps_count, count, count)
        }

        private fun filteredValue(context: Context): Collection<ComponentKey> {
            return context.lawnchairPrefs.hiddenAppSet
                    .map { makeComponentKey(context, it) }
                    .filter(predicate)
        }

        private fun setHiddenApps(context: Context, hidden: Collection<ComponentKey>) {
            val prefs = context.lawnchairPrefs
            val hiddenSet = ArrayList(prefs.hiddenAppSet
                    .map { makeComponentKey(context, it) }
                    .filter { !predicate(it) })
            hiddenSet.addAll(hidden)
            prefs.hiddenAppSet = hiddenSet.map(ComponentKey::toString).toSet()
        }

        override fun clone(): Group.Customization<Collection<ComponentKey>, Boolean> {
            return HiddenAppsRow(profile).also { it.value = value }
        }
    }

    companion object {

        const val TYPE_PERSONAL = "0"
        const val TYPE_WORK = "1"
        const val TYPE_CUSTOM = "2"
        const val TYPE_ALL_APPS = "3"
        const val TYPE_PROFILE_PREFIX = "profile"

        const val KEY_ITEMS = "items"
        const val KEY_HIDDEN = "hidden"

        fun getWorkFilter(profile: Profile): (ComponentKey) -> Boolean {
            return profile::filter
        }
    }

    data class Profile(val user: UserHandle?, val matchesAll: Boolean = false) : Parcelable {

        val isWork = user != null && user != Process.myUserHandle()
        val matcher: ItemInfoMatcher? = if (user != null) {
            ItemInfoMatcher.ofUser(user)
        } else if (!matchesAll) {
            ItemInfoMatcher.ofUser(Process.myUserHandle())
        } else {
            null
        }

        constructor() : this(null, true)

        constructor(parcel: Parcel) : this(
                parcel.readParcelable<UserHandle?>(UserHandle::class.java.classLoader),
                parcel.readBoolean())

        fun matches(user: UserHandle): Boolean {
            if (matchesAll) return true
            return if (this.user == null) {
                user == Process.myUserHandle()
            } else {
                user == this.user
            }
        }

        fun filter(key: ComponentKey): Boolean {
            return matches(key.user)
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(user, flags)
            dest.writeBoolean(matchesAll)
        }

        override fun describeContents() = 0

        override fun toString(): String {
            val obj = JSONObject()
            if (user != null) {
                obj.put(KEY_ID, user.toString().replace("\\D+".toRegex(), "").toLong())
            }
            obj.put(KEY_MATCHES_ALL, matchesAll)
            return obj.toString()
        }

        companion object {
            private const val KEY_ID = "id"
            private const val KEY_MATCHES_ALL = "matchesAll"

            @JvmField
            val CREATOR = object : Parcelable.Creator<Profile> {
                override fun createFromParcel(parcel: Parcel): Profile {
                    return Profile(parcel)
                }

                override fun newArray(size: Int): Array<Profile?> {
                    return arrayOfNulls(size)
                }
            }

            fun fromString(context: Context, profile: String): Profile? {
                val obj = JSONObject(profile)
                val user = if (obj.has(KEY_ID)) {
                    UserManagerCompat.getInstance(context)
                            .getUserForSerialNumber(obj.getLong(KEY_ID)) ?: return null
                } else null
                val matchesAll = obj.getBoolean(KEY_MATCHES_ALL)
                return Profile(user, matchesAll)
            }
        }
    }

    data class ProfileTabCreator(private val profile: Profile) : GroupCreator<Tab> {

        override fun createGroup(context: Context): Tab? {
            return ProfileTab(context, profile)
        }
    }
}
