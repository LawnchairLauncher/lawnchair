/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.view.ViewTreeObserver.OnWindowVisibilityChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ActivityContextComponent
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.views.ActivityContext

/**
 * A context wrapper with lifecycle tracking based on the window events on the rootView of the
 * [ActivityContext]
 */
abstract class BaseContext
@JvmOverloads
constructor(base: Context, themeResId: Int, private val destroyOnDetach: Boolean = true) :
    ContextThemeWrapper(base, themeResId), ActivityContext {

    private val listeners = mutableListOf<OnDeviceProfileChangeListener>()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val cleanupSet = WeakCleanupSet(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val viewCache = ViewCache()

    private val activityComponentLazy: ActivityContextComponent by lazy {
        appComponent.activityContextComponentBuilder.activityContext(this).build()
            as ActivityContextComponent
    }

    override fun getActivityComponent(): ActivityContextComponent = activityComponentLazy

    init {
        Executors.MAIN_EXECUTOR.execute {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
        }
    }

    override fun getOnDeviceProfileChangeListeners() = listeners

    private val finishActions = RunnableList()

    /** Called when the root view is created for this context */
    fun onViewCreated() {
        val view = rootView
        val attachListener =
            object : OnAttachStateChangeListener {

                override fun onViewAttachedToWindow(view: View) {
                    view.rootView.setViewTreeLifecycleOwner(this@BaseContext)
                    view.rootView.setViewTreeSavedStateRegistryOwner(this@BaseContext)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

                    val treeObserver = view.viewTreeObserver

                    val focusListener = OnWindowFocusChangeListener { updateState() }
                    treeObserver.addOnWindowFocusChangeListener(focusListener)
                    finishActions.add {
                        treeObserver.removeOnWindowFocusChangeListener(focusListener)
                    }

                    if (Utilities.ATLEAST_V) {
                        val visibilityListener = OnWindowVisibilityChangeListener { updateState() }
                        treeObserver.addOnWindowVisibilityChangeListener(visibilityListener)
                        finishActions.add {
                            treeObserver.removeOnWindowVisibilityChangeListener(visibilityListener)
                        }
                    }
                }

                override fun onViewDetachedFromWindow(view: View) {
                    if (destroyOnDetach) onViewDestroyed()
                }
            }
        view.addOnAttachStateChangeListener(attachListener)
        finishActions.add { view.removeOnAttachStateChangeListener(attachListener) }

        if (view.isAttachedToWindow) attachListener.onViewAttachedToWindow(view)
        updateState()
    }

    override fun getViewCache() = viewCache

    override fun getOwnerCleanupSet() = cleanupSet

    private fun updateState() {
        if (lifecycleRegistry.currentState.isAtLeast(CREATED)) {
            lifecycleRegistry.currentState =
                if (rootView.windowVisibility != View.VISIBLE) CREATED
                else (if (!rootView.hasWindowFocus()) STARTED else RESUMED)
        }
    }

    fun onViewDestroyed() {
        if (
            !lifecycleRegistry.currentState.isAtLeast(CREATED) &&
                lifecycleRegistry.currentState != DESTROYED
        ) {
            lifecycleRegistry.currentState = CREATED
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        finishActions.executeAllAndDestroy()
    }
}
