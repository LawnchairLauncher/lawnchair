package app.lawnchair.omni.experience

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.patrykmichalik.opto.core.firstBlocking
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One event-driven inventory shared by Shelf, rules, and OmniLink. Never queried from drawing. */
class OmniAppInventory private constructor(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    val revision = MutableStateFlow(0L)

    private val loadMutex = Mutex()

    @Volatile private var cached: List<AppInfo>? = null

    @Volatile private var cachedLocale: Locale? = null

    @Volatile private var categories: Map<String, Int> = emptyMap()
    fun category(app: AppInfo): Int = categories[app.toComponentKey().toString()] ?: -1
    private val worker = HandlerThread("OmniInventory").apply { start() }
    private val callback = object : LauncherApps.Callback() {
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) = invalidate()
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidate()
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = invalidate()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = invalidate()
    }
    init {
        launcherApps?.registerCallback(callback, Handler(worker.looper))
    }
    private fun invalidate() {
        cached = null
        revision.value += 1
    }

    suspend fun visible(): List<AppInfo> = withContext(Dispatchers.IO) {
        val service = launcherApps ?: return@withContext emptyList()
        val before = revision.value
        val locale = context.resources.configuration.locales[0]
        val all = loadMutex.withLock {
            if (cachedLocale == locale) cached?.let { return@withLock it }
            val filter = AppFilter(context)
            val activities = service.profiles.flatMap { user ->
                runCatching { service.getActivityList(null, user) }.getOrDefault(emptyList())
            }.filter { filter.shouldShowApp(it.componentName) }
            // AppInfo's constructor does not populate title. Normal AllAppsList uses IconCache;
            // these text-only lists load labels on IO without decoding or retaining any icons.
            val collator = Collator.getInstance(locale)
            val loaded = activities.map { activity ->
                AppInfo(context, activity, activity.user).apply {
                    title = resolveAppLabel(runCatching { activity.label }.getOrNull(), activity.componentName.packageName)
                }
            }.sortedWith { first, second ->
                collator.compare(first.title.toString(), second.title.toString()).takeIf { it != 0 }
                    ?: first.toComponentKey().toString().compareTo(second.toComponentKey().toString())
            }
            categories = activities.associate { com.android.launcher3.util.ComponentKey(it.componentName, it.user).toString() to it.applicationInfo.category }
            if (revision.value == before) {
                cachedLocale = locale
                cached = loaded
            }
            loaded
        }
        val hidden = PreferenceManager2.getInstance(context).hiddenApps.firstBlocking()
        all.filter { it.toComponentKey().toString() !in hidden }
    }

    companion object {
        @Volatile private var instance: OmniAppInventory? = null
        fun get(context: Context): OmniAppInventory = instance ?: synchronized(this) {
            instance ?: OmniAppInventory(context.applicationContext).also { instance = it }
        }
    }
}
