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

package com.android.launcher3.taskbar

import android.graphics.Rect
import android.view.MotionEvent
import androidx.annotation.Px
import com.android.launcher3.DeviceProfile
import com.android.launcher3.util.ImmutableRect
import com.android.launcher3.util.MutableListenableRef

/**
 * Data class that represents taskbar's UI states. This state is shared to launcher and recents.
 * Taskbar's UI thread is responsible to update below fields whenever any field is changed.
 *
 * Timings when each field is changed:
 * - [_hasBubblesRef]: when BubbleBarView's child bubble view count is changed between 0 vs
 *   non-zero. Should be reset to false if we don't show bubble bar view and BubbleBarViewController
 *   is not even created.
 * - [_shouldShowEduOnAppLaunchRef]: when DeviceProfile, TaskbarUIController or tooltip steps is
 *   changed
 * - [_isDraggingItemRef]: when ether bubble or taskbar is dragging item. Note that this flag only
 *   represents drags originating from the main Taskbar window and does NOT represents drags
 *   originating from all apps using TaskbarOverlayContext.
 * - [_isTaskbarStashedRef]: when [TaskbarStashController.mIsStashed] has changed
 * - [_isTaskbarAllAppsOpenRef]: when [TaskbarAllAppsController.isOpen] has changed
 * - [_isTaskbarOnHomeRef]: when [TaskbarStashController.mState] is changed
 * - [_showDesktopTaskbarForFreeformDisplayRef]: when [DisplayInfo] is changed
 * - [_showLockedTaskbarOnHome]: when [DisplayInfo] is changed
 * - [_isPrimaryDisplayRef]: when [TaskbarActivityContext] is constructed
 */
class TaskbarUiState {

    private val _hasBubblesRef = MutableListenableRef(false)
    private val _shouldShowEduOnAppLaunchRef = MutableListenableRef(false)
    private val _isDraggingItemRef = MutableListenableRef(false)
    private val _isTaskbarStashedRef = MutableListenableRef(false)
    private val _isTaskbarAllAppsOpenRef = MutableListenableRef(false)
    private val _isTaskbarOnHomeRef = MutableListenableRef(false)
    private val _showDesktopTaskbarForFreeformDisplayRef = MutableListenableRef(false)
    private val _showLockedTaskbarOnHome = MutableListenableRef(false)
    private val _isPrimaryDisplayRef = MutableListenableRef(false)

    private fun <T> MutableListenableRef<T>.diffAndDispatch(newValue: T) {
        if (value != newValue) {
            dispatchValue(newValue)
        }
    }

    val hasBubblesRef = _hasBubblesRef.asListenable()
    val shouldShowEduOnAppLaunchRef = _shouldShowEduOnAppLaunchRef.asListenable()
    val isDraggingItemRef = _isDraggingItemRef.asListenable()
    val isTaskbarStashedRef = _isTaskbarStashedRef.asListenable()
    val isTaskbarAllAppsOpenRef = _isTaskbarAllAppsOpenRef.asListenable()
    val isTaskbarOnHomeRef = _isTaskbarOnHomeRef.asListenable()
    val showDesktopTaskbarForFreeformDisplayRef =
        _showDesktopTaskbarForFreeformDisplayRef.asListenable()
    val showLockedTaskbarOnHome = _showLockedTaskbarOnHome.asListenable()
    val isPrimaryDisplayRef = _isPrimaryDisplayRef.asListenable()

    private var _isBubbleDragging = false
    private var _isTaskbarDragging = false
    private var _stashState = 0L
    private var _bubbleBarViewRect = ImmutableRect.EMPTY_RECT
    private var _isBubbleBarViewVisible = true
    private var _stashedBubbleBarHeightPx = Int.MAX_VALUE
    private var _isStashedHandlerViewVisible = true
    private var _stashedHandlerViewRect = ImmutableRect.EMPTY_RECT

    private var _isTaskbarViewShown = false
    private var _taskbarIconsActualBounds = ImmutableRect.EMPTY_RECT
    private var _navbarFloatingRotationButtonsBounds = ImmutableRect.EMPTY_RECT

    private var _deviceProfile = DeviceProfile.DEFAULT_DEVICE_PROFILE

    fun setHasBubble(hasBubbles: Boolean) {
        _hasBubblesRef.diffAndDispatch(hasBubbles)
    }

    fun setShouldShowEduOnAppLaunch(shouldShowEduOnAppLaunch: Boolean) {
        _shouldShowEduOnAppLaunchRef.diffAndDispatch(shouldShowEduOnAppLaunch)
    }

