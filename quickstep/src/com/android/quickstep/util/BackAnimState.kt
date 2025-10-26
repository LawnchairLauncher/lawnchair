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

package com.android.quickstep.util

import android.animation.AnimatorSet
import android.content.Context
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAnimationRunner.AnimationResult
import com.android.launcher3.anim.AnimatorListeners.forEndCallback
import com.android.launcher3.util.RunnableList

/** Interface to represent animation for back to Launcher transition */
interface BackAnimState {

    fun addOnAnimCompleteCallback(r: Runnable)

    fun applyToAnimationResult(result: AnimationResult, c: Context)

    fun start()
}

class AnimatorBackState(private val springAnim: RectFSpringAnim?, private val anim: AnimatorSet?) :
    BackAnimState {

    override fun addOnAnimCompleteCallback(r: Runnable) {
        val animWait = RunnableList()
        if (Flags.predictiveBackToHomePolish()) {
            springAnim?.addAnimatorListener(forEndCallback(animWait::executeAllAndDestroy))
                ?: anim?.addListener(forEndCallback(animWait::executeAllAndDestroy))
                ?: animWait.executeAllAndDestroy()
        } else {
            val springAnimWait = RunnableList()
            springAnim?.addAnimatorListener(forEndCallback(springAnimWait::executeAllAndDestroy))
                ?: springAnimWait.executeAllAndDestroy()

            anim?.addListener(
                forEndCallback(Runnable { springAnimWait.add(animWait::executeAllAndDestroy) })
            ) ?: springAnimWait.add(animWait::executeAllAndDestroy)
        }
        animWait.add(r)
    }

    override fun applyToAnimationResult(result: AnimationResult, c: Context) {
        result.setAnimation(anim, c)
    }

    override fun start() {
        anim?.start()
    }
}

class AlreadyStartedBackAnimState(private val onEndCallback: RunnableList) : BackAnimState {

    override fun addOnAnimCompleteCallback(r: Runnable) {
        onEndCallback.add(r)
    }

    override fun applyToAnimationResult(result: AnimationResult, c: Context) {
        addOnAnimCompleteCallback(result::onAnimationFinished)
    }

    override fun start() {}
}
