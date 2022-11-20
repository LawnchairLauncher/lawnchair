package app.lawnchair.util

import android.os.Handler
import android.os.Looper
import com.android.launcher3.util.Executors

val mainHandler = Handler(Looper.getMainLooper())
val uiHelperHandler: Handler = Executors.UI_HELPER_EXECUTOR.handler

fun runOnMainThread(r: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) r() else mainHandler.post(r)
}
