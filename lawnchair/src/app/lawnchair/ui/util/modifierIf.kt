package app.lawnchair.ui.util

import androidx.compose.ui.Modifier

inline fun Modifier.addIf(condition: Boolean, crossinline factory: Modifier.() -> Modifier): Modifier {
    return if (condition) factory(this) else this
}
