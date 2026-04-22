/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.util

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TransitionFlags
import android.view.WindowManager.TransitionType
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.ChangeFlags
import android.window.TransitionInfo.FLAG_NONE
import android.window.TransitionInfo.TransitionMode
import android.window.WindowContainerToken
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.wm.shell.transition.Transitions.TransitionObserver
import org.mockito.kotlin.mock

@DslMarker
annotation class TransitionObserverTagMarker

/**
 * Abstraction for all the phases of the [TransitionObserver] test.
 */
interface TransitionObserverTestStep

/**
 * Encapsulates the values for the [TransitionObserver#onTransitionReady] input parameters.
 */
class TransitionObserverTransitionReadyInput(
    val transition: IBinder,
    val info: TransitionInfo,
    val startTransaction: Transaction,
    val finishTransaction: Transaction
)

@TransitionObserverTagMarker
class TransitionObserverTestContext : TransitionObserverTestStep {

    lateinit var transitionObserver: TransitionObserver
    lateinit var transitionReadyInput: TransitionObserverTransitionReadyInput

    fun inputBuilder(builderInput: TransitionObserverInputBuilder.() -> Unit) {
        val inputFactoryObj = TransitionObserverInputBuilder()
        inputFactoryObj.builderInput()
        transitionReadyInput = inputFactoryObj.build()
    }

    fun validateOutput(
        validate:
        TransitionObserverResultValidation.() -> Unit
    ) {
        val validateObj = TransitionObserverResultValidation()
        invokeObservable()
        validateObj.validate()
    }

    fun validateOnMerged(
        validate:
        TransitionObserverOnTransitionMergedValidation.() -> Unit
    ) {
        val validateObj = TransitionObserverOnTransitionMergedValidation()
        transitionObserver.onTransitionMerged(
            validateObj.playing,
            validateObj.merged
        )
        validateObj.validate()
    }

    fun validateOnFinished(
        validate:
        TransitionObserverOnTransitionFinishedValidation.() -> Unit
    ) {
        val validateObj = TransitionObserverOnTransitionFinishedValidation()
        transitionObserver.onTransitionFinished(
            transitionReadyInput.transition,
            validateObj.aborted
        )
        validateObj.validate()
    }

    fun invokeObservable() {
        transitionObserver.onTransitionReady(
            transitionReadyInput.transition,
            transitionReadyInput.info,
            transitionReadyInput.startTransaction,
            transitionReadyInput.finishTransaction
        )
    }
}

/**
 * Phase responsible for the input parameters for [TransitionObserver].
 */
class TransitionObserverInputBuilder : TransitionObserverTestStep {

    private val transition = mock<IBinder>()
    private var transitionInfo: TransitionInfo? = null
    private val startTransaction = mock<Transaction>()
    private val finishTransaction = mock<Transaction>()

    fun buildTransitionInfo(
        @TransitionType type: Int = TRANSIT_NONE,
        @TransitionFlags flags: Int = 0
    ) {
        transitionInfo = TransitionInfo(type, flags)
        spyOn(transitionInfo)
    }

    fun addChange(
        token: WindowContainerToken? = mock(),
        leash: SurfaceControl = mock(),
        @TransitionMode changeMode: Int = TRANSIT_NONE,
        parentToken: WindowContainerToken? = null,
        changeTaskInfo: RunningTaskInfo? = null,
        @ChangeFlags changeFlags: Int = FLAG_NONE
    ) = addChange(Change(token, leash).apply {
        mode = changeMode
        parent = parentToken
        taskInfo = changeTaskInfo
        flags = changeFlags
    })

    fun createChange(
        token: WindowContainerToken? = mock(),
        leash: SurfaceControl = mock(),
        @TransitionMode changeMode: Int = TRANSIT_NONE,
        parentToken: WindowContainerToken? = null,
        changeTaskInfo: RunningTaskInfo? = null,
        @ChangeFlags changeFlags: Int = FLAG_NONE
    ) = Change(token, leash).apply {
        mode = changeMode
        parent = parentToken
        taskInfo = changeTaskInfo
        flags = changeFlags
    }

    fun addChange(change: Change) {
        transitionInfo!!.addChange(change)
    }

    fun createTaskInfo(id: Int = 0, windowingMode: Int = WINDOWING_MODE_FREEFORM) =
        RunningTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            configuration.windowConfiguration.windowingMode = windowingMode
            token = WindowContainerToken(mock<IWindowContainerToken>())
            baseIntent = Intent().apply {
                component = ComponentName("package", "component.name")
            }
        }

    fun build(): TransitionObserverTransitionReadyInput = TransitionObserverTransitionReadyInput(
        transition = transition,
        info = transitionInfo!!,
        startTransaction = startTransaction,
        finishTransaction = finishTransaction
    )
}

/**
 * Phase responsible for the execution of validation methods.
 */
class TransitionObserverResultValidation : TransitionObserverTestStep

/**
 * Phase responsible for the execution of validation methods after the
 * [TransitionObservable#onTransitionMerged] has been executed.
 */
class TransitionObserverOnTransitionMergedValidation : TransitionObserverTestStep {
    val merged = mock<IBinder>()
    val playing = mock<IBinder>()

    init {
        spyOn(merged)
        spyOn(playing)
    }
}

/**
 * Phase responsible for the execution of validation methods after the
 * [TransitionObservable#onTransitionFinished] has been executed.
 */
class TransitionObserverOnTransitionFinishedValidation : TransitionObserverTestStep {
    var aborted: Boolean = false
}

/**
 * Allows to run a test about a specific [TransitionObserver] passing the specific
 * implementation and input value as parameters for the [TransitionObserver#onTransitionReady]
 * method.
 * @param observerFactory    The Factory for the TransitionObserver
 * @param inputFactory      The Builder for the onTransitionReady input parameters
 * @param init  The test code itself.
 */
fun executeTransitionObserverTest(
    observerFactory: () -> TransitionObserver,
    init: TransitionObserverTestContext.() -> Unit
): TransitionObserverTestContext {
    val testContext = TransitionObserverTestContext().apply {
        transitionObserver = observerFactory()
    }
    testContext.init()
    return testContext
}
