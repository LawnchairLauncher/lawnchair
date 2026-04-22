package app.lawnchair.util

import com.android.launcher3.util.SafeCloseable

class MultiSafeCloseable : SafeCloseable {

    private val closeables = mutableListOf<SafeCloseable>()

    fun add(safeCloseable: SafeCloseable) {
        closeables.add(safeCloseable)
    }

    override fun close() {
        closeables.forEach { it.close() }
    }
}
