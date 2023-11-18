package app.lawnchair.util

import android.content.Context
import androidx.core.content.getSystemService

inline fun <reified T : Any> Context.requireSystemService(): T = checkNotNull(getSystemService())
