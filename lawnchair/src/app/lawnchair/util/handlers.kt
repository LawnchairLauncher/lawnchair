package app.lawnchair.util

import android.os.Handler
import android.os.Looper
import com.android.launcher3.util.Executors

val mainHandler = Handler(Looper.getMainLooper())
val workerHandler = Executors.MODEL_EXECUTOR.handler
val uiHelperHandler = Executors.UI_HELPER_EXECUTOR.handler
