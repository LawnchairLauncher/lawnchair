package app.lawnchair.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import com.android.launcher3.compat.AlphabeticIndexCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Semaphore

sealed class IconPack(
    protected val context: Context,
    val packPackageName: String,
) {
    private var waiter: Semaphore? = Semaphore(0)
    private lateinit var deferredLoad: Deferred<Unit>

    abstract val label: String

    private val alphabeticIndexCompat by lazy { AlphabeticIndexCompat(context) }

    protected fun startLoad() {
        deferredLoad = scope.async(Dispatchers.IO) {
            loadInternal()
            waiter?.release()
            waiter = null
        }
    }

    suspend fun load() {
        return deferredLoad.await()
    }

    fun loadBlocking() {
        waiter?.run {
            acquireUninterruptibly()
            release()
        }
    }

    abstract fun getIcon(componentName: ComponentName): IconEntry?
    abstract fun getCalendar(componentName: ComponentName): IconEntry?
    abstract fun getClock(entry: IconEntry): ClockMetadata?

    abstract fun getCalendars(): MutableSet<ComponentName>
    abstract fun getClocks(): MutableSet<ComponentName>

    abstract fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable?

    abstract fun getAllIcons(): Flow<List<IconPickerCategory>>

    protected abstract fun loadInternal()

    protected fun removeDuplicates(items: List<IconPickerItem>): List<IconPickerItem> {
        var previous = ""
        val filtered = ArrayList<IconPickerItem>()
        items.sortedBy { it.drawableName }.forEach {
            if (it.drawableName != previous) {
                previous = it.drawableName
                filtered.add(it)
            }
        }
        return filtered
    }

    protected fun categorize(allItems: List<IconPickerItem>): List<IconPickerCategory> {
        return allItems
            .groupBy { alphabeticIndexCompat.computeSectionName(it.label) }
            .map { (sectionName, items) ->
                IconPickerCategory(
                    title = sectionName,
                    items = items
                )
            }
            .sortedBy { it.title }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("IconPack")
    }
}
