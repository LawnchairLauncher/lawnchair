package app.lawnchair.omni.experience.widgets

import app.lawnchair.omni.BoundedSystemCalls
import java.io.Closeable

internal class BoundedWidgetCalls(workerCount: Int = 2, queueCapacity: Int = 8) : Closeable {
    private val calls = BoundedSystemCalls(workerCount, queueCapacity, "OmniWidgetIO")
    suspend fun <T> call(timeoutMillis: Long = 2_500, discard: (T) -> Unit = {}, operation: () -> T): T = calls.call(timeoutMillis, discard, operation)
    override fun close() = calls.close()
    companion object {
        val shared by lazy { BoundedWidgetCalls() }
    }
}
