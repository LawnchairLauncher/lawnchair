package app.lawnchair.util

import java.util.*

val <T> Optional<T>.value: T? get() {
    if (!isPresent) return null
    return get()
}
