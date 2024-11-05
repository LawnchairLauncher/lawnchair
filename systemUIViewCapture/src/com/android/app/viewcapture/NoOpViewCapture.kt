package com.android.app.viewcapture

import android.media.permission.SafeCloseable
import android.os.HandlerThread
import android.view.View
import android.view.Window

/**
 * We don't want to enable the ViewCapture for release builds, since it currently only serves
 * 1p apps, and has memory / cpu load that we don't want to risk negatively impacting release builds
 */
class NoOpViewCapture: ViewCapture(0, 0,
    createAndStartNewLooperExecutor("NoOpViewCapture", HandlerThread.MIN_PRIORITY)) {
    override fun startCapture(view: View, name: String): SafeCloseable {
        return SafeCloseable { }
    }

    override fun startCapture(window: Window): SafeCloseable {
        return SafeCloseable { }
    }
}