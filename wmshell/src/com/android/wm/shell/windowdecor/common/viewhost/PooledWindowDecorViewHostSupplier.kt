/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wm.shell.windowdecor.common.viewhost

import android.content.Context
import android.os.Trace
import android.util.Pools
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A [WindowDecorViewHostSupplier] backed by a pool to allow recycling view hosts which may be
 * expensive to recreate for each new or updated window decoration.
 *
 * Callers can obtain a [WindowDecorViewHost] using [acquire], which will return a pooled object if
 * available, or create a new instance and return it if needed. When finished using a
 * [WindowDecorViewHost], it must be released using [release] to allow it to be sent back into the
 * pool and reused later on.
 *
 * This class also supports pre-warming [ReusableWindowDecorViewHost] instances, which will be put
 * into the pool immediately after creation.
 */
class PooledWindowDecorViewHostSupplier(
    private val context: Context,
    @ShellMainThread private val mainScope: CoroutineScope,
    shellInit: ShellInit,
    private val desktopState: DesktopState,
    private val displayController: DisplayController,
    private val maxPoolSize: Int,
    private val preWarmSize: Int,
) : WindowDecorViewHostSupplier<WindowDecorViewHost> {
    private val pool: Pools.Pool<WindowDecorViewHost> = Pools.SynchronizedPool(maxPoolSize)
    private val displayPools: HashMap<Int, Pools.Pool<WindowDecorViewHost>> = HashMap()
    private var nextDecorViewHostId = 0
    private val enablePerDisplayPool =
        DesktopExperienceFlags.ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL.isTrue

    init {
        require(preWarmSize <= maxPoolSize) { "Pre-warm size should not exceed pool size" }
        shellInit.addInitCallback(this::onShellInit, this)
    }

    private fun onShellInit() {
        if (!enablePerDisplayPool) {
            if (preWarmSize <= 0) {
                return
            }
            preWarmViewHosts(preWarmSize)
            return
        }

        displayController.addDisplayWindowListener(
            object : DisplayController.OnDisplaysChangedListener {
                override fun onDisplayAdded(displayId: Int) {
                    if (displayPools.containsKey(displayId)) {
                        return
                    }
                    displayPools[displayId] = Pools.SynchronizedPool(maxPoolSize)

                    if (
                        preWarmSize > 0 && desktopState.isDesktopModeSupportedOnDisplay(displayId)
                    ) {
                        preWarmViewHosts(preWarmSize, displayId)
                    }
                }

                override fun onDisplayRemoved(displayId: Int) {
                    val displayPool = displayPools.remove(displayId)
                    // Release all the viewHosts in the pool
                    var pooledViewHost = displayPool?.acquire()
                    val transaction = SurfaceControl.Transaction()
                    while (pooledViewHost != null) {
                        pooledViewHost.release(transaction)
                        pooledViewHost = displayPool?.acquire()
                    }
                    transaction.apply()
                }
            }
        )
    }

    private fun preWarmViewHosts(preWarmSize: Int, displayId: Int = DEFAULT_DISPLAY) {
        mainScope.launch {
            // Applying isn't needed, as the surface was never actually shown.
            val t = SurfaceControl.Transaction()
            val displayContext =
                if (enablePerDisplayPool) {
                    displayController.getDisplayContext(displayId) ?: return@launch
                } else {
                    context
                }
            repeat(preWarmSize) {
                val warmedViewHost =
                    newInstance(displayContext, displayContext.display).apply { warmUp() }
                // Put the warmed view host in the pool by releasing it.
                release(warmedViewHost, t)
            }
        }
    }

    override fun acquire(context: Context, display: Display): WindowDecorViewHost {
        val pooledViewHost =
            if (enablePerDisplayPool) {
                displayPools[display.displayId]?.acquire()
            } else {
                pool.acquire()
            }
        if (pooledViewHost != null) {
            return pooledViewHost
        }
        Trace.beginSection("PooledWindowDecorViewHostSupplier#acquire-newInstance")
        val newDecorViewHost = newInstance(context, display)
        Trace.endSection()
        return newDecorViewHost
    }

    override fun release(viewHost: WindowDecorViewHost, t: SurfaceControl.Transaction) {
        if (DesktopExperienceFlags.ENABLE_CLEAR_REUSABLE_SCVH_ON_RELEASE.isTrue) {
            viewHost.reset()
        }
        val displayPool =
            if (enablePerDisplayPool) {
                displayPools[viewHost.displayId]
            } else {
                pool
            }
        if (displayPool == null || !displayPool.release(viewHost)) {
            viewHost.release(t)
        }
    }

    private fun newInstance(context: Context, display: Display): ReusableWindowDecorViewHost {
        // Use a reusable window decor view host, as it allows swapping the entire view hierarchy.
        return ReusableWindowDecorViewHost(
            context = context,
            mainScope = mainScope,
            display = display,
            id = nextDecorViewHostId++,
        )
    }
}
