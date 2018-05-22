package ch.deletescape.lawnchair.iconpack

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.UserHandle
import android.text.TextUtils
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.override.AppInfoProvider
import ch.deletescape.lawnchair.override.CustomInfoProvider
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LooperExecutor
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter
import java.util.*
import kotlin.collections.HashMap

class IconPackManager(private val context: Context) {

    val prefs = LawnchairPreferences.getInstance(context)
    val appInfoProvider = AppInfoProvider.getInstance(context)
    val defaultPack = DefaultPack(context)
    val iconPacks = HashMap<String, IconPack>().apply { put("", defaultPack) }
    val updateReceivers = HashMap<IconPack, BroadcastReceiver>()
    var currentPack = getIconPack(prefs.iconPack)
    var dayOfMonth = 0
        set(value) {
            if (value != field) {
                field = value
                onDateChanged()
            }
        }

    init {
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            }
        }, IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            if (!Utilities.ATLEAST_NOUGAT) {
                addAction(Intent.ACTION_TIME_TICK)
            }
        }, null, Handler(LauncherModel.getWorkerLooper()))
        prefs.addOnPreferenceChangeListener("pref_icon_pack", object : LawnchairPreferences.OnPreferenceChangeListener {
            override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
                if (force) return
                currentPack = getIconPack(prefs.iconPack)
            }
        })
    }

    fun onDateChanged() {
        iconPacks.values.forEach { it.onDateChanged() }
    }

    fun onPackChanged() {
        currentPack = getIconPack(prefs.iconPack)
        iconPacks.values.forEach { context.unregisterReceiver(updateReceivers[it]) }
        iconPacks.clear()
        updateReceivers.clear()
        if (currentPack != defaultPack) {
            iconPacks[currentPack.packPackageName] = currentPack
            registerReceiverForPack(currentPack)
        }
    }

    fun getIconPack(name: String, put: Boolean = true, load: Boolean = false): IconPack {
        if (name == defaultPack.packPackageName) return defaultPack
        return if (isPackProvider(context, name)) {
            if (put)
                iconPacks.getOrPut(name, { createPack(name) })
            else
                iconPacks.getOrElse(name, { createPack(name, false) })
        } else {
            iconPacks.remove(name)
            defaultPack
        }.apply {
            if (load) {
                ensureInitialLoadComplete()
            }
        }
    }

    private fun createPack(name: String, register: Boolean = true)
            = IconPackImpl(context, name).also { if (register) registerReceiverForPack(it) }

    private fun registerReceiverForPack(iconPack: IconPack) {
        updateReceivers.getOrPut(iconPack, {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    onPackUpdated()
                }
            }.also {
                context.registerReceiver(it, ActionIntentFilter.newInstance(iconPack.packPackageName,
                        Intent.ACTION_PACKAGE_CHANGED,
                        Intent.ACTION_PACKAGE_REPLACED,
                        Intent.ACTION_PACKAGE_FULLY_REMOVED))
            }
        })
    }

    fun getIcon(launcherActivityInfo: LauncherActivityInfo,
                iconDpi: Int, flattenDrawable: Boolean, itemInfo: ItemInfo?,
                iconProvider: LawnchairIconProvider?): Drawable {
        val customEntry = CustomInfoProvider.forItem<ItemInfo>(context, itemInfo)?.getIcon(itemInfo!!)
                ?: appInfoProvider.getCustomIconEntry(launcherActivityInfo)
        val pack = customEntry?.run { getIconPack(packPackageName) } ?: currentPack
        return pack.getIcon(launcherActivityInfo, iconDpi, flattenDrawable, customEntry, currentPack, iconProvider)
    }

    fun newIcon(icon: Bitmap, itemInfo: ItemInfo, drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable {
        val customEntry = CustomInfoProvider.forItem<ItemInfo>(context, itemInfo)?.getIcon(itemInfo)
        val pack = customEntry?.run { getIconPack(packPackageName) } ?: currentPack
        return pack.newIcon(icon, itemInfo, customEntry, currentPack, drawableFactory)
    }

    fun getEntryForComponent(component: ComponentKey): IconPack.Entry {
        return currentPack.getEntryForComponent(component) ?: defaultPack.getEntryForComponent(component)!!
    }

    fun getPackProviders(): Set<String> {
        val pm = context.packageManager
        val packs = HashSet<String>()
        ICON_INTENTS.forEach { intent -> pm.queryIntentActivities(Intent(intent), PackageManager.GET_META_DATA).forEach {
            packs.add(it.activityInfo.packageName)
        } }
        return packs
    }

    fun onPackUpdated() {
        LooperExecutor(LauncherModel.getIconPackLooper()).execute {
            val userManagerCompat = UserManagerCompat.getInstance(context)
            val model = LauncherAppState.getInstance(context).model

            for (user in userManagerCompat.userProfiles) {
                model.onPackagesReload(user)
            }

            IconPackManager.getInstance(context).onPackChanged()

            val shortcutManager = DeepShortcutManager.getInstance(context)
            val launcherApps = LauncherAppsCompat.getInstance(context)
            userManagerCompat.userProfiles.forEach { user ->
                launcherApps.getActivityList(null, user).forEach { reloadIcon(shortcutManager, model, user, it.componentName.packageName) }
            }
        }
    }

    private fun reloadIcon(shortcutManager: DeepShortcutManager, model: LauncherModel, user: UserHandle, pkg: String) {
        model.onPackageChanged(pkg, user)
        val shortcuts = shortcutManager.queryForPinnedShortcuts(pkg, user)
        if (!shortcuts.isEmpty()) {
            model.updatePinnedShortcuts(pkg, shortcuts, user)
        }
    }

    data class CustomIconEntry(val packPackageName: String, val icon: String? = null) {

        fun toPackString(): String {
            return "$packPackageName/"
        }

        override fun toString(): String {
            return "$packPackageName/${icon ?: ""}"
        }

        companion object {
            fun fromString(string: String): CustomIconEntry {
                return fromNullableString(string)!!
            }

            fun fromNullableString(string: String?): CustomIconEntry? {
                if (string == null) return null
                val parts = string.split("/")
                val icon = TextUtils.join("/", parts.subList(1, parts.size))
                return CustomIconEntry(parts[0], if (TextUtils.isEmpty(icon)) null else icon)
            }
        }
    }

    companion object {

        const val TAG = "IconPackManager"

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: IconPackManager? = null

        fun getInstance(context: Context): IconPackManager {
            if (INSTANCE == null) {
                INSTANCE = IconPackManager(context.applicationContext)
            }
            return INSTANCE!!
        }

        private val ICON_INTENTS = arrayOf(
                "com.fede.launcher.THEME_ICONPACK",
                "com.anddoes.launcher.THEME",
                "com.teslacoilsw.launcher.THEME",
                "com.gau.go.launcherex.theme",
                "org.adw.launcher.THEMES",
                "org.adw.launcher.icons.ACTION_PICK_ICON")

        internal fun isPackProvider(context: Context, packageName: String?): Boolean {
            if (packageName != null && !packageName.isEmpty()) {
                return ICON_INTENTS.firstOrNull { context.packageManager.queryIntentActivities(
                        Intent(it).setPackage(packageName), PackageManager.GET_META_DATA).iterator().hasNext() } != null
            }
            return false
        }
    }
}
