package app.lawnchair.ui.preferences.views

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView
import app.lawnchair.font.FontCache
import app.lawnchair.ui.util.ViewPool
import app.lawnchair.util.runOnMainThread
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CustomFontTextView(context: Context) :
    AppCompatTextView(context),
    ViewPool.Recyclable {

    private var job: Job? = null

    fun setFont(font: FontCache.Font) {
        reset()
        val fontCache = FontCache.INSTANCE.get(context)
        @Suppress("EXPERIMENTAL_API_USAGE")
        typeface = fontCache.getLoadedFont(font)?.typeface
        job = scope.launch {
            val typeface = fontCache.getTypeface(font)
            runOnMainThread { setTypeface(typeface) }
        }
    }

    override fun onRecycled() {
        reset()
    }

    private fun reset() {
        job?.cancel()
        job = null
        typeface = null
    }

    companion object {
        private val scope = CoroutineScope(CoroutineName("CustomFontTextView"))
    }
}
