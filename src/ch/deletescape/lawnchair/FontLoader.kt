package ch.deletescape.lawnchair

import android.content.Context
import android.graphics.Typeface
import android.support.v4.provider.FontRequest
import android.support.v4.provider.FontsContractCompat
import android.widget.TextView
import com.android.launcher3.R
import java.util.*
import kotlin.collections.HashMap

class FontLoader(context: Context) {

    private var fontLoaded = false
    private var font: Typeface? = null
    private var fontStyles = HashMap<Int, Typeface>()
    private var waitingTasks = WeakHashMap<() -> Unit, Int>()

    init {
        val request = FontRequest(
                "com.google.android.gms.fonts", // ProviderAuthority
                "com.google.android.gms",  // ProviderPackage
                "name=Google Sans",  // Query
                R.array.com_google_android_gms_fonts_certs)

        // retrieve font in the background
        FontsContractCompat.requestFont(context, request, object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                super.onTypefaceRetrieved(typeface)

                onTypefaceLoaded(typeface)
                runOnMainThread { onTypefaceLoaded(typeface) }
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                super.onTypefaceRequestFailed(reason)

                runOnMainThread { onRequestFailed() }
            }
        }, uiWorkerHandler)
    }

    @Synchronized
    fun onTypefaceLoaded(typeface: Typeface) {
        runOnMainThread {
            fontLoaded = true
            font = typeface
            waitingTasks.keys.forEach { it() }
            waitingTasks.clear()
        }
    }

    @Synchronized
    fun onRequestFailed() {
        waitingTasks.clear()
    }

    fun loadGoogleSans(target: TextView, style: Int = Typeface.NORMAL) {
        if (!fontLoaded) {
            waitingTasks[{ loadGoogleSans(target, style) }] = 0
        } else {
            target.typeface = fontStyles.getOrPut(style, { Typeface.create(font, style) })
        }
    }
}
