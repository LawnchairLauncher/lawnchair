/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.animation

import android.annotation.SuppressLint
import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.os.IBinder
import android.os.RemoteException
import android.util.ArrayMap
import android.util.Log
import android.util.RotationUtils
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationAdapter
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.RemoteTransition
import android.window.TransitionInfo
import java.lang.reflect.Method

class RemoteTransitionAdapter {
    companion object {
        /**
         * Almost a copy of Transitions#setupStartState.
         *
         * TODO: remove when there is proper cross-process transaction sync.
         */
        @SuppressLint("NewApi")
        private fun setupLeash(
            leash: SurfaceControl,
            change: TransitionInfo.Change,
            layer: Int,
            info: TransitionInfo,
            t: SurfaceControl.Transaction
        ) {
            val isOpening =
                info.type == WindowManager.TRANSIT_OPEN ||
                    info.type == WindowManager.TRANSIT_TO_FRONT
            // Put animating stuff above this line and put static stuff below it.
            val zSplitLine = info.changes.size
            // changes should be ordered top-to-bottom in z
            val mode = change.mode

            // Launcher animates leaf tasks directly, so always reparent all task leashes to root.
            t.reparent(leash, info.rootLeash)
            t.setPosition(
                leash,
                (change.startAbsBounds.left - info.rootOffset.x).toFloat(),
                (change.startAbsBounds.top - info.rootOffset.y).toFloat()
            )
            t.show(leash)
            // Put all the OPEN/SHOW on top
            if (mode == WindowManager.TRANSIT_OPEN || mode == WindowManager.TRANSIT_TO_FRONT) {
                if (isOpening) {
                    t.setLayer(leash, zSplitLine + info.changes.size - layer)
                    if (
                        change.flags and TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT == 0
                    ) {
                        // if transferred, it should be left visible.
                        t.setAlpha(leash, 0f)
                    }
                } else {
                    // put on bottom and leave it visible
                    t.setLayer(leash, zSplitLine - layer)
                }
            } else if (
                mode == WindowManager.TRANSIT_CLOSE || mode == WindowManager.TRANSIT_TO_BACK
            ) {
                if (isOpening) {
                    // put on bottom and leave visible
                    t.setLayer(leash, zSplitLine - layer)
                } else {
                    // put on top
                    t.setLayer(leash, zSplitLine + info.changes.size - layer)
                }
            } else { // CHANGE
                t.setLayer(leash, zSplitLine + info.changes.size - layer)
            }
        }

        @SuppressLint("NewApi")
        private fun createLeash(
            info: TransitionInfo,
            change: TransitionInfo.Change,
            order: Int,
            t: SurfaceControl.Transaction
        ): SurfaceControl {
            // TODO: once we can properly sync transactions across process, then get rid of this.
            if (change.parent != null && change.flags and TransitionInfo.FLAG_IS_WALLPAPER != 0) {
                // Special case for wallpaper atm. Normally these are left alone; but, a quirk of
                // making leashes means we have to handle them specially.
                return change.leash
            }
            val leashSurface =
                SurfaceControl.Builder()
                    .setName(change.leash.toString() + "_transition-leash")
                    .setContainerLayer()
                    .setParent(
                        if (change.parent == null) info.rootLeash
                        else info.getChange(change.parent!!)!!.leash
                    )
                    .build()
            // Copied Transitions setup code (which expects bottom-to-top order, so we swap here)
            setupLeash(leashSurface, change, info.changes.size - order, info, t)
            t.reparent(change.leash, leashSurface)
            t.setAlpha(change.leash, 1.0f)
            t.show(change.leash)
            t.setPosition(change.leash, 0f, 0f)
            t.setLayer(change.leash, 0)
            return leashSurface
        }

        private fun newModeToLegacyMode(newMode: Int): Int {
            return when (newMode) {
                WindowManager.TRANSIT_OPEN,
                WindowManager.TRANSIT_TO_FRONT -> RemoteAnimationTarget.MODE_OPENING
                WindowManager.TRANSIT_CLOSE,
                WindowManager.TRANSIT_TO_BACK -> RemoteAnimationTarget.MODE_CLOSING
                else -> RemoteAnimationTarget.MODE_CHANGING
            }
        }

        private fun rectOffsetTo(rect: Rect, offset: Point): Rect {
            val out = Rect(rect)
            out.offsetTo(offset.x, offset.y)
            return out
        }

        fun createTarget(
            change: TransitionInfo.Change,
            order: Int,
            info: TransitionInfo,
            t: SurfaceControl.Transaction
        ): RemoteAnimationTarget {
            val target =
                RemoteAnimationTarget(
                    /* taskId */ if (change.taskInfo != null) change.taskInfo!!.taskId else -1,
                    /* mode */ newModeToLegacyMode(change.mode),
                    /* leash */ createLeash(info, change, order, t),
                    /* isTranslucent */ (change.flags and TransitionInfo.FLAG_TRANSLUCENT != 0 ||
                        change.flags and TransitionInfo.FLAG_SHOW_WALLPAPER != 0),
                    /* clipRect */ null,
                    /* contentInsets */ Rect(0, 0, 0, 0),
                    /* prefixOrderIndex */ order,
                    /* position */ null,
                    /* localBounds */ rectOffsetTo(change.endAbsBounds, change.endRelOffset),
                    /* screenSpaceBounds */ Rect(change.endAbsBounds),
                    /* windowConfig */ if (change.taskInfo != null)
                        change.taskInfo!!.configuration.windowConfiguration
                    else WindowConfiguration(),
                    /* isNotInRecents */ if (change.taskInfo != null) !change.taskInfo!!.isRunning
                    else true,
                    /* startLeash */ null,
                    /* startBounds */ Rect(change.startAbsBounds),
                    /* taskInfo */ change.taskInfo,
                    /* allowEnterPip */ change.allowEnterPip,
                    /* windowType */ WindowManager.LayoutParams.INVALID_WINDOW_TYPE
                )
            target.backgroundColor = change.backgroundColor
            return target
        }

        /**
         * Represents a TransitionInfo object as an array of old-style targets
         *
         * @param wallpapers If true, this will return wallpaper targets; otherwise it returns
         *   non-wallpaper targets.
         * @param leashMap Temporary map of change leash -> launcher leash. Is an output, so should
         *   be populated by this function. If null, it is ignored.
         */
        fun wrapTargets(
            info: TransitionInfo,
            wallpapers: Boolean,
            t: SurfaceControl.Transaction,
            leashMap: ArrayMap<SurfaceControl, SurfaceControl>?
        ): Array<RemoteAnimationTarget> {
            val out = ArrayList<RemoteAnimationTarget>()
            for (i in info.changes.indices) {
                val change = info.changes[i]
//                if (change.hasFlags(TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
//                    // For embedded container, when the parent Task is also in the transition, we
//                    // should only animate the parent Task.
//                    if (change.parent != null) continue
//                    // For embedded container without parent, we should only animate if it fills
//                    // the Task. Otherwise we may animate only partial of the Task.
//                    if (!change.hasFlags(TransitionInfo.FLAG_FILLS_TASK)) continue
//                }
//                // Check if it is wallpaper
//                if (wallpapers != change.hasFlags(TransitionInfo.FLAG_IS_WALLPAPER)) continue
                out.add(createTarget(change, info.changes.size - i, info, t))
                if (leashMap != null) {
                    leashMap[change.leash] = out[out.size - 1].leash
                }
            }
            return out.toTypedArray()
        }

        @JvmStatic
        fun adaptRemoteRunner(runner: IRemoteAnimationRunner): IRemoteTransition.Stub {
            return object : IRemoteTransition.Stub() {
                override fun startAnimation(
                    token: IBinder,
                    info: TransitionInfo,
                    t: SurfaceControl.Transaction,
                    finishCallback: IRemoteTransitionFinishedCallback
                ) {
                    val leashMap = ArrayMap<SurfaceControl, SurfaceControl>()
                    val appsCompat = wrapTargets(info, false /* wallpapers */, t, leashMap)
                    val wallpapersCompat = wrapTargets(info, true /* wallpapers */, t, leashMap)
                    // TODO(bc-unlock): Build wrapped object for non-apps target.
                    val nonAppsCompat = arrayOfNulls<RemoteAnimationTarget>(0)

                    // TODO(b/177438007): Move this set-up logic into launcher's animation impl.
                    var isReturnToHome = false
                    var launcherTask: TransitionInfo.Change? = null
                    var wallpaper: TransitionInfo.Change? = null
                    var launcherLayer = 0
                    var rotateDelta = 0
                    var displayW = 0f
                    var displayH = 0f
                    for (i in info.changes.indices.reversed()) {
                        val change = info.changes[i]
                        if (
                            change.taskInfo != null &&
                                change.taskInfo!!.activityType ==
                                    WindowConfiguration.ACTIVITY_TYPE_HOME
                        ) {
                            isReturnToHome =
                                (change.mode == WindowManager.TRANSIT_OPEN ||
                                    change.mode == WindowManager.TRANSIT_TO_FRONT)
                            launcherTask = change
                            launcherLayer = info.changes.size - i
                        } else if (change.flags and TransitionInfo.FLAG_IS_WALLPAPER != 0) {
                            wallpaper = change
                        }
                        if (
                            change.parent == null &&
                                change.endRotation >= 0 &&
                                change.endRotation != change.startRotation
                        ) {
                            rotateDelta = change.endRotation - change.startRotation
                            displayW = change.endAbsBounds.width().toFloat()
                            displayH = change.endAbsBounds.height().toFloat()
                        }
                    }

                    // Prepare for rotation if there is one
                    val counterLauncher = CounterRotator()
                    val counterWallpaper = CounterRotator()
                    if (launcherTask != null && rotateDelta != 0 && launcherTask.parent != null) {
                        counterLauncher.setup(
                            t,
                            info.getChange(launcherTask.parent!!)!!.leash,
                            rotateDelta,
                            displayW,
                            displayH
                        )
                        if (counterLauncher.surface != null) {
                            t.setLayer(counterLauncher.surface!!, launcherLayer)
                        }
                    }
                    if (isReturnToHome) {
                        if (counterLauncher.surface != null) {
                            t.setLayer(counterLauncher.surface!!, info.changes.size * 3)
                        }
                        // Need to "boost" the closing things since that's what launcher expects.
                        for (i in info.changes.indices.reversed()) {
                            val change = info.changes[i]
                            val leash = leashMap[change.leash]
                            val mode = info.changes[i].mode
                            // Only deal with independent layers
                            if (!TransitionInfo.isIndependent(change, info)) continue
                            if (
                                mode == WindowManager.TRANSIT_CLOSE ||
                                    mode == WindowManager.TRANSIT_TO_BACK
                            ) {
                                t.setLayer(leash!!, info.changes.size * 3 - i)
                                counterLauncher.addChild(t, leash)
                            }
                        }
                        // Make wallpaper visible immediately since sysui apparently won't do this.
                        for (i in wallpapersCompat.indices.reversed()) {
                            t.show(wallpapersCompat[i].leash)
                            t.setAlpha(wallpapersCompat[i].leash, 1f)
                        }
                    } else {
                        if (launcherTask != null) {
                            counterLauncher.addChild(t, leashMap[launcherTask.leash])
                        }
                        if (wallpaper != null && rotateDelta != 0 && wallpaper.parent != null) {
                            counterWallpaper.setup(
                                t,
                                info.getChange(wallpaper.parent!!)!!.leash,
                                rotateDelta,
                                displayW,
                                displayH
                            )
                            if (counterWallpaper.surface != null) {
                                t.setLayer(counterWallpaper.surface!!, -1)
                                counterWallpaper.addChild(t, leashMap[wallpaper.leash])
                            }
                        }
                    }
                    t.apply()
                    val animationFinishedCallback =
                        object : IRemoteAnimationFinishedCallback {
                            override fun onAnimationFinished() {
                                val finishTransaction = SurfaceControl.Transaction()
                                counterLauncher.cleanUp(finishTransaction)
                                counterWallpaper.cleanUp(finishTransaction)
                                // Release surface references now. This is apparently to free GPU
                                // memory while doing quick operations (eg. during CTS).
                                val method: Method = info.javaClass.getMethod ("releaseAllSurfaces");
                                method.invoke(info);
                                for (i in leashMap.size - 1 downTo 0) {
                                    leashMap.valueAt(i).release()
                                }
                                try {
                                    finishCallback.onTransitionFinished(
                                        null /* wct */,
                                        finishTransaction
                                    )
                                    finishTransaction.close()
                                } catch (e: RemoteException) {
                                    Log.e(
                                        "ActivityOptionsCompat",
                                        "Failed to call app controlled" +
                                            " animation finished callback",
                                        e
                                    )
                                }
                            }

                            override fun asBinder(): IBinder? {
                                return null
                            }
                        }
                    // TODO(bc-unlcok): Pass correct transit type.
                    runner.onAnimationStart(
                        WindowManager.TRANSIT_OLD_NONE,
                        appsCompat,
                        wallpapersCompat,
                        nonAppsCompat,
                        animationFinishedCallback
                    )
                }

                override fun mergeAnimation(
                    token: IBinder,
                    info: TransitionInfo,
                    t: SurfaceControl.Transaction,
                    mergeTarget: IBinder,
                    finishCallback: IRemoteTransitionFinishedCallback
                ) {
                    // TODO: hook up merge to recents onTaskAppeared if applicable. Until then,
                    //       ignore any incoming merges.
                    // Clean up stuff though cuz GC takes too long for benchmark tests.
                    t.close()
                    val method: Method = info.javaClass.getMethod ("releaseAllSurfaces");
                    method.invoke(info);
                }
            }
        }

        @JvmStatic
        fun adaptRemoteAnimation(adapter: RemoteAnimationAdapter): RemoteTransition {
            return RemoteTransition(adaptRemoteRunner(adapter.runner), adapter.callingApplication)
        }
    }

