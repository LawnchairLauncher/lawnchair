package app.lawnchair.util

import android.content.Context
import androidx.core.content.getSystemService

inline fun <reified T : Any> Context.requireSystemService(): T = checkNotNull(getSystemService())

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> unsafeLazy(noinline initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)
