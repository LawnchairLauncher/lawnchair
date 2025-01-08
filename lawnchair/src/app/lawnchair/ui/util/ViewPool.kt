package app.lawnchair.ui.util

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.recyclerview.widget.RecyclerView

class ViewPool<T>(
    private val context: Context,
    private val factory: (Context) -> T,
) : RecyclerView.RecycledViewPool() where T : View, T : ViewPool.Recyclable {

    private fun getOrCreateHolder(): RecyclerView.ViewHolder {
        return getRecycledView(RecyclerView.INVALID_TYPE) ?: ViewHolder(factory(context))
    }

    @Composable
    fun rememberView(): T {
        val observer = remember { getOrCreateHolder() }
        @Suppress("UNCHECKED_CAST")
        return observer.itemView as T
    }

    private inner class ViewHolder(private val view: T) :
        RecyclerView.ViewHolder(view),
        RememberObserver {
        override fun onRemembered() {
        }

        override fun onForgotten() {
            putRecycledView(this)
            view.onRecycled()
        }

        override fun onAbandoned() {
            putRecycledView(this)
            view.onRecycled()
        }
    }

    interface Recyclable {
        fun onRecycled()
    }
}

@Composable
fun <T> rememberViewPool(factory: (Context) -> T): ViewPool<T> where T : View, T : ViewPool.Recyclable {
    val context = LocalContext.current
    return remember(factory) {
        ViewPool(context, factory)
    }
}
