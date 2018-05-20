package ch.deletescape.lawnchair.iconpack

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.TextUtils
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.override.AppInfoProvider
import com.android.launcher3.LauncherModel
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import java.util.*

class IconPackManager(private val context: Context) {

    val prefs = LawnchairPreferences.getInstance(context)
    val appInfoProvider = AppInfoProvider.getInstance(context)
    val defaultPack = DefaultPack(context)
    val iconPacks = HashMap<String, IconPack>().apply { put("", defaultPack) }
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

    fun getIconPack(name: String, put: Boolean = true, load: Boolean = false): IconPack {
        return if (isPackProvider(context, name)) {
            if (put)
                iconPacks.getOrPut(name, { IconPackImpl(context, name) })
            else
                iconPacks.getOrElse(name, { IconPackImpl(context, name) })
        } else {
            iconPacks.remove(name)
            defaultPack
        }.apply {
            if (load) {
                ensureInitialLoadComplete()
            }
        }
    }

    fun getIcon(launcherActivityInfo: LauncherActivityInfo,
                iconDpi: Int, flattenDrawable: Boolean,
                iconProvider: LawnchairIconProvider?): Drawable {
        val customEntry = appInfoProvider.getCustomIconEntry(launcherActivityInfo)
        val pack = customEntry?.run { getIconPack(packPackageName) } ?: currentPack
        return pack.getIcon(launcherActivityInfo, iconDpi, flattenDrawable, customEntry, currentPack, iconProvider)
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

    data class CustomIconEntry(val packPackageName: String, val icon: String? = null) {

        fun toPackString(): String {
            return "$packPackageName/"
        }

        override fun toString(): String {
            return "$packPackageName/${icon ?: ""}"
        }

        companion object {
            fun fromString(string: String): CustomIconEntry {
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
