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

import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import com.android.wm.shell.transition.Transitions.TransitionObserver
import org.mockito.kotlin.mock

@DslMarker
annotation class TransitionObserverTagMarker

@TransitionObserverTagMarker
class TransitionObserverTestContext(
    private val testTransitionObserverFactory: () -> TransitionObserver
) {

    var startTransaction: Transaction = mock<Transaction>()
    var finishTransaction: Transaction = mock<Transaction>()
    var transition: IBinder = mock<IBinder>()
    lateinit var transitionInfo: TransitionInfo

    fun transitionInfo(builderInput: TransitionInfoTestInputBuilder.() -> Unit) {
        val inputFactoryObj = TransitionInfoTestInputBuilder()
        inputFactoryObj.builderInput()
        transitionInfo = inputFactoryObj.build()
    }

    fun validateOnTransitionReady(validationBlock: () -> Unit) {
        testTransitionObserverFactory().onTransitionReady(
            transition,
            transitionInfo,
            startTransaction,
            finishTransaction
        )
        validationBlock()
    }
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
    val testContext = TransitionObserverTestContext(observerFactory)
    testContext.init()
    return testContext
}
