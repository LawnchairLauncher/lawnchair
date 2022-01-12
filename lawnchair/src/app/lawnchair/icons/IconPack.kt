package app.lawnchair.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Semaphore

abstract class IconPack(
    protected val context: Context,
    val packPackageName: String,
) {
    private var waiter: Semaphore? = Semaphore(0)
    private val deferredLoad: Deferred<Unit>

    abstract val label: String

    init {
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
    abstract fun getCalendar(componentName: ComponentName): CalendarIconEntry?
    abstract fun getClock(entry: IconEntry): ClockMetadata?

    abstract fun getCalendars(): MutableSet<ComponentName>
    abstract fun getClocks(): MutableSet<ComponentName>

    abstract fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable?

    abstract fun getAllIcons(): Flow<List<IconPickerCategory>>

    @Suppress("BlockingMethodInNonBlockingContext")
    protected abstract fun loadInternal()

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("IconPack")
    }
}
