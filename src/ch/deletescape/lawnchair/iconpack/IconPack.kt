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

    fun executeLoadPack() {
        LooperExecutor(LauncherModel.getIconPackLooper()).execute({
            loadPack()
            waiter?.release()
        })
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

    open fun getAllIcons(): List<Category> {
        ensureInitialLoadComplete()
        return categorize(entries)
    }

    protected fun categorize(entries: List<Entry>): List<Category> {
        val categories = ArrayList<Category>()
        var category: Category? = null
        var previousSection = ""
        entries.sortedBy { it.displayName }.forEach {
            val currentSection = indexCompat.computeSectionName(it.displayName)
            if (currentSection != previousSection) {
                previousSection = currentSection
                category = Category(currentSection)
                categories.add(category!!)
            }
            category!!.icons.add(it)
        }
        return categories
    }

    abstract val entries: List<Entry>

    abstract class Entry {

        abstract val displayName: String
        abstract val identifierName: String
        abstract val drawable: Drawable

        abstract fun toCustomEntry(): IconPackManager.CustomIconEntry
    }

    class Category(val title: String) {

        val icons = ArrayList<Entry>()
    }
}
