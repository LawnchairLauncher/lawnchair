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

package com.android.launcher3.integration.util.events

import android.view.ViewTreeObserver
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.debug.TestEventEmitter.TestEvent
import com.android.launcher3.integration.events.EventWaiter
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.statemanager.StateManager.StateListener

object ActivityTestEvents {

    /**
     * Creates an event waiter to wait for a Launcher state. When calling this function make sure it
     * will always run before the change of state or it will be stuck forever.
     */
    fun <LAUNCHER_TYPE : Launcher> LauncherActivityScenarioRule<LAUNCHER_TYPE>.createStateWaiter(
        stateToWaitFor: LauncherState
    ): EventWaiter {
        // This works by listening to all the changes that happen in the view tree like adding and
        // removing views and checking if a FloatingView, in this case TYPE_WIDGET_RESIZE_FRAME was
        // added
        val waiter = EventWaiter(TestEvent.LAUNCHER_STATE_COMPLETED)
        executeOnLauncher {
            it.stateManager.addStateListener(
                object : StateListener<LauncherState> {
                    override fun onStateTransitionStart(toState: LauncherState) {}

                    override fun onStateTransitionComplete(finalState: LauncherState) {
                        if (finalState == stateToWaitFor) {
                            waiter.terminate()
                        }
                    }
                }
            )
        }
        return waiter
    }

    /**
     * Creates an event waiter to wait for a resize frame to be shown. When calling this function
     * make sure it will always run before the resize frame could be shown or you will be stuck
     * waiting if the events happen before calling this function.
     */
    fun <LAUNCHER_TYPE : Launcher> LauncherActivityScenarioRule<LAUNCHER_TYPE>
        .createResizeFrameShownWaiter(): EventWaiter {
        // This works by listening to all the changes that happen in the view tree like adding and
        // removing views and checking if a FloatingView, in this case TYPE_WIDGET_RESIZE_FRAME was
        // added
        val waiter = EventWaiter(TestEvent.RESIZE_FRAME_SHOWING)
        executeOnLauncher { launcher ->
            launcher.rootView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        AbstractFloatingView.getOpenView<AbstractFloatingView>(
                                launcher,
                                TYPE_WIDGET_RESIZE_FRAME,
                            )
                            ?.let {
                                waiter.terminate()
                                launcher.rootView.viewTreeObserver.removeGlobalOnLayoutListener(
                                    this
                                )
                            }
                    }
                }
            )
        }
        return waiter
    }
}
