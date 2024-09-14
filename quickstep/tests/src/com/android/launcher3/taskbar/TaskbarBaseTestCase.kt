/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController
import com.android.launcher3.taskbar.bubbles.BubbleControllers
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController
import com.android.systemui.shared.rotation.RotationButtonController
import java.util.Optional
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Helper class to extend to get access to all controllers. Gotta be careful of your relationship
 * with this class though, it can be quite... controlling.
 */
abstract class TaskbarBaseTestCase {

    @Mock lateinit var taskbarActivityContext: TaskbarActivityContext
    @Mock lateinit var taskbarDragController: TaskbarDragController
    @Mock lateinit var navButtonController: TaskbarNavButtonController
    @Mock lateinit var navbarButtonsViewController: NavbarButtonsViewController
    @Mock lateinit var rotationButtonController: RotationButtonController
    @Mock lateinit var taskbarDragLayerController: TaskbarDragLayerController
    @Mock lateinit var taskbarScrimViewController: TaskbarScrimViewController
    @Mock lateinit var taskbarViewController: TaskbarViewController
    @Mock lateinit var taskbarUnfoldAnimationController: TaskbarUnfoldAnimationController
    @Mock lateinit var taskbarKeyguardController: TaskbarKeyguardController
    @Mock lateinit var stashedHandleViewController: StashedHandleViewController
    @Mock lateinit var taskbarStashController: TaskbarStashController
    @Mock lateinit var taskbarAutohideSuspendController: TaskbarAutohideSuspendController
    @Mock lateinit var taskbarPopupController: TaskbarPopupController
    @Mock
    lateinit var taskbarForceVisibleImmersiveController: TaskbarForceVisibleImmersiveController
    @Mock lateinit var taskbarAllAppsController: TaskbarAllAppsController
    @Mock lateinit var taskbarInsetsController: TaskbarInsetsController
    @Mock lateinit var voiceInteractionWindowController: VoiceInteractionWindowController
    @Mock lateinit var taskbarRecentAppsController: TaskbarRecentAppsController
    @Mock lateinit var taskbarTranslationController: TaskbarTranslationController
    @Mock lateinit var taskbarSpringOnStashController: TaskbarSpringOnStashController
    @Mock lateinit var taskbarOverlayController: TaskbarOverlayController
    @Mock lateinit var taskbarEduTooltipController: TaskbarEduTooltipController
    @Mock lateinit var keyboardQuickSwitchController: KeyboardQuickSwitchController
    @Mock lateinit var taskbarPinningController: TaskbarPinningController
    @Mock lateinit var optionalBubbleControllers: Optional<BubbleControllers>

    lateinit var taskbarControllers: TaskbarControllers

    @Before
    open fun setup() {
        /*
         * NOTE: Mocking of controllers that are written in Kotlin won't work since their methods
         * are final by default (and should not be changed only for tests), meaning unmockable.
         * Womp, womp woooommmmppp.
         * If you want to mock one of those methods, you need to make a parent interface that
         * includes that method to allow mocking it.
         */
        MockitoAnnotations.initMocks(this)
        taskbarControllers =
            TaskbarControllers(
                taskbarActivityContext,
                taskbarDragController,
                navButtonController,
                navbarButtonsViewController,
                rotationButtonController,
                taskbarDragLayerController,
                taskbarViewController,
                taskbarScrimViewController,
                taskbarUnfoldAnimationController,
                taskbarKeyguardController,
                stashedHandleViewController,
                taskbarStashController,
                taskbarAutohideSuspendController,
                taskbarPopupController,
                taskbarForceVisibleImmersiveController,
                taskbarOverlayController,
                taskbarAllAppsController,
                taskbarInsetsController,
                voiceInteractionWindowController,
                taskbarTranslationController,
                taskbarSpringOnStashController,
                taskbarRecentAppsController,
                taskbarEduTooltipController,
                keyboardQuickSwitchController,
                taskbarPinningController,
                optionalBubbleControllers,
            )
    }
}
