/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.progress

import android.os.RemoteException
import android.util.Log
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import javax.inject.Inject

/** Forwards received unfold events to [remoteListener], when present. */
class UnfoldTransitionProgressForwarder @Inject constructor() :
    TransitionProgressListener, IUnfoldAnimation.Stub() {

    private var remoteListener: IUnfoldTransitionListener? = null

    override fun onTransitionStarted() {
        try {
            Log.d(TAG, "onTransitionStarted")
            remoteListener?.onTransitionStarted()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed call onTransitionStarted", e)
        }
    }

    override fun onTransitionFinished() {
        try {
            Log.d(TAG, "onTransitionFinished")
            remoteListener?.onTransitionFinished()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed call onTransitionFinished", e)
        }
    }

    override fun onTransitionProgress(progress: Float) {
        try {
            remoteListener?.onTransitionProgress(progress)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed call onTransitionProgress", e)
        }
    }

    override fun setListener(listener: IUnfoldTransitionListener?) {
        remoteListener = listener
    }

    companion object {
        private val TAG = UnfoldTransitionProgressForwarder::class.java.simpleName
    }
}
