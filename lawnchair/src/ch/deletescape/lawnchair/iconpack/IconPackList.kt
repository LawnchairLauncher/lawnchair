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

package ch.deletescape.lawnchair.iconpack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.TextUtils
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.LooperExecutor
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter

class IconPackList(private val context: Context, private val manager: IconPackManager) {

    private val prefs = Utilities.getLawnchairPrefs(context)

    private val loadedPacks = HashMap<String, LoadedPack>()
    val appliedPacks = ArrayList<IconPack>()

    private val default by lazy { DefaultLoadedPack() }

    init {
        reloadPacks()
    }

    private fun onPackListUpdated(packs: List<String>) {
        LooperExecutor(LauncherModel.getIconPackLooper()).execute {
            loadedPacks.values.forEach {
                if (!packs.contains(it.packageName)) {
                    it.unregister()
                }
            }

            appliedPacks.clear()

            val newPacks = HashMap<String, LoadedPack>()
            packs.forEach { pack ->
                val loadedPack = loadedPacks.getOrPut(pack) {
                    loadPack(pack).apply {
                        iconPack.ensureInitialLoadComplete()
                        register()
                    }
                }
                newPacks[pack] = loadedPack
                appliedPacks.add(loadedPack.iconPack)
            }

            loadedPacks.clear()
            loadedPacks.putAll(newPacks)

            manager.onPacksUpdated()
        }
    }

    private fun loadPack(packageName: String) = if (!TextUtils.isEmpty(packageName))
        LoadedPackImpl(packageName) else default

    fun getPack(packageName: String, keep: Boolean): IconPack {
        if (keep) {
            return loadedPacks.getOrPut(packageName) {
                loadPack(packageName).apply { register() }
            }.iconPack
        }
        loadedPacks[packageName]?.let { return it.iconPack }
        return IconPackImpl(context, packageName)
    }

    fun onDateChanged() {
        loadedPacks.values.forEach { it.iconPack.onDateChanged() }
    }

    fun reloadPacks() {
        setPackList(prefs.iconPacks.getList())
    }

    private fun setPackList(packs: List<String>) {
        onPackListUpdated(packs.filter { IconPackManager.isPackProvider(context, it) })
    }

    fun iterator() = appliedPacks.iterator()

    fun currentPack() = if (!appliedPacks.isEmpty()) appliedPacks[0] else default.iconPack

    fun getAvailablePacks(): MutableSet<PackInfo> {
        val pm = context.packageManager
        val packs = HashSet<PackInfo>()
        IconPackManager.ICON_INTENTS.forEach { intent ->
            pm.queryIntentActivities(Intent(intent), 0)
                    .mapTo(packs) { PackInfo.forPackage(context, it.activityInfo.packageName) }
        }
        return packs
    }

    abstract class PackInfo(val context: Context, val packageName: String) : Comparable<PackInfo> {

        abstract val displayName: String
        abstract val displayIcon: Drawable

        abstract fun load() : IconPack

        override fun equals(other: Any?): Boolean {
            return other is PackInfo && packageName == other.packageName
        }

        override fun hashCode(): Int {
            return packageName.hashCode()
        }

        override fun compareTo(other: PackInfo): Int {
            return displayName.compareTo(other.displayName)
        }

        companion object {

            fun forPackage(context: Context, packageName: String): PackInfo {
                if (TextUtils.isEmpty(packageName)) return DefaultPackInfo(context)
                return PackInfoImpl(context, packageName)
            }
        }
    }

    class DefaultPackInfo(context: Context) : PackInfo(context, "") {

        override val displayIcon by lazy { context.getDrawable(R.mipmap.ic_launcher)!! }
        override val displayName by lazy { context.resources.getString(R.string.icon_pack_default)!! }

        override fun load() = IconPackManager.getInstance(context).defaultPack
    }

    class PackInfoImpl(context: Context, packageName: String) : PackInfo(context, packageName) {

        private val applicationInfo by lazy {
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }

        override val displayIcon by lazy {
            context.packageManager.getApplicationIcon(applicationInfo)!!
        }

        override val displayName by lazy {
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        }

        override fun load() = IconPackImpl(context, packageName)
    }

    abstract inner class LoadedPack(protected var pack: IconPack) {

        val packageName get() = pack.packPackageName
        val iconPack get() = pack

        open fun register() {

        }

        open fun unregister() {

        }

        open fun reloadPack() {
            pack = IconPackImpl(context, packageName)
        }
    }

    inner class LoadedPackImpl(pack: IconPack) : LoadedPack(pack) {

        constructor(packageName: String) : this(IconPackImpl(context, packageName))

        private val updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_CHANGED) {
                    reloadPack()
                    iconPack.ensureInitialLoadComplete()
                    manager.onPacksUpdated()
                } else {
                    reloadPacks()
                }
            }
        }

        override fun register() {
            super.register()

            context.registerReceiver(updateReceiver, ActionIntentFilter.newInstance(packageName,
                    Intent.ACTION_PACKAGE_CHANGED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_FULLY_REMOVED))
        }

        override fun unregister() {
            super.unregister()

            context.unregisterReceiver(updateReceiver)
        }

        override fun reloadPack() {
            super.reloadPack()

            pack = IconPackImpl(context, packageName)
        }
    }

    inner class DefaultLoadedPack : LoadedPack(manager.defaultPack) {

        override fun register() {

        }

        override fun unregister() {

        }

        override fun reloadPack() {

        }
    }
}
