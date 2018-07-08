package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherModel
import com.android.launcher3.compat.AlphabeticIndexCompat
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LooperExecutor
import java.util.concurrent.Semaphore

abstract class IconPack(val context: Context, val packPackageName: String) {
    private var waiter: Semaphore? = Semaphore(0)
    private val indexCompat = AlphabeticIndexCompat(context)
    private val loadCompleteListeners = ArrayList<(IconPack) -> Unit>()

    fun executeLoadPack() {
        LooperExecutor(LauncherModel.getIconPackLooper()).execute({
            loadPack()
            waiter?.release()
            loadCompleteListeners.forEach { it.invoke(this) }
            loadCompleteListeners.clear()
        })
    }

    fun addOnLoadCompleteListener(listener: (IconPack) -> Unit) {
        if (waiter != null) loadCompleteListeners.add(listener)
        else listener.invoke(this)
    }

    @Synchronized
    fun ensureInitialLoadComplete() {
        waiter?.run {
            acquireUninterruptibly()
            release()
            waiter = null
        }
    }

    abstract val displayIcon: Drawable
    abstract val displayName: String

    abstract fun onDateChanged()

    abstract fun loadPack()

    abstract fun getEntryForComponent(key: ComponentKey): Entry?

    abstract fun getIcon(launcherActivityInfo: LauncherActivityInfo,
                         iconDpi: Int, flattenDrawable: Boolean, customIconEntry: IconPackManager.CustomIconEntry?,
                         basePack: IconPack, iconProvider: LawnchairIconProvider?): Drawable

    abstract fun newIcon(icon: Bitmap, itemInfo: ItemInfo, customIconEntry: IconPackManager.CustomIconEntry?,
                         basePack: IconPack, drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable

    open fun getAllIcons(callback: (List<PackEntry>) -> Unit, cancel: () -> Boolean) {
        ensureInitialLoadComplete()
        callback(categorize(entries))
    }

    protected fun categorize(entries: List<Entry>): List<PackEntry> {
        val packEntries = ArrayList<PackEntry>()
        var previousSection = ""
        entries.sortedBy { it.displayName }.forEach {
            val currentSection = indexCompat.computeSectionName(it.displayName)
            if (currentSection != previousSection) {
                previousSection = currentSection
                packEntries.add(CategoryTitle(currentSection))
            }
            packEntries.add(it)
        }
        return packEntries
    }

    abstract val entries: List<Entry>

    open class PackEntry

    class CategoryTitle(val title: String) : PackEntry()

    abstract class Entry : PackEntry() {

        abstract val displayName: String
        abstract val identifierName: String
        abstract val drawable: Drawable

        abstract fun toCustomEntry(): IconPackManager.CustomIconEntry
    }
}
