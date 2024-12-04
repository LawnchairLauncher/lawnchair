package com.android.app.viewcapture

import android.os.Process

open class SimpleViewCapture(threadName: String) : ViewCapture(DEFAULT_MEMORY_SIZE, DEFAULT_INIT_POOL_SIZE,
    createAndStartNewLooperExecutor(threadName, Process.THREAD_PRIORITY_FOREGROUND))