    /** Utility class that takes care of counter-rotating surfaces during a transition animation. */
    class CounterRotator {
        /** Gets the surface with the counter-rotation. */
        var surface: SurfaceControl? = null
            private set

        /**
         * Sets up this rotator.
         *
         * @param rotateDelta is the forward rotation change (the rotation the display is making).
         * @param parentW (and H) Is the size of the rotating parent.
         */
        fun setup(
            t: SurfaceControl.Transaction,
            parent: SurfaceControl,
            rotateDelta: Int,
            parentW: Float,
            parentH: Float
        ) {
            if (rotateDelta == 0) return
            val surface =
                SurfaceControl.Builder()
                    .setName("Transition Unrotate")
                    .setContainerLayer()
                    .setParent(parent)
                    .build()
            // Rotate forward to match the new rotation (rotateDelta is the forward rotation the
            // parent already took). Child surfaces will be in the old rotation relative to the new
            // parent rotation, so we need to forward-rotate the child surfaces to match.
            RotationUtils.rotateSurface(t, surface, rotateDelta)
            val tmpPt = Point(0, 0)
            // parentW/H are the size in the END rotation, the rotation utilities expect the
            // starting size. So swap them if necessary
            val flipped = rotateDelta % 2 != 0
            val pw = if (flipped) parentH else parentW
            val ph = if (flipped) parentW else parentH
            RotationUtils.rotatePoint(tmpPt, rotateDelta, pw.toInt(), ph.toInt())
            t.setPosition(surface, tmpPt.x.toFloat(), tmpPt.y.toFloat())
            t.show(surface)
        }

        /** Adds a surface that needs to be counter-rotate. */
        fun addChild(t: SurfaceControl.Transaction, child: SurfaceControl?) {
            if (surface == null) return
            t.reparent(child!!, surface)
        }

        /**
         * Clean-up. Since finishTransaction should reset all change leashes, we only need to remove
         * the counter rotation surface.
         */
        fun cleanUp(finishTransaction: SurfaceControl.Transaction) {
            if (surface == null) return
            finishTransaction.remove(surface!!)
        }
    }
}