    fun setIsBubbleDragging(isBubbleDragging: Boolean) {
        _isBubbleDragging = isBubbleDragging
        _isDraggingItemRef.diffAndDispatch(_isBubbleDragging or _isTaskbarDragging)
    }

    fun setIsTaskbarDragging(isTaskbarDragging: Boolean) {
        _isTaskbarDragging = isTaskbarDragging
        _isDraggingItemRef.diffAndDispatch(_isBubbleDragging or _isTaskbarDragging)
    }

    fun setIsTaskbarStashed(isTaskbarStashed: Boolean) {
        _isTaskbarStashedRef.diffAndDispatch(isTaskbarStashed)
    }

    fun setIsTaskbarAllAppsOpen(isTaskbarAllAppsOpen: Boolean) {
        _isTaskbarAllAppsOpenRef.diffAndDispatch(isTaskbarAllAppsOpen)
    }

    fun setStashStateRef(state: Long) {
        _stashState = state
        _isTaskbarOnHomeRef.diffAndDispatch(
            (_stashState and TaskbarStashController.FLAG_IN_OVERVIEW.toLong()) == 0L &&
                (_stashState and TaskbarStashController.FLAG_IN_APP.toLong()) == 0L
        )
    }

    fun setShowDesktopTaskbarForFreeformDisplay(showDesktopTaskbarForFreeformDisplay: Boolean) {
        _showDesktopTaskbarForFreeformDisplayRef.diffAndDispatch(
            showDesktopTaskbarForFreeformDisplay
        )
    }

    fun setShowLockedTaskbarOnHome(showLockedTaskbarOnHome: Boolean) {
        _showLockedTaskbarOnHome.diffAndDispatch(showLockedTaskbarOnHome)
    }

    fun setIsPrimaryDisplay(isPrimaryDisplay: Boolean) {
        _isPrimaryDisplayRef.diffAndDispatch(isPrimaryDisplay)
    }

    fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean {
        return isEventOverBubbleBarView(ev) || isEventOverStashedHandler(ev)
    }

    private fun isEventOverBubbleBarView(e: MotionEvent) =
        if (!_isBubbleBarViewVisible) {
            false
        } else {
            _bubbleBarViewRect.contains(e.x, e.y)
        }

    private fun isEventOverStashedHandler(ev: MotionEvent): Boolean {
        if (!_isStashedHandlerViewVisible) {
            return false
        }
        val top = _deviceProfile.deviceProperties.heightPx - _stashedBubbleBarHeightPx
        val x = ev.rawX
        val y = ev.rawY
        return y >= top && x >= _stashedHandlerViewRect.left && x <= _stashedHandlerViewRect.right
    }

    fun setBubbleBarRect(rect: Rect) {
        _bubbleBarViewRect = ImmutableRect.from(rect)
    }

    fun setIsBubbleBarViewVisible(isVisible: Boolean) {
        _isBubbleBarViewVisible = isVisible
    }

    fun setIsStashedHandlerViewVisible(isVisible: Boolean) {
        _isStashedHandlerViewVisible = isVisible
    }

    fun setStashedBubbleBarHeightPx(@Px height: Int) {
        _stashedBubbleBarHeightPx = height
    }

    fun setStashedHandlerViewRect(rect: Rect) {
        _stashedHandlerViewRect = ImmutableRect.from(rect)
    }

    fun isEventOverAnyTaskbarItem(ev: MotionEvent) = isEventOnTaskbarView(ev) || isEventOnNavbar(ev)

    private fun isEventOnTaskbarView(ev: MotionEvent) =
        _isTaskbarViewShown and _taskbarIconsActualBounds.contains(ev.rawX, ev.rawY)

    fun setTaskbarViewIsShown(isShown: Boolean) {
        _isTaskbarViewShown = isShown
    }

    fun setTaskbarIconsActualBounds(rect: Rect) {
        _taskbarIconsActualBounds = ImmutableRect.from(rect)
    }

    private fun isEventOnNavbar(ev: MotionEvent) =
        _navbarFloatingRotationButtonsBounds.contains(ev.x, ev.y)

    fun setNavbarFloatingRotationButtonsBounds(rect: Rect) {
        _navbarFloatingRotationButtonsBounds = ImmutableRect.from(rect)
    }

    fun setDeviceProfile(dp: DeviceProfile) {
        _deviceProfile = dp
    }
}
